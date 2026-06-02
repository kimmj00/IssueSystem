package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkMaintenanceHistoryRepository extends JpaRepository<WorkMaintenanceHistory, Long> {

    // 특정 업로드 파일에 포함된 유지보수 현황만 조회합니다.
    List<WorkMaintenanceHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);

    @Query("""
            select m
            from WorkMaintenanceHistory m
            where m.createdAt >= :startDate
              and m.createdAt < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(m.maintenanceName, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or :keyword = ''
                  or lower(concat(' ',
                      coalesce(m.maintenanceName, ''),
                      coalesce(m.progressIssues, ''),
                      coalesce(m.remarks, ''),
                      coalesce(m.mainDev, ''),
                      coalesce(m.subDev, ''),
                      coalesce(m.salesRep, ''),
                      coalesce(m.siteCode, ''),
                      coalesce(m.region, '')
                  )) like lower(concat('%', :keyword, '%'))
              )
            order by m.createdAt asc, m.id asc
            """)
    Page<WorkMaintenanceHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
            select m
            from WorkMaintenanceHistory m
            where m.createdAt >= :startDate
              and m.createdAt < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(m.maintenanceName, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or :keyword = ''
                  or lower(concat(' ',
                      coalesce(m.maintenanceName, ''),
                      coalesce(m.progressIssues, ''),
                      coalesce(m.remarks, ''),
                      coalesce(m.mainDev, ''),
                      coalesce(m.subDev, ''),
                      coalesce(m.salesRep, ''),
                      coalesce(m.siteCode, ''),
                      coalesce(m.region, '')
                  )) like lower(concat('%', :keyword, '%'))
              )
            """)
    List<WorkMaintenanceHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
