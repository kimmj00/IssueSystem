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

/** ?듯빀寃??API */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/global-search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    /**
     * ?⑥튂?대젰怨?吏?앷났??DB瑜?媛숈? 議곌굔?쇰줈 ?듯빀 寃?됲븳??
     *
     * 蹂寃??ы빆:
     * - 湲곗〈 size ?섎굹濡?媛??먮즺?먮퀎 ?곸쐞 N嫄대쭔 ?쒖떆?섎뜕 援ъ“瑜??쒓굅?쒕떎.
     * - ?⑥튂?대젰 寃곌낵? 吏?앷났??寃곌낵瑜?媛곴컖 ?낅┰?곸쑝濡??섏씠吏뺥븳??
     *
     * ?명솚 泥섎━:
     * - 湲곗〈 ?꾨줎?멸? size留?蹂대궡???숈옉?섎룄濡?size瑜?fallback 媛믪쑝濡??좎??쒕떎.
     * - ?좉퇋 ?꾨줎?몃뒗 patchHistoryPage/patchHistorySize, knowledgePage/knowledgeSize瑜??ъ슜?쒕떎.
     */
    @GetMapping
    public ApiResponse<GlobalSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            // 湲곗〈 API ?명솚?? patchHistorySize/knowledgeSize媛 ?놁쓣 ?뚮쭔 ?ъ슜?쒕떎.
            @RequestParam(required = false) Integer size,

            // ?⑥튂?대젰 寃곌낵 ?섏씠吏??뚮씪誘명꽣
            @RequestParam(required = false) Integer patchHistoryPage,
            @RequestParam(required = false) Integer patchHistorySize,

            // 湲곗〈 ?꾨줎???명솚???뚮씪誘명꽣?낅땲?? ???꾨줎?몃뒗 patchHistoryPage/patchHistorySize瑜??ъ슜?⑸땲??
            @RequestParam(required = false) Integer issuePage,
            @RequestParam(required = false) Integer issueSize,

            // 吏?앷났??寃곌낵 ?섏씠吏??뚮씪誘명꽣
            @RequestParam(required = false) Integer knowledgePage,
            @RequestParam(required = false) Integer knowledgeSize,

            // 작업/이슈이력 유형 필터 (PROJECT | MAINTENANCE)
            @RequestParam(required = false) String workIssueType
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
                        knowledgeSize == null ? fallbackSize : knowledgeSize,
                        workIssueType
                )
        );
    }
}

