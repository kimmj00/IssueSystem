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
@RequestMapping({"/api/patch-histories", "/api/issue-cases"})
public class PatchHistoryController {

    private final PatchHistoryService patchHistoryService;

    /**
     * 패치이력 등록
     *
     * issue_attachment 테이블을 사용하지 않으므로 multipart 기반 첨부파일 등록은 제거했다.
     * 프론트에서는 /api/patch-histories/json을 사용하고, 기존 /api/issue-cases/json은 호환 경로로 유지한다.
     */
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody PatchHistoryCreateRequest request) {
        return ApiResponse.ok(patchHistoryService.create(request));
    }

    /**
     * JSON 기반 패치이력 등록
     *
     * 기본 경로는 /api/patch-histories/json이며, 기존 /api/issue-cases/json도 같은 컨트롤러에서 호환 처리한다.
     */
    @PostMapping("/json")
    public ApiResponse<Long> createJson(@Valid @RequestBody PatchHistoryCreateRequest request) {
        return ApiResponse.ok(patchHistoryService.create(request));
    }

    /** 패치이력 수정 */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody PatchHistoryUpdateRequest request
    ) {
        patchHistoryService.update(id, request);
        return ApiResponse.okMessage("수정되었습니다.");
    }

    /** 패치이력 단건 상세 조회 */
    @GetMapping("/{id}")
    public ApiResponse<PatchHistoryResponse> get(@PathVariable Long id) {
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) List<InfraType> infraTypes,
            @RequestParam(required = false) PatchStatus status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) String deploymentVersion,
            @RequestParam(required = false) List<String> deploymentVersions,
            @RequestParam(required = false) String referenceDeploymentVersion,
            @RequestParam(required = false) String versionRelation,
            @RequestParam(required = false) String detailType,
            @RequestParam(required = false) String detailDeploymentVersion,
            @RequestParam(required = false) String detailVersionRelation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(
                patchHistoryService.search(
                        keyword,
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
            @RequestParam(required = false) List<String> categories
    ) {
        return ApiResponse.ok(patchHistoryService.getFilterOptions(categories));
    }

    @GetMapping("/deployment-versions")
    public ApiResponse<List<String>> getDeploymentVersions(
            @RequestParam(required = false) String detailType
    ) {
        return ApiResponse.ok(patchHistoryService.getDeploymentVersions(detailType));
    }
}
