package com.example.issuesystem.issue.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import com.example.issuesystem.issue.dto.IssueCaseCreateRequest;
import com.example.issuesystem.issue.dto.IssueCaseResponse;
import com.example.issuesystem.issue.dto.IssueCaseUpdateRequest;
import com.example.issuesystem.issue.service.IssueCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/issue-cases")
public class IssueCaseController {

    private final IssueCaseService issueCaseService;

    /**
     * multipart 기반 이슈 등록
     * - request: JSON 본문
     * - files: 첨부파일 목록
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> create(
            @Valid @RequestPart("request") IssueCaseCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.ok(issueCaseService.create(request, files));
    }

    /**
     * JSON 기반 이슈 등록
     * 프론트 초기 개발 단계에서 사용하기 편한 등록 API
     */
    @PostMapping("/json")
    public ApiResponse<Long> createJson(@Valid @RequestBody IssueCaseCreateRequest request) {
        return ApiResponse.ok(issueCaseService.create(request, null));
    }

    /**
     * 이슈 수정
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody IssueCaseUpdateRequest request
    ) {
        issueCaseService.update(id, request);
        return ApiResponse.okMessage("수정되었습니다.");
    }

    /**
     * 이슈 단건 상세 조회
     */
    @GetMapping("/{id}")
    public ApiResponse<IssueCaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(issueCaseService.get(id));
    }

    /**
     * 전체 목록 조회
     * 현재는 search API를 주로 쓰지만 호환용으로 유지
     */
    @GetMapping
    public ApiResponse<List<IssueCaseResponse>> getAll() {
        return ApiResponse.ok(issueCaseService.getAll());
    }

    /**
     * 검색 + 페이징 조회
     * page는 0부터 시작
     */
    @GetMapping("/search")
    public ApiResponse<PageResponse<IssueCaseResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String deploymentVersion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        return ApiResponse.ok(
                issueCaseService.search(keyword, infraType, status, customerName, category, deploymentVersion, page, size)
        );
    }
}