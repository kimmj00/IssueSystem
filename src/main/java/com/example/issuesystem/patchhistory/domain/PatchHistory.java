package com.example.issuesystem.patchhistory.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import com.example.issuesystem.common.domain.InfraType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
// 기존 운영 DB와 데이터 호환을 위해 물리 테이블명은 issue_case로 유지합니다.
@Table(name = "issue_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InfraType infraType;

    @Column(nullable = false, length = 100)
    private String systemName;

    @Column(length = 100)
    private String customerName;

    @Column(length = 50)
    private String versionInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PatchStatus status;

    @Column(nullable = false, length = 300)
    private String symptomSummary;

    @Column(columnDefinition = "text", nullable = false)
    private String symptomDetail;

    @Column(columnDefinition = "text")
    private String causeDetail;

    @Column(columnDefinition = "text")
    private String actionDetail;

    @Column(length = 200)
    private String tags;

    @Column(nullable = false, length = 100)
    private String authorName;

    // 새로운 컬럼 추가
    @Column(length = 50)
    private String category;  // 구분

    @Column(length = 50)
    private String deploymentVersion;  // 배포버전

    @Builder
    public PatchHistory(
            String title,
            InfraType infraType,
            String systemName,
            String customerName,
            String versionInfo,
            PatchStatus status,
            String symptomSummary,
            String symptomDetail,
            String causeDetail,
            String actionDetail,
            String tags,
            String authorName,
            String category,
            String deploymentVersion
    ) {
        this.title = title;
        this.infraType = infraType;
        this.systemName = systemName;
        this.customerName = customerName;
        this.versionInfo = versionInfo;
        this.status = status;
        this.symptomSummary = symptomSummary;
        this.symptomDetail = symptomDetail;
        this.causeDetail = causeDetail;
        this.actionDetail = actionDetail;
        this.tags = tags;
        this.authorName = authorName;
        this.category = category;
        this.deploymentVersion = deploymentVersion;
    }

    public void update(
            String title,
            InfraType infraType,
            String systemName,
            String customerName,
            String versionInfo,
            PatchStatus status,
            String symptomSummary,
            String symptomDetail,
            String causeDetail,
            String actionDetail,
            String tags,
            String category,
            String deploymentVersion
    ) {
        this.title = title;
        this.infraType = infraType;
        this.systemName = systemName;
        this.customerName = customerName;
        this.versionInfo = versionInfo;
        this.status = status;
        this.symptomSummary = symptomSummary;
        this.symptomDetail = symptomDetail;
        this.causeDetail = causeDetail;
        this.actionDetail = actionDetail;
        this.tags = tags;
        this.category = category;
        this.deploymentVersion = deploymentVersion;
    }
}