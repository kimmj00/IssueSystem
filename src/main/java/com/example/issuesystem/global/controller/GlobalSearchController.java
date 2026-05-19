package com.example.issuesystem.global.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.global.service.GlobalSearchService;
import com.example.issuesystem.common.domain.InfraType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 통합검색 API */
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            // 기존 API 호환용. patchHistorySize/knowledgeSize가 없을 때만 사용한다.
            @RequestParam(required = false) Integer size,

            // 패치이력 결과 페이징 파라미터
            @RequestParam(required = false) Integer patchHistoryPage,
            @RequestParam(required = false) Integer patchHistorySize,

            // 기존 프론트 호환용 파라미터입니다. 새 프론트는 patchHistoryPage/patchHistorySize를 사용합니다.
            @RequestParam(required = false) Integer issuePage,
            @RequestParam(required = false) Integer issueSize,

            // 지식공유 결과 페이징 파라미터
            @RequestParam(required = false) Integer knowledgePage,
            @RequestParam(required = false) Integer knowledgeSize
    ) {
        int fallbackSize = size == null ? 10 : size;
        int resolvedPatchHistoryPage = patchHistoryPage != null ? patchHistoryPage : (issuePage == null ? 0 : issuePage);
        int resolvedPatchHistorySize = patchHistorySize != null ? patchHistorySize : (issueSize == null ? fallbackSize : issueSize);

        return ApiResponse.ok(
                globalSearchService.search(
                        keyword,
                        infraType,
                        customerName,
                        startDate,
                        endDate,
                        resolvedPatchHistoryPage,
                        resolvedPatchHistorySize,
                        knowledgePage == null ? 0 : knowledgePage,
                        knowledgeSize == null ? fallbackSize : knowledgeSize
                )
        );
    }
}
