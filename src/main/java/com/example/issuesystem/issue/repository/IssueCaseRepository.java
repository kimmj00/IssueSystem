package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IssueCaseRepository extends JpaRepository<IssueCase, Long> {

    @Query(value = """
        select *
        from issue_case i
        where
            (:keyword is null or :keyword = '' or
                i.title ilike concat('%', :keyword, '%')
                or i.system_name ilike concat('%', :keyword, '%')
                or coalesce(i.customer_name, '') ilike concat('%', :keyword, '%')
                or i.symptom_summary ilike concat('%', :keyword, '%')
                or i.symptom_detail ilike concat('%', :keyword, '%')
                or coalesce(i.cause_detail, '') ilike concat('%', :keyword, '%')
                or coalesce(i.action_detail, '') ilike concat('%', :keyword, '%')
                or coalesce(i.tags, '') ilike concat('%', :keyword, '%')
            )
            and (:infraType is null or i.infra_type = cast(:infraType as varchar))
            and (:status is null or i.status = cast(:status as varchar))
            and (:customerName is null or :customerName = '' or
                coalesce(i.customer_name, '') ilike concat('%', :customerName, '%'))
        order by i.id desc
        """, nativeQuery = true)
    List<IssueCase> search(
            @Param("keyword") String keyword,
            @Param("infraType") String infraType,
            @Param("status") String status,
            @Param("customerName") String customerName
    );
}