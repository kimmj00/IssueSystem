package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueCaseRepository extends JpaRepository<IssueCase, Long>, IssueCaseSearch {

    /** ID 목록으로 이슈 엔티티 재조회 */
    List<IssueCase> findByIdIn(List<Long> ids);
}
