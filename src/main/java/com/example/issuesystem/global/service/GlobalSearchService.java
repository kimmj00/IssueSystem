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
import java.util.Arrays;
import java.util.List;

/**
 * 통합검색 서비스
 *
 * 변경 사항:
 * 1. 이슈/지식공유를 각각 PageResponse로 조회한다.
 * 2. 기존처럼 최대 50건으로 잘라서 보여주는 구조를 제거한다.
 * 3. DB 쿼리에서 이미 검색 점수 순으로 정렬하므로 Service에서는 결과 순서를 다시 섞지 않는다.
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
            int issuePage,
            int issueSize,
            int knowledgePage,
            int knowledgeSize
    ) {
        int safeIssuePage = Math.max(issuePage, 0);
        int safeKnowledgePage = Math.max(knowledgePage, 0);

        // 한 번에 너무 많이 가져오면 화면과 DB 모두 부담이 커지므로 50개까지만 허용한다.
        int safeIssueSize = normalizeSize(issueSize, 10);
        int safeKnowledgeSize = normalizeSize(knowledgeSize, 10);

        PageResponse<IssueCaseResponse> issuePageResult = issueCaseService.search(
                keyword,
                infraType,
                null,
                customerName,
                null,
                null,
                startDate,
                endDate,
                safeIssuePage,
                safeIssueSize
        );

        PageResponse<KnowledgeShareResponse> knowledgePageResult = knowledgeShareService.search(
                keyword,
                customerName,
                infraType,
                startDate,
                endDate,
                safeKnowledgePage,
                safeKnowledgeSize
        );

        List<GlobalSearchItemResponse> issues = issuePageResult.getContent().stream()
                .map(issue -> toIssueItem(issue, keyword, infraType, customerName))
                .toList();

        List<GlobalSearchItemResponse> knowledgeShares = knowledgePageResult.getContent().stream()
                .map(item -> toKnowledgeItem(item, keyword, infraType, customerName))
                .toList();

        long issueTotal = issuePageResult.getTotalElements();
        long knowledgeTotal = knowledgePageResult.getTotalElements();

        return GlobalSearchResponse.builder()
                .issueTotal(issueTotal)
                .knowledgeTotal(knowledgeTotal)
                .total(issueTotal + knowledgeTotal)
                .issues(issues)
                .knowledgeShares(knowledgeShares)
                .issuePage(issuePageResult.getPage())
                .issueSize(issuePageResult.getSize())
                .issueTotalPages(issuePageResult.getTotalPages())
                .issueHasNext(issuePageResult.isHasNext())
                .issueHasPrevious(issuePageResult.isHasPrevious())
                .knowledgePage(knowledgePageResult.getPage())
                .knowledgeSize(knowledgePageResult.getSize())
                .knowledgeTotalPages(knowledgePageResult.getTotalPages())
                .knowledgeHasNext(knowledgePageResult.isHasNext())
                .knowledgeHasPrevious(knowledgePageResult.isHasPrevious())
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

    /** 페이지 크기는 기본 10개, 최대 50개로 제한한다. */
    private int normalizeSize(int size, int defaultSize) {
        if (size <= 0) {
            return defaultSize;
        }

        return Math.min(size, 50);
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
            // 전체 검색어가 그대로 포함되면 높은 점수를 준다.
            if (contains(issue.getTitle(), normalizedKeyword)) score += 120;
            if (containsWithoutSpace(issue.getTitle(), normalizedKeyword)) score += 100;
            if (contains(issue.getSymptomSummary(), normalizedKeyword)) score += 80;
            if (contains(issue.getSymptomDetail(), normalizedKeyword)) score += 50;
            if (contains(issue.getActionDetail(), normalizedKeyword)) score += 35;
            if (contains(issue.getCauseDetail(), normalizedKeyword)) score += 30;

            // 검색어를 쪼갠 token이 많이 맞을수록 점수를 올린다.
            for (String token : tokens) {
                if (contains(issue.getTitle(), token)) score += 30;
                if (contains(issue.getSymptomSummary(), token)) score += 20;
                if (contains(issue.getSymptomDetail(), token)) score += 12;
                if (contains(issue.getActionDetail(), token)) score += 8;
                if (contains(issue.getCauseDetail(), token)) score += 8;
                if (contains(issue.getTags(), token)) score += 6;
                if (contains(issue.getSystemName(), token)) score += 6;
                if (contains(issue.getCustomerName(), token)) score += 6;
                if (contains(issue.getCategory(), token)) score += 4;
                if (contains(issue.getDeploymentVersion(), token)) score += 4;
                if (contains(issue.getVersionInfo(), token)) score += 4;
            }
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
        }

        if (infraType != null && item.getInfraTypes() != null && item.getInfraTypes().contains(infraType)) {
            score += 20;
        }

        if (!normalize(customerName).isBlank() && contains(item.getCustomerName(), normalize(customerName))) {
            score += 20;
        }

        return score;
    }

    private String toMatchLevel(int score) {
        if (score >= 140) {
            return "VERY_HIGH";
        }

        if (score >= 80) {
            return "HIGH";
        }

        if (score >= 20) {
            return "MEDIUM";
        }

        return "LOW";
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
