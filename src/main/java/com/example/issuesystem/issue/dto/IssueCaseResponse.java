package com.example.issuesystem.issue.dto;

import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private String category;
    private String deploymentVersion;

    /**
     * IssueCase 엔티티를 화면 응답 DTO로 변환한다.
     *
     * issue_attachment 테이블을 사용하지 않으므로 attachments 필드와 AttachmentResponse는 제거했다.
     */
    public static IssueCaseResponse from(IssueCase issueCase) {
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
                .category(issueCase.getCategory())
                .deploymentVersion(issueCase.getDeploymentVersion())
                .build();
    }
}
