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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
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
    private static final Pattern ASCII_FRAGMENT_PATTERN = Pattern.compile("[a-z0-9]+");

    private final PatchHistoryService patchHistoryService;
    private final KnowledgeShareService knowledgeShareService;
    private final WorkProjectHistoryRepository workProjectHistoryRepository;
    private final WorkMaintenanceHistoryRepository workMaintenanceHistoryRepository;

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
            String workIssueType
    ) {
        int safePatchHistoryPage = Math.max(patchHistoryPage, 0);
        int safeKnowledgePage = Math.max(knowledgePage, 0);
        int safeWorkIssuePage = Math.max(workIssuePage, 0);

        // 한 번에 너무 많이 가져오면 화면과 DB 모두 부담이 커지므로 50개까지만 허용한다.
        int safePatchHistorySize = normalizeSize(patchHistorySize, 10);
        int safeKnowledgeSize = normalizeSize(knowledgeSize, 10);
        int safeWorkIssueSize = normalizeSize(workIssueSize, 7);
        String highlightKeyword = resolveHighlightKeyword(keyword, titleKeyword, contentKeyword);

        PageResponse<PatchHistoryResponse> patchHistoryPageResult = patchHistoryService.search(
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

        PageResponse<KnowledgeShareResponse> knowledgePageResult = knowledgeShareService.search(
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

        List<GlobalSearchItemResponse> patchHistories = patchHistoryPageResult.getContent().stream()
                .map(patchHistory -> toPatchHistoryItem(patchHistory, highlightKeyword, infraType, customerName))
                .sorted(matchScoreComparator())
                .toList();

        List<GlobalSearchItemResponse> knowledgeShares = knowledgePageResult.getContent().stream()
                .map(item -> toKnowledgeItem(item, highlightKeyword, infraType, customerName))
                .sorted(matchScoreComparator())
                .toList();

        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime endDateTime = endDate != null
                ? endDate.plusDays(1).atStartOfDay()
                : LocalDateTime.of(9999, 12, 31, 0, 0);
        WorkIssueSearchResult workIssueResult = searchWorkIssueHistories(
                highlightKeyword,
                customerName,
                startDateTime,
                endDateTime,
                workIssueType,
                safeWorkIssuePage,
                safeWorkIssueSize
        );

        long patchHistoryTotal = patchHistoryPageResult.getTotalElements();
        long knowledgeTotal = knowledgePageResult.getTotalElements();
        long workProjectTotal = workIssueResult.workProjectTotal();
        long workMaintenanceTotal = workIssueResult.workMaintenanceTotal();
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
                .patchHistoryPage(patchHistoryPageResult.getPage())
                .patchHistorySize(patchHistoryPageResult.getSize())
                .patchHistoryTotalPages(patchHistoryPageResult.getTotalPages())
                .patchHistoryHasNext(patchHistoryPageResult.isHasNext())
                .patchHistoryHasPrevious(patchHistoryPageResult.isHasPrevious())
                .knowledgePage(knowledgePageResult.getPage())
                .knowledgeSize(knowledgePageResult.getSize())
                .knowledgeTotalPages(knowledgePageResult.getTotalPages())
                .knowledgeHasNext(knowledgePageResult.isHasNext())
                .knowledgeHasPrevious(knowledgePageResult.isHasPrevious())
                .build();
    }

    private WorkIssueSearchResult searchWorkIssueHistories(
            String keyword,
            String customerName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String workIssueType,
            int page,
            int size
    ) {
        String resolvedWorkIssueType = normalize(workIssueType).toUpperCase();

        List<GlobalSearchItemResponse> projectRows = workProjectHistoryRepository.searchForGlobal(
                        keyword,
                        customerName,
                        startDateTime,
                        endDateTime
                )
                .stream()
                .map(item -> toWorkProjectItem(item, keyword, customerName))
                .toList();

        List<GlobalSearchItemResponse> maintenanceRows = workMaintenanceHistoryRepository.searchForGlobal(
                        keyword,
                        customerName,
                        startDateTime,
                        endDateTime
                )
                .stream()
                .map(item -> toWorkMaintenanceItem(item, keyword, customerName))
                .toList();

        if ("PROJECT".equals(resolvedWorkIssueType)) {
            return buildWorkIssueSearchResult(
                    projectRows,
                    page,
                    size,
                    projectRows.size(),
                    projectRows.size(),
                    maintenanceRows.size()
            );
        }

        if ("MAINTENANCE".equals(resolvedWorkIssueType)) {
            return buildWorkIssueSearchResult(
                    maintenanceRows,
                    page,
                    size,
                    maintenanceRows.size(),
                    projectRows.size(),
                    maintenanceRows.size()
            );
        }

        List<GlobalSearchItemResponse> mergedRows = new java.util.ArrayList<>();
        mergedRows.addAll(projectRows);
        mergedRows.addAll(maintenanceRows);

        return buildWorkIssueSearchResult(
                mergedRows,
                page,
                size,
                mergedRows.size(),
                projectRows.size(),
                maintenanceRows.size()
        );
    }

    private WorkIssueSearchResult buildWorkIssueSearchResult(
            List<GlobalSearchItemResponse> rows,
            int page,
            int size,
            long totalElements,
            long workProjectTotal,
            long workMaintenanceTotal
    ) {
        List<GlobalSearchItemResponse> sortedRows = rows.stream()
                .sorted(workIssueSortComparator())
                .toList();

        long startOffset = (long) page * size;
        int startIndex = startOffset >= sortedRows.size() ? sortedRows.size() : (int) startOffset;
        int endIndex = Math.min(startIndex + size, sortedRows.size());
        int totalPages = calculateTotalPages(totalElements, size);

        return new WorkIssueSearchResult(
                sortedRows.subList(startIndex, endIndex),
                page,
                size,
                totalPages,
                (((long) page + 1L) * size) < totalElements,
                page > 0 && totalElements > 0,
                workProjectTotal,
                workMaintenanceTotal
        );
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements <= 0 || size <= 0) {
            return 0;
        }

        return (int) ((totalElements + size - 1) / size);
    }

    private Comparator<GlobalSearchItemResponse> workIssueSortComparator() {
        return matchScoreComparator()
                .thenComparing(
                        GlobalSearchItemResponse::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
    }

    private Comparator<GlobalSearchItemResponse> matchScoreComparator() {
        return Comparator.comparingInt(GlobalSearchItemResponse::getMatchScore).reversed();
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
                .summary(project.getScope())
                .detail(project.getProgressLogs())
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
                .summary(maintenance.getProgressIssues())
                .detail(maintenance.getRemarks())
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
                .summary(patchHistory.getSymptomSummary())
                .detail(patchHistory.getSymptomDetail())
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
                .summary(item.getContent())
                .detail(item.getContent())
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

    private int calculatePatchHistoryScore(
            PatchHistoryResponse patchHistory,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        List<String> tokens = tokenize(normalizedKeyword);
        List<String> characters = characterTokens(normalizedKeyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            // 전체 검색어가 그대로 포함되면 높은 점수를 준다.
            if (contains(patchHistory.getTitle(), normalizedKeyword)) score += 120;
            if (containsWithoutSpace(patchHistory.getTitle(), normalizedKeyword)) score += 100;
            if (contains(patchHistory.getSymptomSummary(), normalizedKeyword)) score += 80;
            if (contains(patchHistory.getSymptomDetail(), normalizedKeyword)) score += 50;
            if (contains(patchHistory.getActionDetail(), normalizedKeyword)) score += 35;
            if (contains(patchHistory.getCauseDetail(), normalizedKeyword)) score += 30;

            // 검색어를 쪼갠 token이 많이 맞을수록 점수를 올린다.
            for (String token : tokens) {
                if (contains(patchHistory.getTitle(), token)) score += 30;
                if (contains(patchHistory.getSymptomSummary(), token)) score += 20;
                if (contains(patchHistory.getSymptomDetail(), token)) score += 12;
                if (contains(patchHistory.getActionDetail(), token)) score += 8;
                if (contains(patchHistory.getCauseDetail(), token)) score += 8;
                if (contains(patchHistory.getTags(), token)) score += 6;
                if (contains(patchHistory.getSystemName(), token)) score += 6;
                if (contains(patchHistory.getCustomerName(), token)) score += 6;
                if (contains(patchHistory.getCategory(), token)) score += 4;
                if (contains(patchHistory.getDeploymentVersion(), token)) score += 4;
                if (contains(patchHistory.getVersionInfo(), token)) score += 4;
            }

            score += scoreCharacterMatches(characters, 5, patchHistory.getTitle());
            score += scoreCharacterMatches(characters, 3, patchHistory.getSymptomSummary());
            score += scoreCharacterMatches(characters, 2,
                    patchHistory.getSymptomDetail(),
                    patchHistory.getActionDetail(),
                    patchHistory.getCauseDetail()
            );
            score += scoreCharacterMatches(characters, 1,
                    patchHistory.getTags(),
                    patchHistory.getSystemName(),
                    patchHistory.getCustomerName(),
                    patchHistory.getCategory(),
                    patchHistory.getDeploymentVersion(),
                    patchHistory.getVersionInfo()
            );
        }

        score = calculateStructuredMatchScore(
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
        List<String> tokens = tokenize(normalizedKeyword);
        List<String> characters = characterTokens(normalizedKeyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            // 전체 검색어가 그대로 포함되면 높은 점수를 준다.
            if (contains(item.getTitle(), normalizedKeyword)) score += 120;
            if (containsWithoutSpace(item.getTitle(), normalizedKeyword)) score += 100;
            if (contains(item.getContent(), normalizedKeyword)) score += 70;
            if (contains(item.getCustomerName(), normalizedKeyword)) score += 10;
            if (contains(item.getAuthorName(), normalizedKeyword)) score += 10;

            // 검색어를 쪼갠 token이 많이 맞을수록 점수를 올린다.
            for (String token : tokens) {
                if (contains(item.getTitle(), token)) score += 30;
                if (contains(item.getContent(), token)) score += 15;
                if (contains(item.getCustomerName(), token)) score += 6;
                if (contains(item.getAuthorName(), token)) score += 6;
                if (contains(item.getAttachmentName(), token)) score += 4;
            }

            score += scoreCharacterMatches(characters, 5, item.getTitle());
            score += scoreCharacterMatches(characters, 2, item.getContent());
            score += scoreCharacterMatches(characters, 1,
                    item.getCustomerName(),
                    item.getAuthorName(),
                    item.getAttachmentName()
            );
        }

        score = calculateStructuredMatchScore(
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
        List<String> tokens = tokenize(normalizedKeyword);
        List<String> characters = characterTokens(normalizedKeyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (contains(project.getClientName(), normalizedKeyword)) score += 80;
            if (contains(project.getScope(), normalizedKeyword)) score += 60;
            if (contains(project.getProgressLogs(), normalizedKeyword)) score += 50;
            if (contains(project.getRemainingIssues(), normalizedKeyword)) score += 40;

            for (String token : tokens) {
                if (contains(project.getClientName(), token)) score += 24;
                if (contains(project.getScope(), token)) score += 16;
                if (contains(project.getProgressLogs(), token)) score += 12;
                if (contains(project.getRemainingIssues(), token)) score += 10;
                if (contains(project.getExecutors(), token)) score += 8;
                if (contains(project.getSalesRep(), token)) score += 6;
            }

            score += scoreCharacterMatches(characters, 5, project.getClientName());
            score += scoreCharacterMatches(characters, 2,
                    project.getScope(),
                    project.getProgressLogs(),
                    project.getRemainingIssues()
            );
            score += scoreCharacterMatches(characters, 1,
                    project.getExecutors(),
                    project.getSalesRep(),
                    project.getSiteCode(),
                    project.getProjectScale()
            );
        }

        score = calculateStructuredMatchScore(
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
        List<String> tokens = tokenize(normalizedKeyword);
        List<String> characters = characterTokens(normalizedKeyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (contains(maintenance.getMaintenanceName(), normalizedKeyword)) score += 80;
            if (contains(maintenance.getProgressIssues(), normalizedKeyword)) score += 60;
            if (contains(maintenance.getRemarks(), normalizedKeyword)) score += 40;

            for (String token : tokens) {
                if (contains(maintenance.getMaintenanceName(), token)) score += 24;
                if (contains(maintenance.getProgressIssues(), token)) score += 16;
                if (contains(maintenance.getRemarks(), token)) score += 12;
                if (contains(maintenance.getMainDev(), token)) score += 8;
                if (contains(maintenance.getSubDev(), token)) score += 8;
                if (contains(maintenance.getSalesRep(), token)) score += 6;
            }

            score += scoreCharacterMatches(characters, 5, maintenance.getMaintenanceName());
            score += scoreCharacterMatches(characters, 2,
                    maintenance.getProgressIssues(),
                    maintenance.getRemarks()
            );
            score += scoreCharacterMatches(characters, 1,
                    maintenance.getMainDev(),
                    maintenance.getSubDev(),
                    maintenance.getSalesRep(),
                    maintenance.getSiteCode(),
                    maintenance.getRegion()
            );
        }

        score = calculateStructuredMatchScore(
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
        List<String> characters = characterTokens(normalizedKeyword);
        double titleSimilarity = maxTypoSimilarity(titleValues, normalizedKeyword);
        double bodySimilarity = maxTypoSimilarity(bodyValues, normalizedKeyword);

        if (containsAny(titleValues, normalizedKeyword)) {
            return TITLE_EXACT_SCORE;
        }

        if (containsAnyWithoutSpace(titleValues, normalizedKeyword)) {
            return TITLE_COMPACT_SCORE;
        }

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

    private int scoreCharacterMatches(List<String> characters, int weight, String... values) {
        if (characters.isEmpty() || values == null || values.length == 0) {
            return 0;
        }

        int matchedCount = 0;
        for (String character : characters) {
            boolean matched = Arrays.stream(values).anyMatch(value -> contains(value, character));
            if (matched) {
                matchedCount++;
            }
        }

        return matchedCount * weight;
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
        int length = compact(keyword).length();
        double threshold = length <= 3 ? 0.66 : 0.74;

        return similarity >= threshold;
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

            best = Math.max(best, typoSimilarityFragment(fragment, query));
            best = Math.max(best, typoSimilarityFragment(compact(fragment), compact(query)));
        }

        return best;
    }

    private void addAsciiFragments(Set<String> fragments, String value) {
        Matcher matcher = ASCII_FRAGMENT_PATTERN.matcher(value);
        while (matcher.find()) {
            fragments.add(matcher.group());
        }
    }

    private double typoSimilarityFragment(String value, String keyword) {
        if (value.isBlank() || keyword.isBlank()) {
            return 0;
        }

        int maxLength = Math.max(value.length(), keyword.length());
        if (maxLength == 0) {
            return 0;
        }

        int distance = damerauLevenshteinDistance(value, keyword);
        double similarity = 1.0 - ((double) distance / maxLength);

        if (commonPrefixLength(value, keyword) >= 3) {
            similarity += 0.08;
        }

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
        int[][] distance = new int[left.length() + 1][right.length() + 1];

        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }

        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                int next = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + cost
                );

                if (
                        i > 1
                                && j > 1
                                && left.charAt(i - 1) == right.charAt(j - 2)
                                && left.charAt(i - 2) == right.charAt(j - 1)
                ) {
                    next = Math.min(next, distance[i - 2][j - 2] + 1);
                }

                distance[i][j] = next;
            }
        }

        return distance[left.length()][right.length()];
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
