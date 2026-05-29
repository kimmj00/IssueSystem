package com.example.issuesystem.workissuehistory.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkIssueSummaryResponse {
    private Long uploadId;
    private String reportWeek;
    private String originalFileName;
    private int projectCount;
    private int maintenanceCount;
    private double projectMdTotal;
    private double maintenanceMdTotal;

    /** 화면 상단 카드에 표시할 요약 값을 묶어서 내려줍니다. */
    public static WorkIssueSummaryResponse of(
            Long uploadId,
            String reportWeek,
            String originalFileName,
            int projectCount,
            int maintenanceCount,
            double projectMdTotal,
            double maintenanceMdTotal
    ) {
        return WorkIssueSummaryResponse.builder()
                .uploadId(uploadId)
                .reportWeek(reportWeek)
                .originalFileName(originalFileName)
                .projectCount(projectCount)
                .maintenanceCount(maintenanceCount)
                .projectMdTotal(projectMdTotal)
                .maintenanceMdTotal(maintenanceMdTotal)
                .build();
    }
}
