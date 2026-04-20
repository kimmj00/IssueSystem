package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {
    List<IssueAttachment> findByIssueCaseId(Long issueCaseId);
}