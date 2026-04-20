package com.example.issuesystem.issue.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "issue_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_case_id", nullable = false)
    private IssueCase issueCase;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 500)
    private String storedPath;

    @Column(nullable = false)
    private Long fileSize;

    @Builder
    public IssueAttachment(IssueCase issueCase, String originalFileName, String storedFileName, String storedPath, Long fileSize) {
        this.issueCase = issueCase;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
    }
}