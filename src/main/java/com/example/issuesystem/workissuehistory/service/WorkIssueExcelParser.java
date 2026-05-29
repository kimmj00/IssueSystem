package com.example.issuesystem.workissuehistory.service;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import com.example.issuesystem.workissuehistory.domain.WorkReportUpload;
import lombok.Getter;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class WorkIssueExcelParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 엑셀 표시 형식을 최대한 유지하기 위한 POI formatter입니다.
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    /**
     * 주간보고 엑셀 파일에서 01_프로젝트, 02_유지보수 시트만 파싱합니다.
     *
     * 주간보고 파일은 큰 셀 텍스트와 스타일 정보가 많아 POI의 ZipSecureFile 보호 로직에 걸릴 수 있습니다.
     * 내부 업무용 엑셀 업로드라는 전제에서 압축비 제한을 완화하고, MultipartFile stream 대신 byte[]를 사용해
     * 임시파일/스트림 상태 문제를 줄였습니다.
     */
    public ParsedWorkIssueExcel parse(MultipartFile file, WorkReportUpload upload) {
        configurePoiZipSecurity();

        try (InputStream inputStream = new ByteArrayInputStream(file.getBytes());
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<WorkProjectHistory> projects = parseProjectSheet(findSheet(workbook, "01_프로젝트"), upload);
            List<WorkMaintenanceHistory> maintenanceItems = parseMaintenanceSheet(findSheet(workbook, "02_유지보수"), upload);

            return new ParsedWorkIssueExcel(projects, maintenanceItems);
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다. 원인: " + rootMessage(e), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("주간보고 엑셀 파싱 중 오류가 발생했습니다. 파일 형식과 시트명(01_프로젝트, 02_유지보수)을 확인하세요. 원인: " + rootMessage(e), e);
        }
    }

    /**
     * Apache POI의 zip-bomb 보호값을 내부 주간보고 파일에 맞게 완화합니다.
     * 외부 불특정 사용자의 대용량 파일을 받는 서비스라면 이 값은 더 보수적으로 조정해야 합니다.
     */
    private void configurePoiZipSecurity() {
        ZipSecureFile.setMinInflateRatio(0.00001d);
        ZipSecureFile.setMaxEntrySize(100L * 1024L * 1024L);
        ZipSecureFile.setMaxTextSize(50L * 1024L * 1024L);
    }

    /** 시트명 앞뒤 공백이나 대소문자 차이가 있어도 찾을 수 있게 보정합니다. */
    private Sheet findSheet(Workbook workbook, String expectedName) {
        Sheet exact = workbook.getSheet(expectedName);

        if (exact != null) {
            return exact;
        }

        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            String sheetName = sheet.getSheetName();

            if (sheetName != null && sheetName.trim().equalsIgnoreCase(expectedName)) {
                return sheet;
            }
        }

        return null;
    }

    /** 01_프로젝트 시트를 프로젝트 현황 테이블 데이터로 변환합니다. */
    private List<WorkProjectHistory> parseProjectSheet(Sheet sheet, WorkReportUpload upload) {
        List<WorkProjectHistory> projects = new ArrayList<>();

        if (sheet == null) {
            return projects;
        }

        // 원본 React 소스와 동일하게 0-based 6번 행, 즉 엑셀 7행부터 데이터로 간주합니다.
        for (int rowIndex = 6; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String clientName = cell(row, 2);
            String projectScale = cell(row, 9);

            // 기존 주간보고 소스의 조건: 고객사와 PJT 규모가 있는 행만 프로젝트 데이터로 저장합니다.
            if (isBlank(clientName) || isBlank(projectScale)) {
                continue;
            }

            projects.add(WorkProjectHistory.builder()
                    .upload(upload)
                    .rowNo(rowIndex + 1)
                    .no(cell(row, 0))
                    .salesRep(cell(row, 1))
                    .clientName(clientName)
                    .scope(cell(row, 3))
                    .oz(defaultIfBlank(cell(row, 4), "X"))
                    .dashboard(defaultIfBlank(cell(row, 5), "X"))
                    .apm(defaultIfBlank(cell(row, 6), "X"))
                    .location(cell(row, 7))
                    .startDate(cell(row, 8))
                    .projectScale(projectScale)
                    .executors(cell(row, 10))
                    .visits(parseDouble(cell(row, 11)))
                    .md(parseDouble(cell(row, 12)))
                    .progressLogs(cell(row, 13))
                    .remainingIssues(cell(row, 14))
                    .siteCode(cell(row, 15))
                    .build());
        }

        return projects;
    }

    /** 02_유지보수 시트를 유지보수 현황 테이블 데이터로 변환합니다. */
    private List<WorkMaintenanceHistory> parseMaintenanceSheet(Sheet sheet, WorkReportUpload upload) {
        List<WorkMaintenanceHistory> maintenanceItems = new ArrayList<>();

        if (sheet == null) {
            return maintenanceItems;
        }

        // 원본 React 소스와 동일하게 0-based 13번 행, 즉 엑셀 14행부터 데이터로 간주합니다.
        for (int rowIndex = 13; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String maintenanceName = cell(row, 1);

            if (isBlank(maintenanceName)) {
                continue;
            }

            maintenanceItems.add(WorkMaintenanceHistory.builder()
                    .upload(upload)
                    .rowNo(rowIndex + 1)
                    .no(cell(row, 0))
                    .maintenanceName(maintenanceName)
                    .version(cell(row, 2))
                    .pgVersion(cell(row, 3))
                    .webVersion(cell(row, 4))
                    .statusDate(cell(row, 5))
                    .isUploaded(cell(row, 6))
                    .smsStatus(cell(row, 7))
                    .nmsStatus(cell(row, 8))
                    .oz(defaultIfBlank(cell(row, 9), "X"))
                    .dashboard(defaultIfBlank(cell(row, 10), "X"))
                    .siem(defaultIfBlank(cell(row, 11), "X"))
                    .apm(defaultIfBlank(cell(row, 12), "X"))
                    .salesGrade(cell(row, 13))
                    .contractType(cell(row, 14))
                    .visitType(cell(row, 15))
                    .cycle(cell(row, 16))
                    .method(cell(row, 17))
                    .contractStart(cell(row, 18))
                    .contractEnd(cell(row, 19))
                    .visits(parseDouble(cell(row, 20)))
                    .md(parseDouble(cell(row, 21)))
                    .region(cell(row, 22))
                    .inspectionDates(parseInspectionDates(row))
                    .progressIssues(cell(row, 38))
                    .salesRep(cell(row, 39))
                    .mainDev(cell(row, 40))
                    .subDev(cell(row, 41))
                    .remarks(cell(row, 42))
                    .siteCode(cell(row, 43))
                    .build());
        }

        return maintenanceItems;
    }

    /**
     * 정기점검 월별 수행일자를 text 컬럼에 넣기 위한 key=value 문자열로 변환합니다.
     * 화면 응답에서는 다시 Map으로 변환됩니다.
     */
    private String parseInspectionDates(Row row) {
        String[] months = {"10월", "11월", "12월", "1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월"};
        List<String> values = new ArrayList<>();

        for (int index = 0; index < months.length; index++) {
            String value = cell(row, 23 + index);

            if (!isBlank(value) && !"null".equalsIgnoreCase(value)) {
                values.add(months[index] + "=" + value);
            }
        }

        return String.join("\n", values);
    }

    /** 엑셀 cell 값을 문자열로 안전하게 읽습니다. 날짜 cell은 yyyy-MM-dd로 통일합니다. */
    private String cell(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);

        if (cell == null) {
            return "";
        }

        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .format(DATE_FORMATTER);
            }
        } catch (Exception ignored) {
            // 날짜 판별에 실패한 셀은 아래 DataFormatter로 다시 읽습니다.
        }

        return dataFormatter.formatCellValue(cell).trim();
    }

    /** 숫자 문자열을 Double로 변환합니다. 0,3 같은 표기도 0.3으로 처리합니다. */
    private Double parseDouble(String value) {
        if (isBlank(value)) {
            return 0.0;
        }

        try {
            String normalized = value.trim().replace(" ", "");

            if (normalized.contains(",") && !normalized.contains(".")) {
                normalized = normalized.replace(",", ".");
            } else {
                normalized = normalized.replace(",", "");
            }

            normalized = normalized.replaceAll("[^0-9.\\-]", "");

            if (isBlank(normalized)) {
                return 0.0;
            }

            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /** 중첩 예외의 가장 안쪽 메시지를 응답에 포함하기 위한 dev용 유틸입니다. */
    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Getter
    public static class ParsedWorkIssueExcel {
        private final List<WorkProjectHistory> projects;
        private final List<WorkMaintenanceHistory> maintenanceItems;

        public ParsedWorkIssueExcel(List<WorkProjectHistory> projects, List<WorkMaintenanceHistory> maintenanceItems) {
            this.projects = projects;
            this.maintenanceItems = maintenanceItems;
        }
    }
}
