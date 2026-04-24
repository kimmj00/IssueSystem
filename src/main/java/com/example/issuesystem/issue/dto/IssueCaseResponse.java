package com.example.issuesystem.issue.dto;

import com.example.issuesystem.issue.domain.IssueAttachment;
import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class IssueCaseResponse {
    private Long id;
    private String title;
    private InfraType infraType;
    private String systemName;
    private String customerName;
    private String versionInfo;
    private IssueStatus status;
    private String symptomSummary;
    private String symptomDetail;
    private String causeDetail;
    private String actionDetail;
    private String tags;
    private String authorName;
    private LocalDateTime createdAt;
    private List<AttachmentResponse> attachments;
    private String category;
    private String deploymentVersion;

    @Getter
    @Builder
    public static class AttachmentResponse {
        private Long id;
        private String originalFileName;
        private Long fileSize;
    }

    public static IssueCaseResponse from(IssueCase issueCase, List<IssueAttachment> attachments) {
        return IssueCaseResponse.builder()
                .id(issueCase.getId())
                .title(issueCase.getTitle())
                .infraType(issueCase.getInfraType())
                .systemName(issueCase.getSystemName())
                .customerName(issueCase.getCustomerName())
                .versionInfo(issueCase.getVersionInfo())
                .status(issueCase.getStatus())
                .symptomSummary(issueCase.getSymptomSummary())
                .symptomDetail(issueCase.getSymptomDetail())
                .causeDetail(issueCase.getCauseDetail())
                .actionDetail(issueCase.getActionDetail())
                .tags(issueCase.getTags())
                .authorName(issueCase.getAuthorName())
                .createdAt(issueCase.getCreatedAt())
                .attachments(
                        attachments.stream().map(file -> AttachmentResponse.builder()
                                .id(file.getId())
                                .originalFileName(file.getOriginalFileName())
                                .fileSize(file.getFileSize())
                                .build()).toList()
                )
                .category(issueCase.getCategory())
                .deploymentVersion(issueCase.getDeploymentVersion())
                .build();
    }
}