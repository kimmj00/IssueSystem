package com.example.issuesystem.patchhistory.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.domain.PatchStatus;
import com.example.issuesystem.patchhistory.dto.PatchHistoryCreateRequest;
import com.example.issuesystem.patchhistory.dto.PatchHistoryFilterOptionsResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUpdateRequest;
import com.example.issuesystem.patchhistory.service.PatchHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patch-histories")
public class PatchHistoryController {

    private final PatchHistoryService patchHistoryService;

    /**
     * 패치이력 등록
     *
     * issue_attachment 테이블을 사용하지 않으므로 multipart 기반 첨부파일 등록은 제거했다.
     * 프론트에서는 /api/patch-histories/json을 사용한다.
     * 기존 /api/issue-cases 경로는 IssueCaseController가 처리한다.
     */
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody PatchHistoryCreateRequest request) {
        return ApiResponse.ok(patchHistoryService.create(request));
    }

    /**
     * JSON 기반 패치이력 등록
     *
     * 기본 경로는 /api/patch-histories/json이다.
     */
    @PostMapping("/json")
    public ApiResponse<Long> createJson(@Valid @RequestBody PatchHistoryCreateRequest request) {
        return ApiResponse.ok(patchHistoryService.create(request));
    }

    /** 패치이력 수정 */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchHistoryUpdateRequest request
    ) {
        patchHistoryService.update(id, request);
        return ApiResponse.okMessage("수정되었습니다.");
    }

    /** 패치이력 단건 상세 조회 */
    @GetMapping("/{id}")
    public ApiResponse<PatchHistoryResponse> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(patchHistoryService.get(id));
    }

    /** 전체 목록 조회 */
    @GetMapping
    public ApiResponse<List<PatchHistoryResponse>> getAll() {
        return ApiResponse.ok(patchHistoryService.getAll());
    }

    /**
     * 검색 + 페이징 조회
     *
     * startDate/endDate:
     * - yyyy-MM-dd 형식
     * - endDate는 해당 날짜 전체를 포함하도록 Service에서 다음날 00:00 미만으로 변환한다.
     */
    @GetMapping("/search")
    public ApiResponse<PageResponse<PatchHistoryResponse>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "titleKeyword", required = false) String titleKeyword,
            @RequestParam(name = "contentKeyword", required = false) String contentKeyword,
            @RequestParam(name = "infraType", required = false) InfraType infraType,
            @RequestParam(name = "infraTypes", required = false) List<InfraType> infraTypes,
            @RequestParam(name = "status", required = false) PatchStatus status,
            @RequestParam(name = "customerName", required = false) String customerName,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "categories", required = false) List<String> categories,
            @RequestParam(name = "deploymentVersion", required = false) String deploymentVersion,
            @RequestParam(name = "deploymentVersions", required = false) List<String> deploymentVersions,
            @RequestParam(name = "referenceDeploymentVersion", required = false) String referenceDeploymentVersion,
            @RequestParam(name = "versionRelation", required = false) String versionRelation,
            @RequestParam(name = "detailType", required = false) String detailType,
            @RequestParam(name = "detailDeploymentVersion", required = false) String detailDeploymentVersion,
            @RequestParam(name = "detailVersionRelation", required = false) String detailVersionRelation,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return ApiResponse.ok(
                patchHistoryService.search(
                        keyword,
                        titleKeyword,
                        contentKeyword,
                        infraType,
                        infraTypes,
                        status,
                        customerName,
                        category,
                        categories,
                        deploymentVersion,
                        deploymentVersions,
                        referenceDeploymentVersion,
                        versionRelation,
                        detailType,
                        detailDeploymentVersion,
                        detailVersionRelation,
                        startDate,
                        endDate,
                        page,
                        size
                )
        );
    }

    @GetMapping("/filter-options")
    public ApiResponse<PatchHistoryFilterOptionsResponse> getFilterOptions(
            @RequestParam(name = "categories", required = false) List<String> categories
    ) {
        return ApiResponse.ok(patchHistoryService.getFilterOptions(categories));
    }

    @GetMapping("/deployment-versions")
    public ApiResponse<List<String>> getDeploymentVersions(
            @RequestParam(name = "detailType", required = false) String detailType
    ) {
        return ApiResponse.ok(patchHistoryService.getDeploymentVersions(detailType));
    }
}
