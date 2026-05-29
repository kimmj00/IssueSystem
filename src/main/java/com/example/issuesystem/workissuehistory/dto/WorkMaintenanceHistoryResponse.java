package com.example.issuesystem.workissuehistory.dto;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Builder
public class WorkMaintenanceHistoryResponse {
    private Long id;
    private Long uploadId;
    private int rowNo;
    private String no;
    private String maintenanceName;
    private String version;
    private String pgVersion;
    private String webVersion;
    private String statusDate;
    private String isUploaded;
    private String smsStatus;
    private String nmsStatus;
    private String oz;
    private String dashboard;
    private String siem;
    private String apm;
    private String salesGrade;
    private String contractType;
    private String visitType;
    private String cycle;
    private String method;
    private String contractStart;
    private String contractEnd;
    private Double visits;
    private Double md;
    private String region;
    private Map<String, String> inspectionDates;
    private String progressIssues;
    private String salesRep;
    private String mainDev;
    private String subDev;
    private String remarks;
    private String siteCode;
    private LocalDateTime createdAt;

    /** 유지보수 현황 엔티티를 화면 응답 DTO로 변환합니다. */
    public static WorkMaintenanceHistoryResponse from(WorkMaintenanceHistory item) {
        return WorkMaintenanceHistoryResponse.builder()
                .id(item.getId())
                .uploadId(item.getUpload().getId())
                .rowNo(item.getRowNo())
                .no(item.getNo())
                .maintenanceName(item.getMaintenanceName())
                .version(item.getVersion())
                .pgVersion(item.getPgVersion())
                .webVersion(item.getWebVersion())
                .statusDate(item.getStatusDate())
                .isUploaded(item.getIsUploaded())
                .smsStatus(item.getSmsStatus())
                .nmsStatus(item.getNmsStatus())
                .oz(item.getOz())
                .dashboard(item.getDashboard())
                .siem(item.getSiem())
                .apm(item.getApm())
                .salesGrade(item.getSalesGrade())
                .contractType(item.getContractType())
                .visitType(item.getVisitType())
                .cycle(item.getCycle())
                .method(item.getMethod())
                .contractStart(item.getContractStart())
                .contractEnd(item.getContractEnd())
                .visits(item.getVisits())
                .md(item.getMd())
                .region(item.getRegion())
                .inspectionDates(parseInspectionDates(item.getInspectionDates()))
                .progressIssues(item.getProgressIssues())
                .salesRep(item.getSalesRep())
                .mainDev(item.getMainDev())
                .subDev(item.getSubDev())
                .remarks(item.getRemarks())
                .siteCode(item.getSiteCode())
                .createdAt(item.getCreatedAt())
                .build();
    }

    /**
     * DB에는 단순 텍스트로 저장하고, 응답에서는 화면에서 쓰기 쉬운 Map 형태로 변환합니다.
     * 저장 형식: 10월=2025-10-01\n11월=2025-11-01
     */
    private static Map<String, String> parseInspectionDates(String value) {
        Map<String, String> result = new LinkedHashMap<>();

        if (value == null || value.isBlank()) {
            return result;
        }

        String[] lines = value.split("\\n");
        for (String line : lines) {
            int separatorIndex = line.indexOf('=');

            if (separatorIndex <= 0) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String date = line.substring(separatorIndex + 1).trim();

            if (!key.isEmpty() && !date.isEmpty()) {
                result.put(key, date);
            }
        }

        return result;
    }
}
