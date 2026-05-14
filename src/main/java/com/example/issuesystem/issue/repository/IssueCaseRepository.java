package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface IssueCaseRepository extends JpaRepository<IssueCase, Long> {

    /**
     * 검색 조건에 맞는 이슈 ID만 먼저 페이징 조회한다.
     *
     * 최적화 이유:
     * - native query + ILIKE로 PostgreSQL 텍스트 검색을 안정적으로 처리
     * - 엔티티 전체를 native query로 바로 매핑하지 않고 ID만 조회
     * - 이후 findByIdIn으로 필요한 엔티티만 다시 읽어 매핑 꼬임을 피함
     * - created_at 기간 조건은 null 대신 Service에서 기본 범위를 넣어 PostgreSQL 타입 추론 오류를 방지
     */
    @Query(value = """
        select i.id
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
            and (:category is null or :category = '' or
                coalesce(i.category, '') ilike concat('%', :category, '%'))
            and (:deploymentVersion is null or :deploymentVersion = '' or
                coalesce(i.deployment_version, '') ilike concat('%', :deploymentVersion, '%'))
            and i.created_at >= :startDate
            and i.created_at < :endDate
        order by i.id desc
        """,
            countQuery = """
        select count(*)
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
            and (:category is null or :category = '' or
                coalesce(i.category, '') ilike concat('%', :category, '%'))
            and (:deploymentVersion is null or :deploymentVersion = '' or
                coalesce(i.deployment_version, '') ilike concat('%', :deploymentVersion, '%'))
            and i.created_at >= :startDate
            and i.created_at < :endDate
        """,
            nativeQuery = true)
    Page<Long> searchIds(
            @Param("keyword") String keyword,
            @Param("infraType") String infraType,
            @Param("status") String status,
            @Param("customerName") String customerName,
            @Param("category") String category,
            @Param("deploymentVersion") String deploymentVersion,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /** ID 목록으로 이슈 엔티티 재조회 */
    List<IssueCase> findByIdIn(List<Long> ids);
}
