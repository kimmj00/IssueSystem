package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkProjectHistoryRepository extends JpaRepository<WorkProjectHistory, Long> {

    // 특정 업로드 파일에 포함된 프로젝트 현황만 조회합니다.
    List<WorkProjectHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);
}
