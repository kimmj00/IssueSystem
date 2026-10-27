package com.example.issuesystem.global.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.global.service.GlobalSearchService;
import com.example.issuesystem.global.service.GlobalSearchService.SearchTarget;
import com.example.issuesystem.common.domain.InfraType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** ?통합검색 API */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/global-search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    /**
     * 패치이력과 지식공유 DB를 같은 조건으로 통합 검색한다.
     *
     * 변경 사항:
     * - 기존 size 하나로 각 자료원별 상위 N건만 표시하던 구조를 제거한다.
     * - 패치이력 결과와 지식공유 결과를 각각 독립적으로 페이징한다.
     *
     * 호환 처리:
     * - 기존 프론트가 size만 보내도 동작하도록 size를 fallback 값으로 유지한다.
     * - 신규 프론트는 patchHistoryPage/patchHistorySize, knowledgePage/knowledgeSize를 사용한다.
     */
    @GetMapping
    public ApiResponse<GlobalSearchResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "titleKeyword", required = false) String titleKeyword,
            @RequestParam(name = "contentKeyword", required = false) String contentKeyword,
            @RequestParam(name = "infraType", required = false) InfraType infraType,
            @RequestParam(name = "customerName", required = false) String customerName,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            // 기존 API 호환용. patchHistorySize/knowledgeSize가 없을 때만 사용한다.
            @RequestParam(name = "size", required = false) Integer size,

            // 패치이력 결과 페이징 파라미터
            @RequestParam(name = "patchHistoryPage", required = false) Integer patchHistoryPage,
            @RequestParam(name = "patchHistorySize", required = false) Integer patchHistorySize,

            // 기존 프론트 호환용 파라미터입니다. 새 프론트는 patchHistoryPage/patchHistorySize를 사용합니다.
            @RequestParam(name = "issuePage", required = false) Integer issuePage,
            @RequestParam(name = "issueSize", required = false) Integer issueSize,

            // 지식공유 결과 페이징 파라미터
            @RequestParam(name = "knowledgePage", required = false) Integer knowledgePage,
            @RequestParam(name = "knowledgeSize", required = false) Integer knowledgeSize,

            // 작업/이슈이력 결과 페이지 파라미터
            @RequestParam(name = "workIssuePage", required = false) Integer workIssuePage,
            @RequestParam(name = "workIssueSize", required = false) Integer workIssueSize,

            // 작업/이슈이력 유형 필터 (PROJECT | MAINTENANCE)
            @RequestParam(name = "workIssueType", required = false) String workIssueType,

            // 부분 조회 대상. 예: PATCH_HISTORY, KNOWLEDGE, WORK_ISSUE
            @RequestParam(name = "targets", required = false) String targets
    ) {
        int fallbackSize = size == null ? 10 : size;
        int workIssueFallbackSize = size == null ? 7 : size;
        int resolvedPatchHistoryPage = patchHistoryPage != null ? patchHistoryPage : (issuePage == null ? 0 : issuePage);
        int resolvedPatchHistorySize = patchHistorySize != null ? patchHistorySize : (issueSize == null ? fallbackSize : issueSize);
        int resolvedWorkIssuePage = workIssuePage == null ? 0 : workIssuePage;
        int resolvedWorkIssueSize = workIssueSize == null ? workIssueFallbackSize : workIssueSize;

        return ApiResponse.ok(
                globalSearchService.search(
                        keyword,
                        titleKeyword,
                        contentKeyword,
                        infraType,
                        customerName,
                        startDate,
                        endDate,
                        resolvedPatchHistoryPage,
                        resolvedPatchHistorySize,
                        knowledgePage == null ? 0 : knowledgePage,
                        knowledgeSize == null ? fallbackSize : knowledgeSize,
                        resolvedWorkIssuePage,
                        resolvedWorkIssueSize,
                        workIssueType,
                        parseTargets(targets)
                )
        );
    }

    private Set<SearchTarget> parseTargets(String targets) {
        if (targets == null || targets.isBlank() || "ALL".equalsIgnoreCase(targets.trim())) {
            return EnumSet.allOf(SearchTarget.class);
        }

        EnumSet<SearchTarget> result = EnumSet.noneOf(SearchTarget.class);
        for (String token : targets.split(",")) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }

            switch (normalized) {
                case "PATCH_HISTORY", "PATCH" -> result.add(SearchTarget.PATCH_HISTORY);
                case "KNOWLEDGE", "KNOWLEDGE_SHARE" -> result.add(SearchTarget.KNOWLEDGE);
                case "WORK_ISSUE", "WORK_ISSUE_HISTORY" -> result.add(SearchTarget.WORK_ISSUE);
                default -> {
                    // 알 수 없는 값은 무시하고, 전부 무시된 경우 전체 조회로 fallback 한다.
                }
            }
        }

        return result.isEmpty() ? EnumSet.allOf(SearchTarget.class) : result;
    }
}

