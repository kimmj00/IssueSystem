package com.example.issuesystem.global.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.global.service.GlobalSearchService;
import com.example.issuesystem.issue.domain.InfraType;
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
     * 이슈관리 시스템과 지식공유 DB를 같은 조건으로 통합 검색한다.
     *
     * page는 두 자료원을 한 화면에 요약 표시하는 구조라 우선 0페이지 기준으로 조회한다.
     * size는 각 자료원별 표시 개수다.
     */
    @GetMapping
    public ApiResponse<GlobalSearchResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(
                globalSearchService.search(
                        keyword,
                        infraType,
                        customerName,
                        startDate,
                        endDate,
                        size
                )
        );
    }
}
