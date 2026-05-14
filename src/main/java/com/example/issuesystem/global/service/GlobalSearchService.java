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
import java.util.Comparator;
import java.util.List;

/**
 * 통합검색 서비스
 *
 * 최적화 방향:
 * 1. 프론트에서 API 2개를 따로 호출하지 않고 백엔드 단일 API로 통합한다.
 * 2. 각 자료원별 count는 PageResponse의 totalElements를 사용한다.
 * 3. 화면 표시용 후보는 size보다 넉넉히 가져온 뒤 일치도 점수로 정렬한다.
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
        // 너무 크게 잡으면 초기 검색이 느려지므로 최대 50건으로 제한한다.
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
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (contains(issue.getTitle(), normalizedKeyword)) score += 5;
            if (contains(issue.getSymptomSummary(), normalizedKeyword)) score += 4;
            if (contains(issue.getSymptomDetail(), normalizedKeyword)) score += 3;
            if (contains(issue.getCauseDetail(), normalizedKeyword)) score += 2;
            if (contains(issue.getActionDetail(), normalizedKeyword)) score += 2;
            if (contains(issue.getTags(), normalizedKeyword)) score += 1;
            if (contains(issue.getCustomerName(), normalizedKeyword)) score += 1;
        }

        if (infraType != null && infraType.equals(issue.getInfraType())) {
            score += 3;
        }

        if (!normalize(customerName).isBlank() && contains(issue.getCustomerName(), normalize(customerName))) {
            score += 3;
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
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (contains(item.getTitle(), normalizedKeyword)) score += 5;
            if (contains(item.getContent(), normalizedKeyword)) score += 4;
            if (contains(item.getCustomerName(), normalizedKeyword)) score += 1;
            if (contains(item.getAuthorName(), normalizedKeyword)) score += 1;
        }

        if (infraType != null && item.getInfraTypes() != null && item.getInfraTypes().contains(infraType)) {
            score += 3;
        }

        if (!normalize(customerName).isBlank() && contains(item.getCustomerName(), normalize(customerName))) {
            score += 3;
        }

        return score;
    }

    private String toMatchLevel(int score) {
        if (score >= 8) {
            return "VERY_HIGH";
        }

        if (score >= 5) {
            return "HIGH";
        }

        if (score >= 2) {
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
}
