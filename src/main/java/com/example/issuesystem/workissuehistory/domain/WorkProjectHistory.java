package com.example.issuesystem.workissuehistory.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "work_project_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkProjectHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 엑셀 업로드에서 만들어진 행인지 추적하기 위한 부모 업로드 이력입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id", nullable = false)
    private WorkReportUpload upload;

    // 엑셀의 실제 행 번호입니다. 장애 분석이나 재확인 때 원본 행을 찾기 쉽게 저장합니다.
    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "excel_no", length = 30)
    private String no;

    @Column(name = "sales_rep", length = 100)
    private String salesRep;

    @Column(name = "client_name", nullable = false, length = 255)
    private String clientName;

    @Column(name = "scope", columnDefinition = "text")
    private String scope;

    @Column(name = "oz", length = 20)
    private String oz;

    @Column(name = "dashboard", length = 20)
    private String dashboard;

    @Column(name = "apm", length = 20)
    private String apm;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "start_date", length = 50)
    private String startDate;

    @Column(name = "project_scale", length = 100)
    private String projectScale;

    @Column(name = "executors", length = 255)
    private String executors;

    @Column(name = "visits")
    private Double visits;

    @Column(name = "md")
    private Double md;

    // 금주 실적/진행내역 원문입니다. 날짜별 분리는 프론트에서 화면용으로 처리합니다.
    @Column(name = "progress_logs", columnDefinition = "text")
    private String progressLogs;

    @Column(name = "remaining_issues", columnDefinition = "text")
    private String remainingIssues;

    @Column(name = "site_code", length = 100)
    private String siteCode;

    @Builder
    public WorkProjectHistory(
            WorkReportUpload upload,
            int rowNo,
            String no,
            String salesRep,
            String clientName,
            String scope,
            String oz,
            String dashboard,
            String apm,
            String location,
            String startDate,
            String projectScale,
            String executors,
            Double visits,
            Double md,
            String progressLogs,
            String remainingIssues,
            String siteCode
    ) {
        this.upload = upload;
        this.rowNo = rowNo;
        this.no = no;
        this.salesRep = salesRep;
        this.clientName = clientName;
        this.scope = scope;
        this.oz = oz;
        this.dashboard = dashboard;
        this.apm = apm;
        this.location = location;
        this.startDate = startDate;
        this.projectScale = projectScale;
        this.executors = executors;
        this.visits = visits;
        this.md = md;
        this.progressLogs = progressLogs;
        this.remainingIssues = remainingIssues;
        this.siteCode = siteCode;
    }
}
