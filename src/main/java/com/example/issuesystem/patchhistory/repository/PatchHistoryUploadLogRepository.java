package com.example.issuesystem.patchhistory.repository;

import com.example.issuesystem.patchhistory.domain.PatchHistoryUploadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatchHistoryUploadLogRepository extends JpaRepository<PatchHistoryUploadLog, Long> {

    List<PatchHistoryUploadLog> findTop100ByOrderByCreatedAtDesc();
}
