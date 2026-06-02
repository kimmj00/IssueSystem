package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkProjectHistoryRepository extends JpaRepository<WorkProjectHistory, Long> {

    // 특정 업로드 파일에 포함된 프로젝트 현황만 조회합니다.
    List<WorkProjectHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);

    @Query("""
            select p
            from WorkProjectHistory p
            where p.createdAt >= :startDate
              and p.createdAt < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(p.clientName, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or :keyword = ''
                  or lower(concat(' ',
                      coalesce(p.clientName, ''),
                      coalesce(p.scope, ''),
                      coalesce(p.executors, ''),
                      coalesce(p.progressLogs, ''),
                      coalesce(p.remainingIssues, ''),
                      coalesce(p.salesRep, ''),
                      coalesce(p.siteCode, ''),
                      coalesce(p.projectScale, '')
                  )) like lower(concat('%', :keyword, '%'))
              )
            order by p.createdAt asc, p.id asc
            """)
    Page<WorkProjectHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
            select p
            from WorkProjectHistory p
            where p.createdAt >= :startDate
              and p.createdAt < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(p.clientName, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or :keyword = ''
                  or lower(concat(' ',
                      coalesce(p.clientName, ''),
                      coalesce(p.scope, ''),
                      coalesce(p.executors, ''),
                      coalesce(p.progressLogs, ''),
                      coalesce(p.remainingIssues, ''),
                      coalesce(p.salesRep, ''),
                      coalesce(p.siteCode, ''),
                      coalesce(p.projectScale, '')
                  )) like lower(concat('%', :keyword, '%'))
              )
            """)
    List<WorkProjectHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
