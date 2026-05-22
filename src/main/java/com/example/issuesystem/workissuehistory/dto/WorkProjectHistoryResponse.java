package com.example.issuesystem.workissuehistory.dto;

import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkProjectHistoryResponse {
    private Long id;
    private Long uploadId;
    private int rowNo;
    private String no;
    private String salesRep;
    private String clientName;
    private String scope;
    private String oz;
    private String dashboard;
    private String apm;
    private String location;
    private String startDate;
    private String projectScale;
    private String executors;
    private Double visits;
    private Double md;
    private String progressLogs;
    private String remainingIssues;
    private String siteCode;
    private LocalDateTime createdAt;

    /** 프로젝트 현황 엔티티를 화면 응답 DTO로 변환합니다. */
    public static WorkProjectHistoryResponse from(WorkProjectHistory item) {
        return WorkProjectHistoryResponse.builder()
                .id(item.getId())
                .uploadId(item.getUpload().getId())
                .rowNo(item.getRowNo())
                .no(item.getNo())
                .salesRep(item.getSalesRep())
                .clientName(item.getClientName())
                .scope(item.getScope())
                .oz(item.getOz())
                .dashboard(item.getDashboard())
                .apm(item.getApm())
                .location(item.getLocation())
                .startDate(item.getStartDate())
                .projectScale(item.getProjectScale())
                .executors(item.getExecutors())
                .visits(item.getVisits())
                .md(item.getMd())
                .progressLogs(item.getProgressLogs())
                .remainingIssues(item.getRemainingIssues())
                .siteCode(item.getSiteCode())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
