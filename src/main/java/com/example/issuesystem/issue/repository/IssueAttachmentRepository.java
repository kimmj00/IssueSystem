package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAttachmentRepository extends JpaRepository<IssueAttachment, Long> {

    // 단건 상세 조회 시 사용하는 첨부파일 조회
    List<IssueAttachment> findByIssueCaseId(Long issueCaseId);

    // 목록/검색 조회 시 여러 이슈의 첨부파일을 한 번에 가져오기 위한 메서드
    List<IssueAttachment> findByIssueCaseIdIn(List<Long> issueCaseIds);
}