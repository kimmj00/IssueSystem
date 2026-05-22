package com.example.issuesystem.workissuehistory.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "work_report_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkReportUpload extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자가 업로드한 원본 엑셀 파일명입니다.
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    // 주간보고 기준 주차입니다. 예: 2026-05-3주, 5월 3주 등 화면에서 입력한 값을 저장합니다.
    @Column(name = "report_week", length = 50)
    private String reportWeek;

    // 업로드한 사람 이름입니다. 로그인 기능이 붙기 전까지는 프론트 입력값을 사용합니다.
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    // 01_프로젝트 시트에서 저장된 건수입니다.
    @Column(name = "project_count", nullable = false)
    private int projectCount;

    // 02_유지보수 시트에서 저장된 건수입니다.
    @Column(name = "maintenance_count", nullable = false)
    private int maintenanceCount;

    @Builder
    public WorkReportUpload(String originalFileName, String reportWeek, String uploadedBy) {
        this.originalFileName = originalFileName;
        this.reportWeek = reportWeek;
        this.uploadedBy = uploadedBy;
        this.projectCount = 0;
        this.maintenanceCount = 0;
    }

    /**
     * 엑셀 파싱이 끝난 뒤 저장 건수를 업로드 이력에 반영합니다.
     * 업로드 목록 화면에서 각 파일이 몇 건을 만들었는지 바로 확인하기 위한 값입니다.
     */
    public void updateCounts(int projectCount, int maintenanceCount) {
        this.projectCount = projectCount;
        this.maintenanceCount = maintenanceCount;
    }
}
