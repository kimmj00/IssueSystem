package com.example.issuesystem.issue.dto;

import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueCaseUpdateRequest {
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotNull(message = "인프라 유형은 필수입니다.")
    private InfraType infraType;

    @NotBlank(message = "시스템명은 필수입니다.")
    private String systemName;

    private String customerName;
    private String versionInfo;

    @NotNull(message = "상태는 필수입니다.")
    private IssueStatus status;

    @NotBlank(message = "증상 요약은 필수입니다.")
    private String symptomSummary;

    @NotBlank(message = "증상 상세는 필수입니다.")
    private String symptomDetail;

    private String causeDetail;
    private String actionDetail;
    private String tags;
}