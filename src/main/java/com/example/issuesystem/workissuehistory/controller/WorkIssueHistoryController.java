package com.example.issuesystem.workissuehistory.controller;

import com.example.issuesystem.common.ApiResponse;
import com.example.issuesystem.workissuehistory.dto.WorkIssueSummaryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkIssueUploadResultResponse;
import com.example.issuesystem.workissuehistory.dto.WorkMaintenanceHistoryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkProjectHistoryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkReportUploadResponse;
import com.example.issuesystem.workissuehistory.service.WorkIssueHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work-issue-histories")
public class WorkIssueHistoryController {

    private final WorkIssueHistoryService workIssueHistoryService;

    /**
     * 주간보고 엑셀 업로드 API입니다.
     * 프론트는 파일만 전송하고, 실제 엑셀 파싱과 DB 저장은 백엔드에서 처리합니다.
     */
    @PostMapping("/upload")
    public ApiResponse<WorkIssueUploadResultResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String reportWeek,
            @RequestParam(required = false) String uploadedBy
    ) {
        return ApiResponse.ok(workIssueHistoryService.upload(file, reportWeek, uploadedBy));
    }

    /** 최근 업로드 이력 목록입니다. */
    @GetMapping("/uploads")
    public ApiResponse<List<WorkReportUploadResponse>> getUploads() {
        return ApiResponse.ok(workIssueHistoryService.getUploads());
    }

    /** 화면 상단 요약 카드 데이터입니다. uploadId가 없으면 최신 업로드 건 기준입니다. */
    @GetMapping("/summary")
    public ApiResponse<WorkIssueSummaryResponse> getSummary(@RequestParam(required = false) Long uploadId) {
        return ApiResponse.ok(workIssueHistoryService.getSummary(uploadId));
    }

    /** 프로젝트 현황 목록입니다. uploadId가 없으면 최신 업로드 건 기준입니다. */
    @GetMapping("/projects")
    public ApiResponse<List<WorkProjectHistoryResponse>> getProjects(@RequestParam(required = false) Long uploadId) {
        return ApiResponse.ok(workIssueHistoryService.getProjects(uploadId));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<WorkProjectHistoryResponse> getProject(@PathVariable Long id) {
        return ApiResponse.ok(workIssueHistoryService.getProject(id));
    }

    /** 유지보수 현황 목록입니다. uploadId가 없으면 최신 업로드 건 기준입니다. */
    @GetMapping("/maintenance")
    public ApiResponse<List<WorkMaintenanceHistoryResponse>> getMaintenance(@RequestParam(required = false) Long uploadId) {
        return ApiResponse.ok(workIssueHistoryService.getMaintenance(uploadId));
    }

    @GetMapping("/maintenance/{id}")
    public ApiResponse<WorkMaintenanceHistoryResponse> getMaintenanceItem(@PathVariable Long id) {
        return ApiResponse.ok(workIssueHistoryService.getMaintenanceItem(id));
    }
}
