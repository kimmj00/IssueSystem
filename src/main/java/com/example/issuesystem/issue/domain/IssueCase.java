package com.example.issuesystem.issue.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "issue_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueCase extends BaseTimeEntity {

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
    private IssueStatus status;

    @Column(nullable = false, length = 300)
    private String symptomSummary;

    @Lob
    @Column(nullable = false)
    private String symptomDetail;

    @Lob
    private String causeDetail;

    @Lob
    private String actionDetail;

    @Column(length = 200)
    private String tags;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Builder
    public IssueCase(
            String title,
            InfraType infraType,
            String systemName,
            String customerName,
            String versionInfo,
            IssueStatus status,
            String symptomSummary,
            String symptomDetail,
            String causeDetail,
            String actionDetail,
            String tags,
            String authorName
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
        this.authorName=authorName;
    }

    public void update(
            String title,
            InfraType infraType,
            String systemName,
            String customerName,
            String versionInfo,
            IssueStatus status,
            String symptomSummary,
            String symptomDetail,
            String causeDetail,
            String actionDetail,
            String tags
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
    }
}