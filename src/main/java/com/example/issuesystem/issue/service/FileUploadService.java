package com.example.issuesystem.issue.service;

import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.issue.domain.IssueCase;
import com.example.issuesystem.issue.domain.IssueStatus;
import com.example.issuesystem.issue.repository.IssueCaseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final IssueCaseRepository issueCaseRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 엑셀 업로드 처리
     * - 엑셀을 읽어서 IssueCase 목록으로 변환
     * - 한 행마다 save() 하지 않고, 일정 개수씩 모아서 saveAll() 처리
     */
    @Transactional
    public int processExcelFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();

        if (containsIgnoreCase(fileName, "\uB9C8\uC774\uB108\uD328\uCE58")) {
            return processMinorPatchExcelFile(file);
        }

        return processPatchListExcelFile(file);
    }

    private int processPatchListExcelFile(MultipartFile file) {
        int savedCount = 0;

        // 한 번에 저장할 데이터 개수
        // Render + Supabase 조합에서는 50~100 정도가 무난함
        final int BATCH_SIZE = 100;

        // DB 저장 전 임시로 데이터를 모아두는 리스트
        List<IssueCase> batch = new ArrayList<>();

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

                    // 번호와 패치내역이 모두 없으면 실제 데이터 행이 아니라고 판단
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

                            // 고객사: 패치리스트에서는 개발적용 값을 임시 저장
                            .customerName(isBlank(developmentApply) ? null : developmentApply)

                            // DB 버전
                            .versionInfo(dbVersion)

                            // 배포 버전
                            .deploymentVersion(deploymentVersion)

                            // 엑셀 업로드 데이터는 처리 완료 이력으로 간주
                            .status(IssueStatus.RESOLVED)

                            // 증상 요약: 패치내역 첫 줄
                            .symptomSummary(makeSummary(patchContent))

                            // 증상 상세: 전체 패치내역
                            .symptomDetail(defaultText(patchContent, "패치내역 없음"))

                            // 원인: 엑셀에 별도 원인 컬럼이 없으므로 null
                            .causeDetail(null)

                            // 조치 내용: 비고 컬럼 저장
                            .actionDetail(note)

                            // 검색용 태그
                            .tags(makeTags(originalInfra, category, issueType, sheet.getSheetName()))

                            // 작성자: 엑셀 업로드 자동 등록
                            .authorName("excel-upload")

                            // 구분
                            .category(category)
                            .build();

                    // 바로 저장하지 않고 batch 리스트에 모음
                    batch.add(issueCase);

                    // batch가 일정 개수 이상이면 한 번에 저장
                    if (batch.size() >= BATCH_SIZE) {
                        issueCaseRepository.saveAll(batch);
                        savedCount += batch.size();

                        log.info("엑셀 업로드 중간 저장 완료: 누적 {}건", savedCount);

                        // 저장 완료한 데이터는 메모리에서 제거
                        batch.clear();
                    }
                }
            }

            // 마지막에 남아 있는 데이터 저장
            if (!batch.isEmpty()) {
                issueCaseRepository.saveAll(batch);
                savedCount += batch.size();

                log.info("엑셀 업로드 마지막 저장 완료: 누적 {}건", savedCount);

                batch.clear();
            }

            log.info("엑셀 파일 처리 완료: 총 {}건 저장", savedCount);

            return savedCount;

        } catch (Throwable e) {
            log.error("엑셀 파싱 실패", e);
            throw new IllegalStateException("엑셀 업로드 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private int processMinorPatchExcelFile(MultipartFile file) {
        int savedCount = 0;
        final int BATCH_SIZE = 100;

        List<IssueCase> batch = new ArrayList<>();
        Map<IssueCase, LocalDateTime> createdAtMap = new HashMap<>();

        log.info("Minor patch excel processing started: fileName={}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("Sheet1");

            if (sheet == null) {
                throw new IllegalArgumentException("Sheet1 sheet was not found.");
            }

            Map<String, Integer> headerMap = readHeaderRow(sheet, 5);
            validateMinorPatchHeaders(headerMap);

            for (int rowNum = 6; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);

                if (row == null || isMinorPatchBlankRow(row, headerMap)) {
                    continue;
                }

                String division = getCellValue(row, headerMap.get("\uAD6C\uBD84"));
                String function = getCellValue(row, headerMap.get("\uAE30\uB2A5"));
                String infra = getCellValue(row, headerMap.get("\uC778\uD504\uB77C"));
                String content = getCellValue(row, headerMap.get("\uB0B4\uC6A9"));
                String scriptExecuted = getCellValue(row, getFirstHeaderIndex(headerMap,
                        "\u0053\u0063\u0072\u0070\u0069\u0074 \uC2E4\uD589\uC5EC\uBD80",
                        "\u0053\u0063\u0072\u0069\u0070\u0074 \uC2E4\uD589\uC5EC\uBD80"));
                String note = getCellValue(row, headerMap.get("\uBE44\uACE0"));
                String commonApplyVersion = getCellValue(row, headerMap.get("\uACF5\uD1B5\uC801\uC6A9\uBC84\uC804"));
                String author = getCellValue(row, headerMap.get("\uB2F4\uB2F9\uC790"));
                LocalDateTime completedAt = getCellDateTime(row, headerMap.get("\uC644\uB8CC\uC77C"));

                IssueCase issueCase = IssueCase.builder()
                        .title(makeMinorPatchTitle(content, sheet.getSheetName(), rowNum))
                        .infraType(resolveInfraType(infra))
                        .systemName(isBlank(infra) ? "\uBBF8\uC9C0\uC815" : infra)
                        .customerName(null)
                        .versionInfo(null)
                        .deploymentVersion(commonApplyVersion)
                        .status(IssueStatus.RESOLVED)
                        .symptomSummary(makeSummary(content))
                        .symptomDetail(makeMinorPatchDetail(content, scriptExecuted, note))
                        .causeDetail(null)
                        .actionDetail(null)
                        .tags(makeTags(infra, division, function, sheet.getSheetName()))
                        .authorName(isBlank(author) ? "excel-upload" : author)
                        .category(makeMinorPatchCategory(function, division))
                        .build();

                batch.add(issueCase);

                if (completedAt != null) {
                    createdAtMap.put(issueCase, completedAt);
                }

                if (batch.size() >= BATCH_SIZE) {
                    List<IssueCase> savedIssues = issueCaseRepository.saveAll(batch);
                    issueCaseRepository.flush();
                    updateCreatedAt(savedIssues, createdAtMap);
                    savedCount += savedIssues.size();
                    batch.clear();
                    createdAtMap.clear();
                }
            }

            if (!batch.isEmpty()) {
                List<IssueCase> savedIssues = issueCaseRepository.saveAll(batch);
                issueCaseRepository.flush();
                updateCreatedAt(savedIssues, createdAtMap);
                savedCount += savedIssues.size();
                batch.clear();
                createdAtMap.clear();
            }

            log.info("Minor patch excel processing completed: savedCount={}", savedCount);

            return savedCount;
        } catch (Throwable e) {
            log.error("Minor patch excel parsing failed", e);
            throw new IllegalStateException("Minor patch excel upload failed: " + e.getMessage(), e);
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

            if (temp.containsKey("번호")
                    && temp.containsKey("INFRA")
                    && temp.containsKey("패치내역")) {
                temp.put("_HEADER_ROW", row.getRowNum());
                return temp;
            }
        }

        return headerMap;
    }

    private Map<String, Integer> readHeaderRow(Sheet sheet, int headerRowIndex) {
        Row headerRow = sheet.getRow(headerRowIndex);

        if (headerRow == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> headerMap = new HashMap<>();

        for (Cell cell : headerRow) {
            String value = getCellValue(cell);

            if (!isBlank(value)) {
                headerMap.put(value.trim(), cell.getColumnIndex());
            }
        }

        return headerMap;
    }

    private void validateMinorPatchHeaders(Map<String, Integer> headerMap) {
        List<String> requiredHeaders = List.of(
                "구분",
                "기능",
                "인프라",
                "내용",
                "비고",
                "공통적용버전",
                "담당자",
                "완료일"
        );

        for (String requiredHeader : requiredHeaders) {
            if (!headerMap.containsKey(requiredHeader)) {
                throw new IllegalArgumentException("Required header was not found: " + requiredHeader);
            }
        }

        if (getFirstHeaderIndex(headerMap, "Scrpit 실행여부", "Script 실행여부") == null) {
            throw new IllegalArgumentException("Required header was not found: Scrpit/Script 실행여부");
        }
    }

    private boolean isMinorPatchBlankRow(Row row, Map<String, Integer> headerMap) {
        return isBlank(getCellValue(row, headerMap.get("구분")))
                && isBlank(getCellValue(row, headerMap.get("기능")))
                && isBlank(getCellValue(row, headerMap.get("인프라")))
                && isBlank(getCellValue(row, headerMap.get("내용")))
                && isBlank(getCellValue(row, headerMap.get("비고")))
                && isBlank(getCellValue(row, headerMap.get("공통적용버전")))
                && isBlank(getCellValue(row, headerMap.get("담당자")))
                && isBlank(getCellValue(row, headerMap.get("완료일")));
    }

    private Integer getFirstHeaderIndex(Map<String, Integer> headerMap, String... headerNames) {
        for (String headerName : headerNames) {
            Integer index = headerMap.get(headerName);

            if (index != null) {
                return index;
            }
        }

        return null;
    }

    private String makeMinorPatchCategory(String function, String division) {
        if (isBlank(function)) {
            return division;
        }

        if (isBlank(division)) {
            return function;
        }

        return limit(function.trim() + "(" + division.trim() + ")", 50);
    }

    private String makeMinorPatchDetail(String content, String scriptExecuted, String note) {
        List<String> parts = new ArrayList<>();

        if (!isBlank(content)) {
            parts.add(content.trim());
        }

        if (!isBlank(scriptExecuted)) {
            parts.add(scriptExecuted.trim());
        }

        if (!isBlank(note)) {
            parts.add(note.trim());
        }

        return parts.isEmpty() ? "내용 없음" : String.join("\n\n", parts);
    }

    private String makeMinorPatchTitle(String content, String sheetName, int rowNum) {
        String firstSentence = firstSentence(content);

        if (isBlank(firstSentence)) {
            firstSentence = sheetName + " minor patch row " + (rowNum + 1);
        }

        return limit(firstSentence, 200);
    }

    private String firstSentence(String value) {
        if (isBlank(value)) {
            return "";
        }

        String normalized = value.trim();
        int sentenceEnd = findFirstSentenceEnd(normalized);

        if (sentenceEnd >= 0) {
            return normalized.substring(0, sentenceEnd + 1).trim();
        }

        return makeSummary(normalized);
    }

    private int findFirstSentenceEnd(String value) {
        int result = -1;

        for (String delimiter : List.of(".", "?", "!", "。", "？", "！", "다.", "요.")) {
            int index = value.indexOf(delimiter);

            if (index >= 0) {
                int endIndex = index + delimiter.length() - 1;
                result = result < 0 ? endIndex : Math.min(result, endIndex);
            }
        }

        return result;
    }

    private LocalDateTime getCellDateTime(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null) {
            return null;
        }

        Cell cell = row.getCell(columnIndex);

        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        return parseDateTime(getCellValue(cell));
    }

    private LocalDateTime parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }

        String normalized = value.trim().replace(".", "-").replace("/", "-");

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-M-d HH:mm")
        )) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy-M-d")
        )) {
            try {
                return LocalDate.parse(normalized, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        throw new IllegalArgumentException("Unsupported date format: " + value);
    }

    private void updateCreatedAt(List<IssueCase> savedIssues, Map<IssueCase, LocalDateTime> createdAtMap) {
        for (IssueCase issueCase : savedIssues) {
            LocalDateTime createdAt = createdAtMap.get(issueCase);

            if (createdAt == null) {
                continue;
            }

            entityManager.createNativeQuery("""
                    update issue_case
                    set created_at = :createdAt
                    where id = :id
                    """)
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", issueCase.getId())
                    .executeUpdate();
        }
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
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

    /**
     * 태그 문자열 누적
     */
    private void appendTag(StringBuilder sb, String value) {
        if (isBlank(value)) {
            return;
        }

        if (!sb.isEmpty()) {
            sb.append(", ");
        }

        sb.append(value.trim());
    }

    /**
     * 값이 비어 있으면 기본값 반환
     */
    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * 문자열 최대 길이 제한
     */
    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * null 또는 공백 문자열 체크
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
