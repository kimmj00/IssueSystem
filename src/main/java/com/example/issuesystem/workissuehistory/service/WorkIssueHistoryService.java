package com.example.issuesystem.workissuehistory.service;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import com.example.issuesystem.workissuehistory.domain.WorkReportUpload;
import com.example.issuesystem.workissuehistory.dto.WorkIssueSummaryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkIssueUploadResultResponse;
import com.example.issuesystem.workissuehistory.dto.WorkMaintenanceHistoryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkProjectHistoryResponse;
import com.example.issuesystem.workissuehistory.dto.WorkReportUploadResponse;
import com.example.issuesystem.workissuehistory.repository.WorkIssueBatchRepository;
import com.example.issuesystem.workissuehistory.repository.WorkMaintenanceHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkProjectHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkReportUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkIssueHistoryService {

    private final WorkReportUploadRepository uploadRepository;
    private final WorkProjectHistoryRepository projectRepository;
    private final WorkMaintenanceHistoryRepository maintenanceRepository;
    private final WorkIssueBatchRepository batchRepository;
    private final WorkIssueExcelParser excelParser;

    /**
     * 주간보고 엑셀을 업로드하고, 01_프로젝트/02_유지보수 데이터를 DB에 저장합니다.
     *
     * 변경점:
     * - work_report_upload 1건은 JPA로 저장합니다. 업로드 이력은 엔티티 단위 관리가 유리합니다.
     * - 하위 대량 행은 JdbcTemplate batch insert로 저장합니다. saveAll보다 업로드 체감 속도가 좋습니다.
     */
    @Transactional
    public WorkIssueUploadResultResponse upload(MultipartFile file, String reportWeek, String uploadedBy) {
        validateExcelFile(file);

        WorkReportUpload upload = uploadRepository.saveAndFlush(
                WorkReportUpload.builder()
                        .originalFileName(file.getOriginalFilename())
                        .reportWeek(normalize(reportWeek))
                        .uploadedBy(normalize(uploadedBy))
                        .build()
        );

        WorkIssueExcelParser.ParsedWorkIssueExcel parsed = excelParser.parse(file, upload);

        int savedProjectCount = batchRepository.saveProjects(upload.getId(), parsed.getProjects());
        int savedMaintenanceCount = batchRepository.saveMaintenance(upload.getId(), parsed.getMaintenanceItems());

        upload.updateCounts(savedProjectCount, savedMaintenanceCount);

        return WorkIssueUploadResultResponse.of(
                upload.getId(),
                upload.getOriginalFileName(),
                upload.getReportWeek(),
                savedProjectCount,
                savedMaintenanceCount
        );
    }

    /** 최근 업로드 20건을 내려줍니다. 화면의 업로드 이력 선택 박스에서 사용합니다. */
    @Transactional(readOnly = true)
    public List<WorkReportUploadResponse> getUploads() {
        return uploadRepository.findTop20ByOrderByIdDesc()
                .stream()
                .map(WorkReportUploadResponse::from)
                .toList();
    }

    /** 선택된 업로드 건의 프로젝트 현황을 조회합니다. uploadId가 없으면 최신 업로드 건을 사용합니다. */
    @Transactional(readOnly = true)
    public List<WorkProjectHistoryResponse> getProjects(Long uploadId) {
        Optional<WorkReportUpload> upload = resolveUpload(uploadId);

        if (upload.isEmpty()) {
            return List.of();
        }

        return projectRepository.findByUploadIdOrderByRowNoAscIdAsc(upload.get().getId())
                .stream()
                .map(WorkProjectHistoryResponse::from)
                .toList();
    }

    /** 선택된 업로드 건의 유지보수 현황을 조회합니다. uploadId가 없으면 최신 업로드 건을 사용합니다. */
    @Transactional(readOnly = true)
    public List<WorkMaintenanceHistoryResponse> getMaintenance(Long uploadId) {
        Optional<WorkReportUpload> upload = resolveUpload(uploadId);

        if (upload.isEmpty()) {
            return List.of();
        }

        return maintenanceRepository.findByUploadIdOrderByRowNoAscIdAsc(upload.get().getId())
                .stream()
                .map(WorkMaintenanceHistoryResponse::from)
                .toList();
    }

    /**
     * 화면 상단 요약 카드에 필요한 값을 계산합니다.
     *
     * 기존 방식은 프로젝트/유지보수 목록 전체를 조회한 뒤 Java stream으로 M/D를 합산했습니다.
     * 이 방식은 DB에서 COUNT/SUM만 계산하므로 summary 조회가 가벼워집니다.
     */
    @Transactional(readOnly = true)
    public WorkIssueSummaryResponse getSummary(Long uploadId) {
        Optional<WorkReportUpload> upload = resolveUpload(uploadId);

        if (upload.isEmpty()) {
            return WorkIssueSummaryResponse.of(null, null, null, 0, 0, 0.0, 0.0);
        }

        Long resolvedUploadId = upload.get().getId();
        WorkIssueBatchRepository.SummaryStats stats = batchRepository.getSummaryStats(resolvedUploadId);

        return WorkIssueSummaryResponse.of(
                resolvedUploadId,
                upload.get().getReportWeek(),
                upload.get().getOriginalFileName(),
                stats.projectCount(),
                stats.maintenanceCount(),
                stats.projectMdTotal(),
                stats.maintenanceMdTotal()
        );
    }

    /** uploadId가 있으면 해당 건, 없으면 최신 업로드 건을 찾습니다. */
    private Optional<WorkReportUpload> resolveUpload(Long uploadId) {
        if (uploadId != null) {
            return uploadRepository.findById(uploadId);
        }

        return uploadRepository.findFirstByOrderByIdDesc();
    }

    /** 엑셀 확장자와 빈 파일 여부를 검증합니다. */
    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일을 선택하세요.");
        }

        String fileName = file.getOriginalFilename();
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();

        if (!lowerFileName.endsWith(".xlsx") && !lowerFileName.endsWith(".xls")) {
            throw new IllegalArgumentException("xlsx 또는 xls 파일만 업로드할 수 있습니다.");
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
