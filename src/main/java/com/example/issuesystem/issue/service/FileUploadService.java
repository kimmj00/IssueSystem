package com.example.issuesystem.issue.service;

import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.IssueStatus;
import com.example.issuesystem.issue.repository.IssueCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final IssueCaseRepository issueCaseRepository;

    /**
     * 엑셀 업로드 처리
     * - xlsx, xls 모두 WorkbookFactory로 처리
     * - 모든 시트를 순회
     * - "번호", "INFRA", "패치내역"이 있는 행을 헤더로 판단
     * - 헤더명 기준으로 컬럼을 찾아 저장
     */
    @Transactional
    public int processExcelFile(MultipartFile file) {
        int savedCount = 0;

        log.info("엑셀 파일 처리 시작");

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            log.info("Workbook 생성 성공, 시트 개수={}", workbook.getNumberOfSheets());

            for (Sheet sheet : workbook) {
                log.info("시트 처리 시작: {}", sheet.getSheetName());
                Map<String, Integer> headerMap = findHeaderRow(sheet);
                log.info("헤더맵: {}", headerMap);

                if (headerMap.isEmpty()) {
                    log.warn("헤더를 찾지 못한 시트: {}", sheet.getSheetName());
                    continue;
                }

                int headerRowNum = headerMap.get("_HEADER_ROW");
                log.info("헤더 행 번호: {}", headerRowNum);

                for (int rowNum = headerRowNum + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);

                    if (row == null) {
                        continue;
                    }

                    String no = getCellValue(row, headerMap.get("번호"));
                    String patchContent = getCellValue(row, headerMap.get("패치내역"));

                    // 번호와 패치내역이 없으면 데이터 행이 아니라고 판단
                    if (isBlank(no) && isBlank(patchContent)) {
                        continue;
                    }

                    String originalInfra = getCellValue(row, headerMap.get("INFRA"));
                    String category = getCellValue(row, headerMap.get("구분"));
                    String issueType = getCellValue(row, headerMap.get("유형"));
                    String dbVersion = getCellValue(row, headerMap.get("DB Version"));
                    String deploymentVersion = getCellValue(row, headerMap.get("배포 버전"));
                    String developmentApply = getCellValue(row, headerMap.get("개발적용"));
                    String note = getCellValue(row, headerMap.get("비고"));

                    IssueCase issueCase = IssueCase.builder()
                            // 제목: 패치내역 첫 줄을 제목으로 사용
                            .title(makeTitle(patchContent, sheet.getSheetName(), rowNum))

                            // 인프라: enum에 없으면 EMS로 기본 처리
                            .infraType(resolveInfraType(originalInfra))

                            // 시스템명: 엑셀 INFRA 원본값 저장
                            .systemName(isBlank(originalInfra) ? "미지정" : originalInfra)

                            // 고객사: 패치리스트는 고객사 개념이 없으므로 공백
                            .customerName(isBlank(developmentApply) ? null : developmentApply)

                            // DB버전은 기존 versionInfo에 저장
                            .versionInfo(dbVersion)
                            .deploymentVersion(deploymentVersion)

                            // 패치 이력은 처리 완료 이력으로 간주
                            .status(IssueStatus.RESOLVED)

                            // 증상 요약: 패치내역 첫 줄
                            .symptomSummary(makeSummary(patchContent))

                            // 증상 상세: 전체 패치내역
                            .symptomDetail(defaultText(patchContent, "패치내역 없음"))

                            // 원인: 엑셀에 별도 원인 컬럼이 없으므로 비워둠
                            .causeDetail(null)

                            // 조치 내용: 비고/스크립트 등 참고 내용
                            .actionDetail(note)

                            // 태그: 원본 인프라, 유형, 시트명 등을 검색용으로 저장
                            .tags(makeTags(originalInfra, category, issueType, sheet.getSheetName()))

                            // 작성자: 엑셀 업로드 자동 등록
                            .authorName("excel-upload")

                            // 추가 컬럼
                            .category(category)
                            .build();

                    issueCaseRepository.save(issueCase);
                    savedCount++;
                    log.info("저장 대상 row={}, infra={}, category={}, patchContent={}",
                            rowNum, originalInfra, category, makeSummary(patchContent));
                }
            }


            return savedCount;
        } catch (Throwable  e) {
            log.error("엑셀 파싱 실패", e);
            throw new IllegalStateException("엑셀 업로드 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 헤더 행 찾기
     * - "번호", "INFRA", "패치내역"이 포함된 행을 헤더로 판단
     */
    private Map<String, Integer> findHeaderRow(Sheet sheet) {
        Map<String, Integer> headerMap = new HashMap<>();

        for (Row row : sheet) {
            Map<String, Integer> temp = new HashMap<>();

            for (Cell cell : row) {
                String value = getCellValue(cell);

                if (!isBlank(value)) {
                    temp.put(value.trim(), cell.getColumnIndex());
                }
            }

            if (temp.containsKey("번호") && temp.containsKey("INFRA") && temp.containsKey("패치내역")) {
                temp.put("_HEADER_ROW", row.getRowNum());
                return temp;
            }
        }

        return headerMap;
    }

    /**
     * row + columnIndex 기준 셀 값 추출
     */
    private String getCellValue(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null) {
            return "";
        }

        return getCellValue(row.getCell(columnIndex));
    }

    /**
     * 셀 타입과 상관없이 문자열로 변환
     * - 문자열, 숫자, 날짜, 수식, 빈 셀 모두 처리
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * 엑셀 INFRA 값을 InfraType enum으로 변환
     * - enum에 없는 값은 EMS로 기본 처리
     * - 예: "공통"은 현재 InfraType enum에 없으므로 EMS 처리
     */
    private InfraType resolveInfraType(String value) {
        if (isBlank(value)) {
            return InfraType.EMS;
        }

        try {
            return InfraType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return InfraType.EMS;
        }
    }

    /**
     * 제목 생성
     */
    private String makeTitle(String patchContent, String sheetName, int rowNum) {
        String summary = makeSummary(patchContent);

        if (isBlank(summary)) {
            summary = sheetName + " 패치 이력 " + rowNum;
        }

        return limit(summary, 200);
    }

    /**
     * 패치내역 첫 줄을 요약으로 사용
     */
    private String makeSummary(String text) {
        if (isBlank(text)) {
            return "";
        }

        String firstLine = text.split("\\R")[0].trim();
        return limit(firstLine, 300);
    }

    /**
     * 태그 생성
     */
    private String makeTags(String infra, String category, String issueType, String sheetName) {
        StringBuilder sb = new StringBuilder();

        appendTag(sb, "excel");
        appendTag(sb, sheetName);
        appendTag(sb, infra);
        appendTag(sb, category);
        appendTag(sb, issueType);

        return limit(sb.toString(), 200);
    }

    private void appendTag(StringBuilder sb, String value) {
        if (isBlank(value)) {
            return;
        }

        if (!sb.isEmpty()) {
            sb.append(", ");
        }

        sb.append(value.trim());
    }

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}