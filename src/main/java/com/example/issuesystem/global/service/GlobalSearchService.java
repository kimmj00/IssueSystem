package com.example.issuesystem.global.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.global.dto.GlobalSearchItemResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.dto.PatchHistoryResponse;
import com.example.issuesystem.patchhistory.service.PatchHistoryService;
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
 * 1. 패치이력/지식공유를 각각 PageResponse로 조회한다.
 * 2. 기존처럼 최대 50건으로 잘라서 보여주는 구조를 제거한다.
 * 3. DB 쿼리에서 이미 검색 점수 순으로 정렬하므로 Service에서는 결과 순서를 다시 섞지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final PatchHistoryService patchHistoryService;
    private final KnowledgeShareService knowledgeShareService;

    @Transactional
    public GlobalSearchResponse search(
            String keyword,
            InfraType infraType,
            String customerName,
            LocalDate startDate,
            LocalDate endDate,
            int patchHistoryPage,
            int patchHistorySize,
            int knowledgePage,
            int knowledgeSize
    ) {
        int safePatchHistoryPage = Math.max(patchHistoryPage, 0);
        int safeKnowledgePage = Math.max(knowledgePage, 0);

        // 한 번에 너무 많이 가져오면 화면과 DB 모두 부담이 커지므로 50개까지만 허용한다.
        int safePatchHistorySize = normalizeSize(patchHistorySize, 10);
        int safeKnowledgeSize = normalizeSize(knowledgeSize, 10);

        PageResponse<PatchHistoryResponse> patchHistoryPageResult = patchHistoryService.search(
                keyword,
                infraType,
                null,
                customerName,
                null,
                null,
                startDate,
                endDate,
                safePatchHistoryPage,
                safePatchHistorySize
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

        List<GlobalSearchItemResponse> patchHistories = patchHistoryPageResult.getContent().stream()
                .map(patchHistory -> toPatchHistoryItem(patchHistory, keyword, infraType, customerName))
                .toList();

        List<GlobalSearchItemResponse> knowledgeShares = knowledgePageResult.getContent().stream()
                .map(item -> toKnowledgeItem(item, keyword, infraType, customerName))
                .toList();

        long patchHistoryTotal = patchHistoryPageResult.getTotalElements();
        long knowledgeTotal = knowledgePageResult.getTotalElements();

        return GlobalSearchResponse.builder()
                .patchHistoryTotal(patchHistoryTotal)
                .knowledgeTotal(knowledgeTotal)
                .total(patchHistoryTotal + knowledgeTotal)
                .patchHistories(patchHistories)
                .knowledgeShares(knowledgeShares)
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
        }

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
