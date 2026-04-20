package com.example.issuesystem.issue.controller;

import com.example.issuesystem.common.ApiResponse;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> create(
            @Valid @RequestPart("request") IssueCaseCreateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ApiResponse.ok(issueCaseService.create(request, files));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody IssueCaseUpdateRequest request
    ) {
        issueCaseService.update(id, request);
        return ApiResponse.okMessage("수정되었습니다.");
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueCaseResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(issueCaseService.get(id));
    }

    @GetMapping
    public ApiResponse<List<IssueCaseResponse>> getAll() {
        return ApiResponse.ok(issueCaseService.getAll());
    }

    @PostMapping("/json")
    public ApiResponse<Long> createJson(@Valid @RequestBody IssueCaseCreateRequest request) {
        return ApiResponse.ok(issueCaseService.create(request, null));
    }

    @GetMapping("/search")
    public ApiResponse<List<IssueCaseResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InfraType infraType,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) String customerName
    ) {
        return ApiResponse.ok(issueCaseService.search(keyword, infraType, status, customerName));
    }
}
