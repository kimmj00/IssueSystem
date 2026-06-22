package com.example.issuesystem.patchhistory.dto;

import com.example.issuesystem.patchhistory.domain.PatchHistory;
import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.domain.PatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PatchHistoryResponse {
    private Long id;
    private String title;
    private InfraType infraType;
    private String systemName;
    private String customerName;
    private String versionInfo;
    private PatchStatus status;
    private String content;
    private String symptomSummary;
    private String symptomDetail;
    private String causeDetail;
    private String actionDetail;
    private String tags;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDate completedDate;
    private String category;
    private String deploymentVersion;

    /**
     * PatchHistory 엔티티를 화면 응답 DTO로 변환한다.
     *
     * issue_attachment 테이블을 사용하지 않으므로 attachments 필드와 AttachmentResponse는 제거했다.
     */
    public static PatchHistoryResponse from(PatchHistory patchHistory) {
        return PatchHistoryResponse.builder()
                .id(patchHistory.getId())
                .title(patchHistory.getTitle())
                .infraType(patchHistory.getInfraType())
                .systemName(patchHistory.getSystemName())
                .customerName(patchHistory.getCustomerName())
                .versionInfo(patchHistory.getVersionInfo())
                .status(patchHistory.getStatus())
                .content(patchHistory.getSymptomDetail())
                .symptomSummary(patchHistory.getSymptomSummary())
                .symptomDetail(patchHistory.getSymptomDetail())
                .causeDetail(patchHistory.getCauseDetail())
                .actionDetail(patchHistory.getActionDetail())
                .tags(patchHistory.getTags())
                .authorName(patchHistory.getAuthorName())
                .createdAt(patchHistory.getCreatedAt())
                .completedDate(patchHistory.getCompletedDate())
                .category(patchHistory.getCategory())
                .deploymentVersion(patchHistory.getDeploymentVersion())
                .build();
    }
}
