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
@Table(name = "work_maintenance_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkMaintenanceHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 엑셀 업로드에서 만들어진 행인지 추적하기 위한 부모 업로드 이력입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id", nullable = false)
    private WorkReportUpload upload;

    // 엑셀의 실제 행 번호입니다.
    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "excel_no", length = 30)
    private String no;

    @Column(name = "maintenance_name", nullable = false, length = 255)
    private String maintenanceName;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "pg_version", length = 100)
    private String pgVersion;

    @Column(name = "web_version", length = 100)
    private String webVersion;

    @Column(name = "status_date", length = 50)
    private String statusDate;

    @Column(name = "is_uploaded", length = 50)
    private String isUploaded;

    @Column(name = "sms_status", length = 100)
    private String smsStatus;

    @Column(name = "nms_status", length = 100)
    private String nmsStatus;

    @Column(name = "oz", length = 20)
    private String oz;

    @Column(name = "dashboard", length = 20)
    private String dashboard;

    @Column(name = "siem", length = 20)
    private String siem;

    @Column(name = "apm", length = 20)
    private String apm;

    @Column(name = "sales_grade", length = 100)
    private String salesGrade;

    @Column(name = "contract_type", length = 100)
    private String contractType;

    @Column(name = "visit_type", length = 100)
    private String visitType;

    @Column(name = "cycle", length = 100)
    private String cycle;

    @Column(name = "method", length = 100)
    private String method;

    @Column(name = "contract_start", length = 50)
    private String contractStart;

    @Column(name = "contract_end", length = 50)
    private String contractEnd;

    @Column(name = "visits")
    private Double visits;

    @Column(name = "md")
    private Double md;

    @Column(name = "region", length = 100)
    private String region;

    // 정기점검 월별 수행일자를 JSON이 아닌 단순 텍스트로 저장합니다.
    // 형식: 10월=2025-10-01\n11월=...
    @Column(name = "inspection_dates", columnDefinition = "text")
    private String inspectionDates;

    @Column(name = "progress_issues", columnDefinition = "text")
    private String progressIssues;

    @Column(name = "sales_rep", length = 100)
    private String salesRep;

    @Column(name = "main_dev", length = 100)
    private String mainDev;

    @Column(name = "sub_dev", length = 100)
    private String subDev;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "site_code", length = 100)
    private String siteCode;

    @Builder
    public WorkMaintenanceHistory(
            WorkReportUpload upload,
            int rowNo,
            String no,
            String maintenanceName,
            String version,
            String pgVersion,
            String webVersion,
            String statusDate,
            String isUploaded,
            String smsStatus,
            String nmsStatus,
            String oz,
            String dashboard,
            String siem,
            String apm,
            String salesGrade,
            String contractType,
            String visitType,
            String cycle,
            String method,
            String contractStart,
            String contractEnd,
            Double visits,
            Double md,
            String region,
            String inspectionDates,
            String progressIssues,
            String salesRep,
            String mainDev,
            String subDev,
            String remarks,
            String siteCode
    ) {
        this.upload = upload;
        this.rowNo = rowNo;
        this.no = no;
        this.maintenanceName = maintenanceName;
        this.version = version;
        this.pgVersion = pgVersion;
        this.webVersion = webVersion;
        this.statusDate = statusDate;
        this.isUploaded = isUploaded;
        this.smsStatus = smsStatus;
        this.nmsStatus = nmsStatus;
        this.oz = oz;
        this.dashboard = dashboard;
        this.siem = siem;
        this.apm = apm;
        this.salesGrade = salesGrade;
        this.contractType = contractType;
        this.visitType = visitType;
        this.cycle = cycle;
        this.method = method;
        this.contractStart = contractStart;
        this.contractEnd = contractEnd;
        this.visits = visits;
        this.md = md;
        this.region = region;
        this.inspectionDates = inspectionDates;
        this.progressIssues = progressIssues;
        this.salesRep = salesRep;
        this.mainDev = mainDev;
        this.subDev = subDev;
        this.remarks = remarks;
        this.siteCode = siteCode;
    }
}
