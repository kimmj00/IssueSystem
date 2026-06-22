package com.example.issuesystem.patchhistory.dto;

import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.domain.PatchStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PatchHistoryUpdateRequest {
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotNull(message = "인프라 유형은 필수입니다.")
    private InfraType infraType;

    @NotBlank(message = "시스템명은 필수입니다.")
    private String systemName;

    private String customerName;
    private String versionInfo;

    @NotNull(message = "상태는 필수입니다.")
    private PatchStatus status;

    private String symptomSummary;

    private String symptomDetail;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private String causeDetail;
    private String actionDetail;
    private String tags;
    private String category;
    private String deploymentVersion;
    private LocalDate completedDate;
}
