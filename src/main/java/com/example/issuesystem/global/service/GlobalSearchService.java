package com.example.issuesystem.global.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.global.dto.GlobalSearchItemResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.dto.IssueCaseResponse;
import com.example.issuesystem.issue.service.IssueCaseService;
import com.example.issuesystem.knowledge.dto.KnowledgeShareResponse;
import com.example.issuesystem.knowledge.service.KnowledgeShareService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 통합검색 서비스
 *
 * 검색 개선 내용:
 * 1. 전체 검색어가 그대로 포함되면 높은 점수
 * 2. 검색어를 token으로 쪼갠 뒤 일부라도 포함되면 점수 부여
 * 3. token이 많이 맞을수록 높은 점수
 * 4. 제목 일치는 높은 점수
 * 5. 상세내용 일치는 낮은 점수
 * 6. DB 후보 조회는 pg_trgm을 사용하고, 화면 일치도 계산은 Java trigram 유사도로 보조 점수 부여
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final IssueCaseService issueCaseService;
    private final KnowledgeShareService knowledgeShareService;

    @Transactional
    public GlobalSearchResponse search(
            String keyword,
            InfraType infraType,
            String customerName,
            LocalDate startDate,
            LocalDate endDate,
            int size
    ) {
        int safeSize = size <= 0 ? 10 : Math.min(size, 50);

        // 일치도 정렬 품질을 높이기 위해 화면 표시 개수보다 더 많은 후보를 가져온다.
        // Repository에서도 점수순 정렬을 적용하므로 candidateSize 50이면 현재 화면 규모에서는 충분하다.
        int candidateSize = Math.min(Math.max(safeSize * 5, safeSize), 50);

        PageResponse<IssueCaseResponse> issuePage = issueCaseService.search(
                keyword,
                infraType,
                null,
                customerName,
                null,
                null,
                startDate,
                endDate,
                0,
                candidateSize
        );

        PageResponse<KnowledgeShareResponse> knowledgePage = knowledgeShareService.search(
                keyword,
                customerName,
                infraType,
                startDate,
                endDate,
                0,
                candidateSize
        );

        List<GlobalSearchItemResponse> issues = issuePage.getContent().stream()
                .map(issue -> toIssueItem(issue, keyword, infraType, customerName))
                .sorted(resultComparator())
                .limit(safeSize)
                .toList();

        List<GlobalSearchItemResponse> knowledgeShares = knowledgePage.getContent().stream()
                .map(item -> toKnowledgeItem(item, keyword, infraType, customerName))
                .sorted(resultComparator())
                .limit(safeSize)
                .toList();

        long issueTotal = issuePage.getTotalElements();
        long knowledgeTotal = knowledgePage.getTotalElements();

        return GlobalSearchResponse.builder()
                .issueTotal(issueTotal)
                .knowledgeTotal(knowledgeTotal)
                .total(issueTotal + knowledgeTotal)
                .issues(issues)
                .knowledgeShares(knowledgeShares)
                .build();
    }

    private GlobalSearchItemResponse toIssueItem(
            IssueCaseResponse issue,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        int score = calculateIssueScore(issue, keyword, infraType, customerName);

        return GlobalSearchItemResponse.builder()
                .sourceType("ISSUE")
                .sourceLabel("이슈관리 시스템")
                .id(issue.getId())
                .title(issue.getTitle())
                .summary(issue.getSymptomSummary())
                .detail(issue.getSymptomDetail())
                .infraTypes(issue.getInfraType() == null ? List.of() : List.of(issue.getInfraType().name()))
                .customerName(issue.getCustomerName())
                .authorName(issue.getAuthorName())
                .createdAt(issue.getCreatedAt())
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

    private Comparator<GlobalSearchItemResponse> resultComparator() {
        return Comparator
                .comparingInt(GlobalSearchItemResponse::getMatchScore)
                .reversed()
                .thenComparing(GlobalSearchItemResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GlobalSearchItemResponse::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int calculateIssueScore(
            IssueCaseResponse issue,
            String keyword,
            InfraType infraType,
            String customerName
    ) {
        String normalizedKeyword = normalize(keyword);
        List<String> tokens = tokenize(normalizedKeyword);
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            // 제목은 가장 중요하므로 가장 높은 가중치
            score += scoreText(issue.getTitle(), normalizedKeyword, tokens, 120, 100, 30, 20);

            // 증상 요약은 목록에서 바로 보이는 핵심 정보
            score += scoreText(issue.getSymptomSummary(), normalizedKeyword, tokens, 80, 65, 20, 12);

            // 상세/원인/조치 내용은 관련도는 있으나 제목보다 낮은 점수
            score += scoreText(issue.getSymptomDetail(), normalizedKeyword, tokens, 50, 40, 12, 8);
            score += scoreText(issue.getActionDetail(), normalizedKeyword, tokens, 35, 28, 8, 5);
            score += scoreText(issue.getCauseDetail(), normalizedKeyword, tokens, 30, 24, 8, 5);

            // 태그/시스템명/고객사는 보조 점수
            score += scoreText(issue.getTags(), normalizedKeyword, tokens, 18, 14, 6, 3);
            score += scoreText(issue.getSystemName(), normalizedKeyword, tokens, 15, 12, 5, 3);
            score += scoreText(issue.getCustomerName(), normalizedKeyword, tokens, 12, 10, 4, 2);
            score += scoreText(issue.getCategory(), normalizedKeyword, tokens, 12, 10, 4, 2);
            score += scoreText(issue.getDeploymentVersion(), normalizedKeyword, tokens, 10, 8, 4, 2);
            score += scoreText(issue.getVersionInfo(), normalizedKeyword, tokens, 10, 8, 4, 2);
        }

        if (infraType != null && infraType.equals(issue.getInfraType())) {
            score += 20;
        }

        if (!normalize(customerName).isBlank() && contains(issue.getCustomerName(), normalize(customerName))) {
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
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            // 제목은 가장 높은 가중치
            score += scoreText(item.getTitle(), normalizedKeyword, tokens, 120, 100, 30, 20);

            // 지식공유 내용은 상세 본문이므로 제목보다 낮은 가중치
            score += scoreText(item.getContent(), normalizedKeyword, tokens, 70, 55, 15, 8);

            // 고객사/담당자는 보조 점수
            score += scoreText(item.getCustomerName(), normalizedKeyword, tokens, 12, 10, 4, 2);
            score += scoreText(item.getAuthorName(), normalizedKeyword, tokens, 10, 8, 3, 2);
        }

        if (infraType != null && item.getInfraTypes() != null && item.getInfraTypes().contains(infraType)) {
            score += 20;
        }

        if (!normalize(customerName).isBlank() && contains(item.getCustomerName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    /**
     * 한 필드에 대한 검색 점수를 계산한다.
     *
     * exactWeight:
     * - 전체 검색어가 그대로 포함될 때 점수
     *
     * compactExactWeight:
     * - 공백 제거 후 전체 검색어가 포함될 때 점수
     * - 예: DB = "에이전트설치", 검색어 = "에이전트 설치"
     *
     * tokenWeight:
     * - 검색어 token 하나가 포함될 때마다 더하는 점수
     *
     * trigramMaxWeight:
     * - 문자열 유사도 보조 점수의 최대치
     */
    private int scoreText(
            String value,
            String normalizedKeyword,
            List<String> tokens,
            int exactWeight,
            int compactExactWeight,
            int tokenWeight,
            int trigramMaxWeight
    ) {
        String normalizedValue = normalize(value);

        if (normalizedValue.isBlank() || normalizedKeyword.isBlank()) {
            return 0;
        }

        int score = 0;

        // 전체 검색어 그대로 포함
        if (normalizedValue.contains(normalizedKeyword)) {
            score += exactWeight;
        }

        // 공백만 다른 경우 보완
        if (compact(normalizedValue).contains(compact(normalizedKeyword))) {
            score += compactExactWeight;
        }

        // token 일부 일치. 많이 맞을수록 점수가 올라간다.
        for (String token : tokens) {
            if (normalizedValue.contains(token)) {
                score += tokenWeight;
            }
        }

        // pg_trgm의 개념과 비슷하게 3글자 조각 기준 Java 유사도를 보조 점수로 사용한다.
        // 실제 DB 후보 조회에는 PostgreSQL pg_trgm similarity가 사용된다.
        score += Math.round(trigramSimilarity(normalizedValue, normalizedKeyword) * trigramMaxWeight);

        return score;
    }

    private String toMatchLevel(int score) {
        if (score >= 120) {
            return "VERY_HIGH";
        }

        if (score >= 60) {
            return "HIGH";
        }

        if (score >= 20) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null || keyword.isBlank()) {
            return false;
        }

        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String compact(String value) {
        return normalize(value).replaceAll("\\s+", "");
    }

    /**
     * 검색어를 공백 기준으로 token 분리한다.
     *
     * 2글자 미만 token은 너무 많은 오탐을 만들 수 있어 제외한다.
     * 예: "a", "1" 같은 단일 글자는 통합검색 후보를 과도하게 넓힌다.
     */
    private List<String> tokenize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();

        for (String token : keyword.trim().toLowerCase().split("\\s+")) {
            String normalizedToken = token.trim();

            if (normalizedToken.length() >= 2) {
                tokens.add(normalizedToken);
            }
        }

        return new ArrayList<>(tokens);
    }

    /**
     * Java 레벨 보조 유사도 계산.
     *
     * PostgreSQL pg_trgm과 100% 같은 구현은 아니지만,
     * 화면의 matchScore를 계산할 때 비슷한 문자열을 약간 더 올려주는 보조 점수로 사용한다.
     */
    private double trigramSimilarity(String a, String b) {
        String left = compact(a);
        String right = compact(b);

        if (left.isBlank() || right.isBlank()) {
            return 0.0;
        }

        Set<String> leftGrams = trigrams(left);
        Set<String> rightGrams = trigrams(right);

        if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
            return left.equals(right) ? 1.0 : 0.0;
        }

        int intersection = 0;

        for (String gram : leftGrams) {
            if (rightGrams.contains(gram)) {
                intersection++;
            }
        }

        int union = leftGrams.size() + rightGrams.size() - intersection;

        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Set<String> trigrams(String value) {
        Set<String> grams = new LinkedHashSet<>();

        if (value.length() < 3) {
            grams.add(value);
            return grams;
        }

        for (int i = 0; i <= value.length() - 3; i++) {
            grams.add(value.substring(i, i + 3));
        }

        return grams;
    }
}
