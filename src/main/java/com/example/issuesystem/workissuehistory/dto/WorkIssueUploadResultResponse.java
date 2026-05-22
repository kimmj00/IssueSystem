package com.example.issuesystem.workissuehistory.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkIssueUploadResultResponse {
    private Long uploadId;
    private String originalFileName;
    private String reportWeek;
    private int projectCount;
    private int maintenanceCount;

    /**
     * 업로드 완료 알림에 필요한 최소 결과만 내려줍니다.
     * 실제 목록 데이터는 업로드 후 조회 API를 다시 호출해서 가져옵니다.
     */
    public static WorkIssueUploadResultResponse of(
            Long uploadId,
            String originalFileName,
            String reportWeek,
            int projectCount,
            int maintenanceCount
    ) {
        return WorkIssueUploadResultResponse.builder()
                .uploadId(uploadId)
                .originalFileName(originalFileName)
                .reportWeek(reportWeek)
                .projectCount(projectCount)
                .maintenanceCount(maintenanceCount)
                .build();
    }
}
