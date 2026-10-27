package com.example.issuesystem.global.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.global.dto.GlobalSearchItemResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.dto.PatchHistoryResponse;
import com.example.issuesystem.patchhistory.service.PatchHistoryService;
import com.example.issuesystem.knowledge.dto.KnowledgeShareResponse;
import com.example.issuesystem.knowledge.service.KnowledgeShareService;
import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import com.example.issuesystem.workissuehistory.repository.WorkMaintenanceHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkProjectHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkIssueGlobalSearchRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 통합검색 서비스
 *
 * 변경 사항:
 * 1. 패치이력/지식공유를 각각 PageResponse로 조회한다.
 * 2. 기존처럼 최대 50건으로 잘라서 보여주는 구조를 제거한다.
 * 3. DB 쿼리에서 이미 검색 점수 순으로 정렬하므로 Service에서는 결과 순서를 다시 섞지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    public enum SearchTarget {
        PATCH_HISTORY,
        KNOWLEDGE,
        WORK_ISSUE
    }

    private static final int TITLE_EXACT_SCORE = 100_000;
    private static final int TITLE_COMPACT_SCORE = 90_000;
    private static final int TITLE_TYPO_SCORE = 80_000;
    private static final int TITLE_TOKEN_SCORE = 70_000;
    private static final int BODY_EXACT_SCORE = 50_000;
    private static final int BODY_COMPACT_SCORE = 45_000;
    private static final int BODY_TYPO_SCORE = 40_000;
    private static final int BODY_TOKEN_SCORE = 30_000;
    private static final int AUX_TOKEN_SCORE = 20_000;
    private static final int FALLBACK_SCORE = 1_000;
    private static final int SUMMARY_PREVIEW_LENGTH = 500;
    private static final int DETAIL_PREVIEW_LENGTH = 1_200;
    private static final Pattern ASCII_FRAGMENT_PATTERN = Pattern.compile("[a-z0-9]+");

    private final PatchHistoryService patchHistoryService;
    private final KnowledgeShareService knowledgeShareService;
    private final WorkProjectHistoryRepository workProjectHistoryRepository;
    private final WorkMaintenanceHistoryRepository workMaintenanceHistoryRepository;
    private final WorkIssueGlobalSearchRepository workIssueGlobalSearchRepository;

    @Transactional
    public GlobalSearchResponse search(
            String keyword,
            String titleKeyword,
            String contentKeyword,
            InfraType infraType,
            String customerName,
            LocalDate startDate,
            LocalDate endDate,
            int patchHistoryPage,
            int patchHistorySize,
            int knowledgePage,
            int knowledgeSize,
            int workIssuePage,
            int workIssueSize,
            String workIssueType,
            Set<SearchTarget> targets
    ) {
        Set<SearchTarget> resolvedTargets = targets == null || targets.isEmpty()
                ? EnumSet.allOf(SearchTarget.class)
                : EnumSet.copyOf(targets);
        int safePatchHistoryPage = Math.max(patchHistoryPage, 0);
        int safeKnowledgePage = Math.max(knowledgePage, 0);
        int safeWorkIssuePage = Math.max(workIssuePage, 0);

        // 한 번에 너무 많이 가져오면 화면과 DB 모두 부담이 커지므로 50개까지만 허용한다.
        int safePatchHistorySize = normalizeSize(patchHistorySize, 10);
        int safeKnowledgeSize = normalizeSize(knowledgeSize, 10);
        int safeWorkIssueSize = normalizeSize(workIssueSize, 7);
        String highlightKeyword = resolveHighlightKeyword(keyword, titleKeyword, contentKeyword);

        PageResponse<PatchHistoryResponse> patchHistoryPageResult = null;
        List<GlobalSearchItemResponse> patchHistories = null;
        long patchHistoryTotal = 0;

        if (resolvedTargets.contains(SearchTarget.PATCH_HISTORY)) {
            patchHistoryPageResult = patchHistoryService.search(
                    keyword,
                    titleKeyword,
                    contentKeyword,
                    infraType,
                    null,
                    null,
                    customerName,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    startDate,
                    endDate,
                    safePatchHistoryPage,
                    safePatchHistorySize
            );

            patchHistories = patchHistoryPageResult.getContent().stream()
                    .map(patchHistory -> toPatchHistoryItem(patchHistory, highlightKeyword, infraType, customerName))
                    .toList();
            patchHistoryTotal = patchHistoryPageResult.getTotalElements();
        }

        PageResponse<KnowledgeShareResponse> knowledgePageResult = null;
        List<GlobalSearchItemResponse> knowledgeShares = null;
        long knowledgeTotal = 0;

        if (resolvedTargets.contains(SearchTarget.KNOWLEDGE)) {
            knowledgePageResult = knowledgeShareService.search(
                    keyword,
                    titleKeyword,
                    contentKeyword,
                    customerName,
                    infraType,
                    null,
                    startDate,
                    endDate,
                    safeKnowledgePage,
                    safeKnowledgeSize
            );

            knowledgeShares = knowledgePageResult.getContent().stream()
                    .map(item -> toKnowledgeItem(item, highlightKeyword, infraType, customerName))
                    .toList();
            knowledgeTotal = knowledgePageResult.getTotalElements();
        }

        WorkIssueSearchResult workIssueResult = emptyWorkIssueSearchResult(safeWorkIssuePage, safeWorkIssueSize);
        long workProjectTotal = 0;
        long workMaintenanceTotal = 0;

        if (resolvedTargets.contains(SearchTarget.WORK_ISSUE)) {
            LocalDateTime startDateTime = startDate != null
                    ? startDate.atStartOfDay()
                    : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime endDateTime = endDate != null
                    ? endDate.plusDays(1).atStartOfDay()
                    : LocalDateTime.of(9999, 12, 31, 0, 0);
            workIssueResult = searchWorkIssueHistories(
                    keyword,
                    titleKeyword,
                    contentKeyword,
                    customerName,
                    startDateTime,
                    endDateTime,
                    workIssueType,
                    safeWorkIssuePage,
                    safeWorkIssueSize
            );
            workProjectTotal = workIssueResult.workProjectTotal();
            workMaintenanceTotal = workIssueResult.workMaintenanceTotal();
        }

        long workIssueHistoryTotal = workProjectTotal + workMaintenanceTotal;

        return GlobalSearchResponse.builder()
                .patchHistoryTotal(patchHistoryTotal)
                .knowledgeTotal(knowledgeTotal)
                .workIssueHistoryTotal(workIssueHistoryTotal)
                .workProjectTotal(workProjectTotal)
                .workMaintenanceTotal(workMaintenanceTotal)
                .total(patchHistoryTotal + knowledgeTotal + workIssueHistoryTotal)
                .patchHistories(patchHistories)
                .knowledgeShares(knowledgeShares)
                .workIssueHistories(workIssueResult.content())
                .workIssuePage(workIssueResult.page())
                .workIssueSize(workIssueResult.size())
                .workIssueTotalPages(workIssueResult.totalPages())
                .workIssueHasNext(workIssueResult.hasNext())
                .workIssueHasPrevious(workIssueResult.hasPrevious())
                .patchHistoryPage(patchHistoryPageResult == null ? safePatchHistoryPage : patchHistoryPageResult.getPage())
                .patchHistorySize(patchHistoryPageResult == null ? safePatchHistorySize : patchHistoryPageResult.getSize())
                .patchHistoryTotalPages(patchHistoryPageResult == null ? 0 : patchHistoryPageResult.getTotalPages())
                .patchHistoryHasNext(patchHistoryPageResult != null && patchHistoryPageResult.isHasNext())
                .patchHistoryHasPrevious(patchHistoryPageResult != null && patchHistoryPageResult.isHasPrevious())
                .knowledgePage(knowledgePageResult == null ? safeKnowledgePage : knowledgePageResult.getPage())
                .knowledgeSize(knowledgePageResult == null ? safeKnowledgeSize : knowledgePageResult.getSize())
                .knowledgeTotalPages(knowledgePageResult == null ? 0 : knowledgePageResult.getTotalPages())
                .knowledgeHasNext(knowledgePageResult != null && knowledgePageResult.isHasNext())
                .knowledgeHasPrevious(knowledgePageResult != null && knowledgePageResult.isHasPrevious())
                .build();
    }

    private WorkIssueSearchResult emptyWorkIssueSearchResult(int page, int size) {
        return new WorkIssueSearchResult(List.of(), page, size, 0, false, false, 0, 0);
    }

    private WorkIssueSearchResult searchWorkIssueHistories(
            String keyword, String titleKeyword, String contentKeyword, String customerName,
            LocalDateTime startDateTime, LocalDateTime endDateTime, String workIssueType, int page, int size
    ) {
        var result = workIssueGlobalSearchRepository.search(keyword, titleKeyword, contentKeyword,
                customerName, startDateTime, endDateTime, workIssueType, page, size);
        List<Long> projectIds = result.rows().stream().filter(row -> "PROJECT".equals(row.type()))
                .map(WorkIssueGlobalSearchRepository.Row::id).toList();
        List<Long> maintenanceIds = result.rows().stream().filter(row -> "MAINTENANCE".equals(row.type()))
                .map(WorkIssueGlobalSearchRepository.Row::id).toList();
        Map<Long, WorkProjectHistory> projects = projectIds.isEmpty() ? Map.of()
                : workProjectHistoryRepository.findAllById(projectIds).stream()
                    .collect(Collectors.toMap(WorkProjectHistory::getId, item -> item));
        Map<Long, WorkMaintenanceHistory> maintenance = maintenanceIds.isEmpty() ? Map.of()
                : workMaintenanceHistoryRepository.findAllById(maintenanceIds).stream()
                    .collect(Collectors.toMap(WorkMaintenanceHistory::getId, item -> item));
        String highlight = resolveHighlightKeyword(keyword, titleKeyword, contentKeyword);
        List<GlobalSearchItemResponse> content = new ArrayList<>();
        for (var row : result.rows()) {
            if ("PROJECT".equals(row.type())) {
                if (projects.containsKey(row.id())) content.add(toWorkProjectItem(projects.get(row.id()), highlight, customerName));
            } else if (maintenance.containsKey(row.id())) {
                content.add(toWorkMaintenanceItem(maintenance.get(row.id()), highlight, customerName));
            }
        }
        long total = result.projectTotal() + result.maintenanceTotal();
        return new WorkIssueSearchResult(content, page, size, calculateTotalPages(total, size),
                ((long) page + 1) * size < total, page > 0, result.projectTotal(), result.maintenanceTotal());
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements <= 0 || size <= 0) {
            return 0;
        }

        return (int) ((totalElements + size - 1) / size);
    }

    private record WorkIssueSearchResult(
            List<GlobalSearchItemResponse> content,
            int page,
            int size,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious,
            long workProjectTotal,
            long workMaintenanceTotal
    ) {
    }

    private GlobalSearchItemResponse toWorkProjectItem(
            WorkProjectHistory project,
            String keyword,
            String customerName
    ) {
        int score = calculateWorkProjectScore(project, keyword, customerName);

        return GlobalSearchItemResponse.builder()
                .sourceType("WORK_ISSUE_HISTORY")
                .sourceLabel("작업/이슈이력")
                .workHistoryType("PROJECT")
                .id(project.getId())
                .title(project.getClientName())
                .summary(preview(project.getScope(), keyword, SUMMARY_PREVIEW_LENGTH))
                .detail(preview(project.getProgressLogs(), keyword, DETAIL_PREVIEW_LENGTH))
                .infraTypes(List.of())
                .customerName(project.getClientName())
                .authorName(project.getExecutors())
                .createdAt(project.getCreatedAt())
                .matchScore(score)
                .matchLevel(toMatchLevel(score))
                .build();
    }

    private GlobalSearchItemResponse toWorkMaintenanceItem(
            WorkMaintenanceHistory maintenance,
            String keyword,
            String customerName
    ) {
        int score = calculateWorkMaintenanceScore(maintenance, keyword, customerName);

        return GlobalSearchItemResponse.builder()
                .sourceType("WORK_ISSUE_HISTORY")
                .sourceLabel("작업/이슈이력")
                .workHistoryType("MAINTENANCE")
                .id(maintenance.getId())
                .title(maintenance.getMaintenanceName())
                .summary(preview(maintenance.getProgressIssues(), keyword, SUMMARY_PREVIEW_LENGTH))
                .detail(preview(maintenance.getRemarks(), keyword, DETAIL_PREVIEW_LENGTH))
                .infraTypes(List.of())
                .customerName(maintenance.getMaintenanceName())
                .authorName(maintenance.getMainDev())
                .createdAt(maintenance.getCreatedAt())
                .matchScore(score)
                .matchLevel(toMatchLevel(score))
                .build();
    }

    private GlobalSearchItemResponse toPatchHistoryItem(
            PatchHistoryResponse patchHistory,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        int score = calculatePatchHistoryScore(patchHistory, keyword, infraType, customerName);

        return GlobalSearchItemResponse.builder()
                .sourceType("PATCH_HISTORY")
                .sourceLabel("패치이력")
                .id(patchHistory.getId())
                .title(patchHistory.getTitle())
                .summary(preview(patchHistory.getSymptomSummary(), keyword, SUMMARY_PREVIEW_LENGTH))
                .detail(preview(patchHistory.getSymptomDetail(), keyword, DETAIL_PREVIEW_LENGTH))
                .infraTypes(patchHistory.getInfraType() == null ? List.of() : List.of(patchHistory.getInfraType().name()))
                .customerName(patchHistory.getCustomerName())
                .authorName(patchHistory.getAuthorName())
                .createdAt(patchHistory.getCreatedAt())
                .matchScore(score)
                .matchLevel(toMatchLevel(score))
                .build();
    }

    private GlobalSearchItemResponse toKnowledgeItem(
            KnowledgeShareResponse item,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        int score = calculateKnowledgeScore(item, keyword, infraType, customerName);

        return GlobalSearchItemResponse.builder()
                .sourceType("KNOWLEDGE")
                .sourceLabel("지식공유 DB")
                .id(item.getId())
                .title(item.getTitle())
                .summary(preview(item.getContent(), keyword, SUMMARY_PREVIEW_LENGTH))
                .detail(preview(item.getContent(), keyword, DETAIL_PREVIEW_LENGTH))
                .infraTypes(
                        item.getInfraTypes() == null
                                ? List.of()
                                : item.getInfraTypes().stream().map(Enum::name).toList()
                )
                .customerName(item.getCustomerName())
                .authorName(item.getAuthorName())
                .createdAt(item.getCreatedAt())
                .matchScore(score)
                .matchLevel(toMatchLevel(score))
                .build();
    }

    /** 페이지 크기는 기본 10개, 최대 50개로 제한한다. */
    private int normalizeSize(int size, int defaultSize) {
        if (size <= 0) {
            return defaultSize;
        }

        return Math.min(size, 50);
    }

    private String preview(String value, String keyword, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        int start = previewStart(value, keyword, maxLength);
        int end = Math.min(value.length(), start + maxLength);
        String preview = value.substring(start, end).trim();

        if (start > 0) {
            preview = "..." + preview;
        }

        if (end < value.length()) {
            preview = preview + "...";
        }

        return preview;
    }

    private int previewStart(String value, String keyword, int maxLength) {
        int matchIndex = firstKeywordIndex(value, keyword);
        if (matchIndex < 0) {
            return 0;
        }

        return Math.max(0, matchIndex - (maxLength / 4));
    }

    private int firstKeywordIndex(String value, String keyword) {
        String normalizedValue = normalize(value);
        String normalizedKeyword = normalize(keyword);
        if (normalizedValue.isBlank() || normalizedKeyword.isBlank()) {
            return -1;
        }

        int exactIndex = normalizedValue.indexOf(normalizedKeyword);
        if (exactIndex >= 0) {
            return exactIndex;
        }

        return tokenize(normalizedKeyword).stream()
                .mapToInt(normalizedValue::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(-1);
    }

    private int calculatePatchHistoryScore(
            PatchHistoryResponse patchHistory,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        int score = calculateStructuredMatchScore(
                normalizedKeyword,
                values(patchHistory.getTitle()),
                values(
                        patchHistory.getSymptomSummary(),
                        patchHistory.getSymptomDetail(),
                        patchHistory.getActionDetail(),
                        patchHistory.getCauseDetail()
                ),
                values(
                        patchHistory.getTags(),
                        patchHistory.getSystemName(),
                        patchHistory.getCustomerName(),
                        patchHistory.getCategory(),
                        patchHistory.getDeploymentVersion(),
                        patchHistory.getVersionInfo()
                )
        );

        if (infraType != null && infraType.equals(patchHistory.getInfraType())) {
            score += 20;
        }

        if (!normalize(customerName).isBlank() && contains(patchHistory.getCustomerName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    private int calculateKnowledgeScore(
            KnowledgeShareResponse item,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        int score = calculateStructuredMatchScore(
                normalizedKeyword,
                values(item.getTitle()),
                values(item.getContent()),
                values(item.getCustomerName(), item.getAuthorName(), item.getAttachmentName())
        );

        if (infraType != null && item.getInfraTypes() != null && item.getInfraTypes().contains(infraType)) {
            score += 20;
        }

        if (!normalize(customerName).isBlank() && contains(item.getCustomerName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    private int calculateWorkProjectScore(
            WorkProjectHistory project,
            String keyword,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        int score = calculateStructuredMatchScore(
                normalizedKeyword,
                values(project.getClientName()),
                values(project.getScope(), project.getProgressLogs(), project.getRemainingIssues()),
                values(project.getExecutors(), project.getSalesRep(), project.getSiteCode(), project.getProjectScale())
        );

        if (!normalize(customerName).isBlank() && contains(project.getClientName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    private int calculateWorkMaintenanceScore(
            WorkMaintenanceHistory maintenance,
            String keyword,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        int score = calculateStructuredMatchScore(
                normalizedKeyword,
                values(maintenance.getMaintenanceName()),
                values(maintenance.getProgressIssues(), maintenance.getRemarks()),
                values(
                        maintenance.getMainDev(),
                        maintenance.getSubDev(),
                        maintenance.getSalesRep(),
                        maintenance.getSiteCode(),
                        maintenance.getRegion()
                )
        );

        if (!normalize(customerName).isBlank() && contains(maintenance.getMaintenanceName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    private String toMatchLevel(int score) {
        if (score >= TITLE_COMPACT_SCORE) {
            return "TITLE_EXACT";
        }

        if (score >= TITLE_TOKEN_SCORE) {
            return "TITLE_SIMILAR";
        }

        if (score >= BODY_COMPACT_SCORE) {
            return "BODY_EXACT";
        }

        if (score >= BODY_TOKEN_SCORE) {
            return "BODY_SIMILAR";
        }

        if (score >= AUX_TOKEN_SCORE) {
            return "PARTIAL";
        }

        return "LOW";
    }

    private int calculateStructuredMatchScore(
            String normalizedKeyword,
            List<String> titleValues,
            List<String> bodyValues,
            List<String> auxiliaryValues
    ) {
        if (normalizedKeyword.isBlank()) {
            return 0;
        }

        List<String> tokens = tokenize(normalizedKeyword);

        if (containsAny(titleValues, normalizedKeyword)) {
            return TITLE_EXACT_SCORE;
        }

        if (containsAnyWithoutSpace(titleValues, normalizedKeyword)) {
            return TITLE_COMPACT_SCORE;
        }

        double titleSimilarity = maxTypoSimilarity(titleValues, normalizedKeyword);
        if (isTypoCandidate(titleSimilarity, normalizedKeyword)) {
            return TITLE_TYPO_SCORE + similarityTieScore(titleSimilarity);
        }

        int titleTokenMatches = tokenMatchCount(tokens, titleValues);
        if (titleTokenMatches > 0) {
            return TITLE_TOKEN_SCORE + Math.min(titleTokenMatches, 20);
        }

        if (containsAny(bodyValues, normalizedKeyword)) {
            return BODY_EXACT_SCORE;
        }

        if (containsAnyWithoutSpace(bodyValues, normalizedKeyword)) {
            return BODY_COMPACT_SCORE;
        }

        double bodySimilarity = maxTypoSimilarity(bodyValues, normalizedKeyword);
        if (isTypoCandidate(bodySimilarity, normalizedKeyword)) {
            return BODY_TYPO_SCORE + similarityTieScore(bodySimilarity);
        }

        int bodyTokenMatches = tokenMatchCount(tokens, bodyValues);
        if (bodyTokenMatches > 0) {
            return BODY_TOKEN_SCORE + Math.min(bodyTokenMatches, 20);
        }

        int auxiliaryTokenMatches = tokenMatchCount(tokens, auxiliaryValues);
        if (auxiliaryTokenMatches > 0) {
            return AUX_TOKEN_SCORE + Math.min(auxiliaryTokenMatches, 20);
        }

        List<String> characters = characterTokens(normalizedKeyword);
        int fallbackMatches = characterMatchCount(characters, titleValues, bodyValues, auxiliaryValues);
        if (fallbackMatches >= minimumFallbackCharacterMatches(characters.size())) {
            return FALLBACK_SCORE + Math.min(fallbackMatches, 20);
        }

        return 0;
    }

    private List<String> values(String... values) {
        return Arrays.asList(values);
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.trim().split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private List<String> characterTokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return value.replaceAll("\\s+", "")
                .codePoints()
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    @SafeVarargs
    private int characterMatchCount(List<String> characters, List<String>... valueGroups) {
        if (characters.isEmpty() || valueGroups == null || valueGroups.length == 0) {
            return 0;
        }

        int matchedCount = 0;
        for (String character : characters) {
            boolean matched = Arrays.stream(valueGroups)
                    .flatMap(List::stream)
                    .anyMatch(value -> contains(value, character));
            if (matched) {
                matchedCount++;
            }
        }

        return matchedCount;
    }

    private int minimumFallbackCharacterMatches(int characterCount) {
        if (characterCount >= 5) {
            return 3;
        }

        if (characterCount >= 3) {
            return 2;
        }

        return Integer.MAX_VALUE;
    }

    private boolean containsAny(List<String> values, String keyword) {
        return values.stream().anyMatch(value -> contains(value, keyword));
    }

    private boolean containsAnyWithoutSpace(List<String> values, String keyword) {
        return values.stream().anyMatch(value -> containsWithoutSpace(value, keyword));
    }

    private int tokenMatchCount(List<String> tokens, List<String> values) {
        if (tokens.isEmpty()) {
            return 0;
        }

        int matchCount = 0;
        for (String token : tokens) {
            for (String value : values) {
                if (contains(value, token)) {
                    matchCount++;
                }
            }
        }

        return matchCount;
    }

    private double maxTypoSimilarity(List<String> values, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }

        return values.stream()
                .mapToDouble(value -> typoSimilarity(value, keyword))
                .max()
                .orElse(0);
    }

    private boolean isTypoCandidate(double similarity, String keyword) {
        return similarity >= typoThreshold(compact(keyword));
    }

    private double typoThreshold(String compactKeyword) {
        return compactKeyword.length() <= 3 ? 0.66 : 0.74;
    }

    private int similarityTieScore(double similarity) {
        return (int) Math.floor(similarity * 1_000);
    }

    private double typoSimilarity(String value, String keyword) {
        String target = normalize(value);
        String query = normalize(keyword);

        if (target.isBlank() || query.isBlank()) {
            return 0;
        }

        String compactQuery = compact(query);
        double threshold = typoThreshold(compactQuery);

        Set<String> fragments = new LinkedHashSet<>();
        fragments.add(target);
        fragments.add(compact(target));
        fragments.addAll(tokenize(target));
        addAsciiFragments(fragments, target);

        double best = 0;
        for (String fragment : fragments) {
            if (fragment.isBlank()) {
                continue;
            }

            best = Math.max(best, typoSimilarityFragment(fragment, query, threshold));
            best = Math.max(best, typoSimilarityFragment(compact(fragment), compactQuery, threshold));
            if (best >= 1.0) {
                return best;
            }
        }

        return best;
    }

    private void addAsciiFragments(Set<String> fragments, String value) {
        Matcher matcher = ASCII_FRAGMENT_PATTERN.matcher(value);
        while (matcher.find()) {
            fragments.add(matcher.group());
        }
    }

    private double typoSimilarityFragment(String value, String keyword, double threshold) {
        if (value.isBlank() || keyword.isBlank()) {
            return 0;
        }

        int maxLength = Math.max(value.length(), keyword.length());
        if (maxLength == 0) {
            return 0;
        }

        double prefixBonus = commonPrefixLength(value, keyword) >= 3 ? 0.08 : 0;
        // 편집 거리는 길이 차이보다 작을 수 없다. 접두사 가산점까지 더해도
        // 오타 기준에 못 미치는 긴 본문은 거리 행렬을 계산할 필요가 없다.
        double upperBound = 1.0 - ((double) Math.abs(value.length() - keyword.length()) / maxLength)
                + prefixBonus;
        if (upperBound < threshold) {
            return 0;
        }

        int distance = damerauLevenshteinDistance(value, keyword);
        double similarity = 1.0 - ((double) distance / maxLength) + prefixBonus;
        return Math.min(similarity, 1.0);
    }

    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int count = 0;
        while (count < limit && left.charAt(count) == right.charAt(count)) {
            count++;
        }

        return count;
    }

    private int damerauLevenshteinDistance(String left, String right) {
        if (left.length() < right.length()) {
            return damerauLevenshteinDistance(right, left);
        }
        // 인접 문자 교환에는 직전 두 행만 필요하다.
        int[] previousPrevious = new int[right.length() + 1];
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                int next = Math.min(
                        Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + cost
                );

                if (
                        i > 1
                                && j > 1
                                && left.charAt(i - 1) == right.charAt(j - 2)
                                && left.charAt(i - 2) == right.charAt(j - 1)
                ) {
                    next = Math.min(next, previousPrevious[j - 2] + 1);
                }

                current[j] = next;
            }
            int[] reusable = previousPrevious;
            previousPrevious = previous;
            previous = current;
            current = reusable;
        }

        return previous[right.length()];
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null || keyword.isBlank()) {
            return false;
        }

        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    /** 공백 차이만 있는 경우도 같은 검색어로 본다. 예: "에이전트설치" vs "에이전트 설치" */
    private boolean containsWithoutSpace(String value, String keyword) {
        if (value == null || keyword == null || keyword.isBlank()) {
            return false;
        }

        String normalizedValue = value.toLowerCase().replace(" ", "");
        String normalizedKeyword = keyword.toLowerCase().replace(" ", "");

        return normalizedValue.contains(normalizedKeyword);
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String resolveHighlightKeyword(String keyword, String titleKeyword, String contentKeyword) {
        String separatedKeyword = String.join(
                " ",
                normalizeForDisplay(titleKeyword),
                normalizeForDisplay(contentKeyword)
        ).trim();

        return separatedKeyword.isBlank() ? normalizeForDisplay(keyword) : separatedKeyword;
    }

    private String normalizeForDisplay(String value) {
        return value == null ? "" : value.trim();
    }
}
