package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkReportUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkReportUploadRepository extends JpaRepository<WorkReportUpload, Long> {

    // 화면 최초 진입 시 가장 최근 업로드 건을 기본 조회 대상으로 사용합니다.
    Optional<WorkReportUpload> findFirstByOrderByIdDesc();

    // 업로드 목록 select 박스 표시용입니다.
    List<WorkReportUpload> findTop20ByOrderByIdDesc();
}
