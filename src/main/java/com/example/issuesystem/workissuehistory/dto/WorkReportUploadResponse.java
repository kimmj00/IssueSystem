package com.example.issuesystem.workissuehistory.dto;

import com.example.issuesystem.workissuehistory.domain.WorkReportUpload;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkReportUploadResponse {
    private Long uploadId;
    private String originalFileName;
    private String reportWeek;
    private String uploadedBy;
    private int projectCount;
    private int maintenanceCount;
    private LocalDateTime createdAt;

    /** 업로드 이력 엔티티를 화면 응답 DTO로 변환합니다. */
    public static WorkReportUploadResponse from(WorkReportUpload upload) {
        return WorkReportUploadResponse.builder()
                .uploadId(upload.getId())
                .originalFileName(upload.getOriginalFileName())
                .reportWeek(upload.getReportWeek())
                .uploadedBy(upload.getUploadedBy())
                .projectCount(upload.getProjectCount())
                .maintenanceCount(upload.getMaintenanceCount())
                .createdAt(upload.getCreatedAt())
                .build();
    }
}
