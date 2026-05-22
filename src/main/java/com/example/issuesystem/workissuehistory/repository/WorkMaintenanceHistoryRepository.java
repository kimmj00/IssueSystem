package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkMaintenanceHistoryRepository extends JpaRepository<WorkMaintenanceHistory, Long> {

    // 특정 업로드 파일에 포함된 유지보수 현황만 조회합니다.
    List<WorkMaintenanceHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);
}
