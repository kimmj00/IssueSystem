package com.example.issuesystem.patchhistory.service;

import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.domain.PatchHistory;
import com.example.issuesystem.patchhistory.domain.PatchHistoryUploadLog;
import com.example.issuesystem.patchhistory.domain.PatchStatus;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUploadExcludedItem;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUploadFileResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUploadResult;
import com.example.issuesystem.patchhistory.repository.PatchHistoryRepository;
import com.example.issuesystem.patchhistory.repository.PatchHistoryUploadLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchHistoryFileUploadService {

    private static final int BATCH_SIZE = 100;
    private static final int PATCH_LIST_HEADER_ROW_INDEX = 5;
    private static final int PATCH_LIST_DATA_START_ROW_INDEX = 6;

    private static final String FILE_TYPE_PATCH_LIST = "\uD328\uCE58\uB9AC\uC2A4\uD2B8";
    private static final String FILE_TYPE_MINOR_PATCH = "\uB9C8\uC774\uB108\uD328\uCE58";
    private static final String HEADER_NO = "\uBC88\uD638";
    private static final String HEADER_INFRA = "INFRA";
    private static final String HEADER_CATEGORY = "\uAD6C\uBD84";
    private static final String HEADER_TYPE = "\uC720\uD615";
    private static final String HEADER_DB_VERSION = "DBVersion";
    private static final String HEADER_DEPLOYMENT_VERSION = "\uBC30\uD3EC\uBC84\uC804";
    private static final String HEADER_DEV_APPLY = "\uAC1C\uBC1C\uC801\uC6A9";
    private static final String HEADER_PATCH_CONTENT = "\uD328\uCE58\uB0B4\uC5ED";
    private static final String HEADER_NOTE = "\uBE44\uACE0";
    private static final String HEADER_MINOR_INFRA = "\uC778\uD504\uB77C";
    private static final String HEADER_FUNCTION = "\uAE30\uB2A5";
    private static final String HEADER_CONTENT = "\uB0B4\uC6A9";
    private static final String HEADER_AUTHOR = "\uB2F4\uB2F9\uC790";
    private static final String HEADER_COMPLETED_DATE = "\uC644\uB8CC\uC77C";
    private static final String HEADER_COMMON_APPLY_VERSION = "\uACF5\uD1B5\uC801\uC6A9\uBC84\uC804";
    private static final String HEADER_DEPLOY_FOLDER_NAME = "\uBC30\uD3EC\uD3F4\uB354\uBA85";
    private static final String HEADER_SCRIPT_EXECUTED = "Script\uC2E4\uD589\uC5EC\uBD80";
    private static final String HEADER_SCRIPT_EXECUTED_TYPO = "Scrpit\uC2E4\uD589\uC5EC\uBD80";
    private static final String DEFAULT_SYSTEM_NAME = "\uBBF8\uC9C0\uC815";
    private static final String DEFAULT_PATCH_CONTENT = "\uD328\uCE58\uB0B4\uC5ED \uC5C6\uC74C";
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2,4}[./-]\\d{1,2}[./-]\\d{1,2})");

    private final PatchHistoryRepository patchHistoryRepository;
    private final PatchHistoryUploadLogRepository uploadLogRepository;

    @Transactional
    public PatchHistoryUploadResult processExcelFile(MultipartFile file) {
        log.info("Excel upload started. fileName={}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            String originalFilename = file.getOriginalFilename();
            PatchHistoryUploadResult result;

            if (originalFilename != null && originalFilename.contains(FILE_TYPE_MINOR_PATCH)) {
                result = processMinorPatchWorkbook(workbook);
            } else if (originalFilename != null && originalFilename.contains(FILE_TYPE_PATCH_LIST)) {
                result = processPatchListWorkbook(workbook);
            } else {
                result = processLegacyWorkbook(workbook);
            }

            uploadLogRepository.save(new PatchHistoryUploadLog(
                    defaultText(originalFilename, "unknown"),
                    result.getSavedCount(),
                    result.getExcludedCount()
            ));

            return result;
        } catch (Throwable e) {
            log.error("Excel upload failed.", e);
            throw new IllegalStateException("Excel upload failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<PatchHistoryUploadFileResponse> getRecentUploadFiles() {
        return uploadLogRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(PatchHistoryUploadFileResponse::from)
                .toList();
    }

    private PatchHistoryUploadResult processLegacyWorkbook(Workbook workbook) {
        int savedCount = 0;
        List<PatchHistory> batch = new ArrayList<>();
        Set<String> uploadContentKeys = new HashSet<>();
        List<PatchHistoryUploadExcludedItem> excludedItems = new ArrayList<>();

        for (Sheet sheet : workbook) {
            Map<String, Integer> headerMap = findHeaderRow(sheet);

            if (headerMap.isEmpty()) {
                log.warn("Legacy upload header row not found. sheet={}", sheet.getSheetName());
                continue;
            }

            int headerRowNum = headerMap.get("_HEADER_ROW");

            for (int rowNum = headerRowNum + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);

                if (row == null) {
                    continue;
                }

                String no = getCellValue(row, headerMap.get(HEADER_NO));
                String patchContent = getCellValue(row, headerMap.get(HEADER_PATCH_CONTENT));

                if (isBlank(no) && isBlank(patchContent)) {
                    continue;
                }

                String originalInfra = getCellValue(row, headerMap.get(HEADER_INFRA));
                String category = getCellValue(row, headerMap.get(HEADER_CATEGORY));
                String historyType = getCellValue(row, headerMap.get(HEADER_TYPE));
                String dbVersion = getCellValue(row, headerMap.get(HEADER_DB_VERSION));
                String deploymentVersion = getCellValue(row, headerMap.get(HEADER_DEPLOYMENT_VERSION));
                String developmentApply = getCellValue(row, headerMap.get(HEADER_DEV_APPLY));
                String note = getCellValue(row, headerMap.get(HEADER_NOTE));

                PatchHistory patchHistory = PatchHistory.builder()
                        .title(makeTitle(patchContent, sheet.getSheetName(), rowNum + 1))
                        .infraType(resolveInfraType(originalInfra))
                        .systemName(isBlank(originalInfra) ? DEFAULT_SYSTEM_NAME : limit(originalInfra, 100))
                        .customerName(isBlank(developmentApply) ? null : limit(developmentApply, 100))
                        .versionInfo(limit(dbVersion, 50))
                        .deploymentVersion(limit(deploymentVersion, 50))
                        .status(PatchStatus.RESOLVED)
                        .symptomSummary(makeSummary(patchContent))
                        .symptomDetail(defaultText(patchContent, DEFAULT_PATCH_CONTENT))
                        .causeDetail(null)
                        .actionDetail(note)
                        .tags(makeTags(originalInfra, category, historyType, sheet.getSheetName()))
                        .authorName("excel-upload")
                        .category(limit(category, 50))
                        .completedDate(null)
                        .build();

                DuplicateCheckResult duplicateCheckResult = checkDuplicatePatchHistory(patchHistory, uploadContentKeys);

                if (duplicateCheckResult.duplicate()) {
                    log.info("Duplicate legacy row skipped. sheet={}, row={}", sheet.getSheetName(), rowNum + 1);
                    excludedItems.add(makeExcludedItem(sheet.getSheetName(), rowNum + 1, patchHistory, duplicateCheckResult.reason()));
                    continue;
                }

                batch.add(patchHistory);
                savedCount = flushIfNeeded(batch, savedCount);
            }
        }

        savedCount = flush(batch, savedCount);
        log.info("Legacy Excel upload completed. savedCount={}", savedCount);

        return makeUploadResult(savedCount, excludedItems);
    }

    private PatchHistoryUploadResult processMinorPatchWorkbook(Workbook workbook) {
        Sheet sheet = workbook.getSheet("Sheet1");

        if (sheet == null) {
            throw new IllegalArgumentException("Sheet1 sheet was not found.");
        }

        Row headerRow = sheet.getRow(PATCH_LIST_HEADER_ROW_INDEX);

        if (headerRow == null) {
            throw new IllegalArgumentException("Minor patch header row was not found.");
        }

        Map<String, Integer> headerMap = buildHeaderMap(headerRow);
        validateMinorPatchHeaders(headerMap);
        log.info("Minor patch header map. sheet={}, headers={}", sheet.getSheetName(), headerMap);

        int savedCount = 0;
        List<PatchHistory> batch = new ArrayList<>();
        Set<String> uploadContentKeys = new HashSet<>();
        List<PatchHistoryUploadExcludedItem> excludedItems = new ArrayList<>();

        for (int rowNum = PATCH_LIST_DATA_START_ROW_INDEX; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);

            if (row == null) {
                continue;
            }

            PatchHistory patchHistory = buildMinorPatchHistory(row, headerMap, sheet.getSheetName(), rowNum);

            if (patchHistory == null) {
                continue;
            }

            DuplicateCheckResult duplicateCheckResult = checkDuplicatePatchHistory(patchHistory, uploadContentKeys);

            if (duplicateCheckResult.duplicate()) {
                log.info("Duplicate minor patch row skipped. sheet={}, row={}", sheet.getSheetName(), rowNum + 1);
                excludedItems.add(makeExcludedItem(sheet.getSheetName(), rowNum + 1, patchHistory, duplicateCheckResult.reason()));
                continue;
            }

            batch.add(patchHistory);
            savedCount = flushIfNeeded(batch, savedCount);
        }

        savedCount = flush(batch, savedCount);
        log.info("Minor patch upload completed. savedCount={}", savedCount);

        return makeUploadResult(savedCount, excludedItems);
    }

    private PatchHistoryUploadResult processPatchListWorkbook(Workbook workbook) {
        int savedCount = 0;
        List<PatchHistory> batch = new ArrayList<>();
        Set<String> uploadContentKeys = new HashSet<>();
        List<PatchHistoryUploadExcludedItem> excludedItems = new ArrayList<>();

        for (Sheet sheet : workbook) {
            LocalDate completedDate = extractPatchListCompletedDate(sheet);
            Row headerRow = sheet.getRow(PATCH_LIST_HEADER_ROW_INDEX);

            if (headerRow == null) {
                log.warn("Patch list header row not found. sheet={}", sheet.getSheetName());
                continue;
            }

            Map<String, Integer> headerMap = buildHeaderMap(headerRow);
            log.info("Patch list header map. sheet={}, headers={}", sheet.getSheetName(), headerMap);

            for (int rowNum = PATCH_LIST_DATA_START_ROW_INDEX; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);

                if (row == null) {
                    continue;
                }

                PatchHistory patchHistory = buildPatchListHistory(row, headerMap, sheet.getSheetName(), rowNum, completedDate);

                if (patchHistory == null) {
                    continue;
                }

                DuplicateCheckResult duplicateCheckResult = checkDuplicatePatchHistory(patchHistory, uploadContentKeys);

                if (duplicateCheckResult.duplicate()) {
                    log.info("Duplicate patch list row skipped. sheet={}, row={}", sheet.getSheetName(), rowNum + 1);
                    excludedItems.add(makeExcludedItem(sheet.getSheetName(), rowNum + 1, patchHistory, duplicateCheckResult.reason()));
                    continue;
                }

                batch.add(patchHistory);
                savedCount = flushIfNeeded(batch, savedCount);
            }
        }

        savedCount = flush(batch, savedCount);
        log.info("Patch list upload completed. savedCount={}", savedCount);

        return makeUploadResult(savedCount, excludedItems);
    }

    private PatchHistory buildPatchListHistory(
            Row row,
            Map<String, Integer> headerMap,
            String sheetName,
            int rowNum,
            LocalDate completedDate
    ) {
        String originalInfra = getCellValue(row, headerMap.get(HEADER_INFRA));
        String category = getCellValue(row, headerMap.get(HEADER_CATEGORY));
        String historyType = getCellValue(row, headerMap.get(HEADER_TYPE));
        String deploymentVersion = getCellValue(row, headerMap.get(HEADER_DEPLOYMENT_VERSION));
        String patchContent = getCellValue(row, headerMap.get(HEADER_PATCH_CONTENT));
        String note = getCellValue(row, headerMap.get(HEADER_NOTE));

        if (isBlank(originalInfra)
                && isBlank(category)
                && isBlank(historyType)
                && isBlank(deploymentVersion)
                && isBlank(patchContent)
                && isBlank(note)) {
            return null;
        }

        String combinedCategory = combineCategoryAndType(category, historyType);
        String detailContent = combineWithBlankLine(patchContent, note);
        String title = makeTitle(detailContent, sheetName, rowNum + 1);
        String summary = makeSummary(detailContent);

        if (isBlank(summary)) {
            summary = title;
        }

        return PatchHistory.builder()
                .title(title)
                .infraType(resolveInfraType(originalInfra))
                .systemName(isBlank(originalInfra) ? DEFAULT_SYSTEM_NAME : limit(originalInfra, 100))
                .customerName(null)
                .versionInfo(null)
                .status(PatchStatus.RESOLVED)
                .symptomSummary(summary)
                .symptomDetail(defaultText(detailContent, DEFAULT_PATCH_CONTENT))
                .causeDetail(null)
                .actionDetail(null)
                .tags(makeTags(originalInfra, combinedCategory, historyType, sheetName))
                .authorName("excel-upload")
                .category(limit(combinedCategory, 50))
                .deploymentVersion(limit(deploymentVersion, 50))
                .completedDate(completedDate)
                .build();
    }

    private PatchHistory buildMinorPatchHistory(
            Row row,
            Map<String, Integer> headerMap,
            String sheetName,
            int rowNum
    ) {
        String category = getCellValue(row, headerMap.get(HEADER_CATEGORY));
        String originalInfra = getCellValue(row, headerMap.get(HEADER_MINOR_INFRA));
        String function = getCellValue(row, headerMap.get(HEADER_FUNCTION));
        String content = getCellValue(row, headerMap.get(HEADER_CONTENT));
        String author = getCellValue(row, headerMap.get(HEADER_AUTHOR));
        String deploymentVersion = getCellValue(row, headerMap.get(HEADER_COMMON_APPLY_VERSION));
        LocalDate completedDate = getCellDate(row, headerMap.get(HEADER_COMPLETED_DATE));
        String deployFolderName = getCellValue(row, headerMap.get(HEADER_DEPLOY_FOLDER_NAME));
        String scriptExecuted = getCellValue(row, firstHeaderIndex(
                headerMap,
                HEADER_SCRIPT_EXECUTED,
                HEADER_SCRIPT_EXECUTED_TYPO
        ));
        String note = getCellValue(row, headerMap.get(HEADER_NOTE));

        if (isBlank(category)
                && isBlank(originalInfra)
                && isBlank(function)
                && isBlank(content)
                && isBlank(author)
                && isBlank(deploymentVersion)
                && isBlank(deployFolderName)
                && isBlank(scriptExecuted)
                && isBlank(note)) {
            return null;
        }

        String combinedCategory = combineCategoryAndType(function, category);
        String labeledDeployFolderName = isBlank(deployFolderName) ? null : "배포펄더 : " + deployFolderName;
        String detailContent = combineWithBlankLine(content, scriptExecuted, note, labeledDeployFolderName);
        String title = makeMinorPatchTitle(content, sheetName, rowNum + 1);
        String summary = makeSummary(content);

        if (isBlank(summary)) {
            summary = title;
        }

        return PatchHistory.builder()
                .title(title)
                .infraType(resolveInfraType(originalInfra))
                .systemName(isBlank(originalInfra) ? DEFAULT_SYSTEM_NAME : limit(originalInfra, 100))
                .customerName(null)
                .versionInfo(null)
                .status(PatchStatus.RESOLVED)
                .symptomSummary(summary)
                .symptomDetail(defaultText(detailContent, DEFAULT_PATCH_CONTENT))
                .causeDetail(null)
                .actionDetail(null)
                .tags(makeTags(originalInfra, combinedCategory, category, sheetName))
                .authorName(isBlank(author) ? "excel-upload" : limit(author, 100))
                .category(limit(combinedCategory, 50))
                .deploymentVersion(limit(deploymentVersion, 50))
                .completedDate(completedDate)
                .build();
    }

    private void validateMinorPatchHeaders(Map<String, Integer> headerMap) {
        for (String requiredHeader : List.of(
                HEADER_CATEGORY,
                HEADER_MINOR_INFRA,
                HEADER_FUNCTION,
                HEADER_CONTENT,
                HEADER_AUTHOR,
                HEADER_COMPLETED_DATE,
                HEADER_COMMON_APPLY_VERSION,
                HEADER_DEPLOY_FOLDER_NAME,
                HEADER_NOTE
        )) {
            if (!headerMap.containsKey(requiredHeader)) {
                throw new IllegalArgumentException("Required minor patch header was not found: " + requiredHeader);
            }
        }

        if (firstHeaderIndex(headerMap, HEADER_SCRIPT_EXECUTED, HEADER_SCRIPT_EXECUTED_TYPO) == null) {
            throw new IllegalArgumentException("Required minor patch header was not found: Script/Scrpit 실행여부");
        }
    }

    private Integer firstHeaderIndex(Map<String, Integer> headerMap, String... headerNames) {
        for (String headerName : headerNames) {
            Integer index = headerMap.get(headerName);

            if (index != null) {
                return index;
            }
        }

        return null;
    }

    private int flushIfNeeded(List<PatchHistory> batch, int savedCount) {
        if (batch.size() < BATCH_SIZE) {
            return savedCount;
        }

        return flush(batch, savedCount);
    }

    private int flush(List<PatchHistory> batch, int savedCount) {
        if (batch.isEmpty()) {
            return savedCount;
        }

        patchHistoryRepository.saveAll(batch);
        int nextSavedCount = savedCount + batch.size();
        batch.clear();

        return nextSavedCount;
    }

    private DuplicateCheckResult checkDuplicatePatchHistory(PatchHistory patchHistory, Set<String> uploadContentKeys) {
        String contentKey = makeContentKey(patchHistory);

        if (uploadContentKeys.contains(contentKey)) {
            return new DuplicateCheckResult(true, "\uC5C5\uB85C\uB4DC \uD30C\uC77C \uB0B4 \uC911\uBCF5");
        }

        boolean existsInDatabase = patchHistoryRepository.existsBySameContent(
                patchHistory.getTitle(),
                patchHistory.getInfraType().name(),
                patchHistory.getSystemName(),
                patchHistory.getCustomerName(),
                patchHistory.getVersionInfo(),
                patchHistory.getStatus().name(),
                patchHistory.getSymptomSummary(),
                patchHistory.getSymptomDetail(),
                patchHistory.getCauseDetail(),
                patchHistory.getActionDetail(),
                patchHistory.getAuthorName(),
                patchHistory.getCategory(),
                patchHistory.getDeploymentVersion(),
                patchHistory.getCompletedDate()
        );

        uploadContentKeys.add(contentKey);

        if (existsInDatabase) {
            return new DuplicateCheckResult(true, "\uAE30\uC874 \uB4F1\uB85D \uB370\uC774\uD130\uC640 \uC911\uBCF5");
        }

        return new DuplicateCheckResult(false, null);
    }

    private PatchHistoryUploadExcludedItem makeExcludedItem(
            String sheetName,
            int rowNumber,
            PatchHistory patchHistory,
            String reason
    ) {
        return PatchHistoryUploadExcludedItem.builder()
                .sheetName(sheetName)
                .rowNumber(rowNumber)
                .title(patchHistory.getTitle())
                .reason(reason)
                .build();
    }

    private PatchHistoryUploadResult makeUploadResult(
            int savedCount,
            List<PatchHistoryUploadExcludedItem> excludedItems
    ) {
        return PatchHistoryUploadResult.builder()
                .savedCount(savedCount)
                .excludedCount(excludedItems.size())
                .excludedItems(excludedItems)
                .build();
    }

    private record DuplicateCheckResult(boolean duplicate, String reason) {
    }

    private String makeContentKey(PatchHistory patchHistory) {
        return String.join(
                "\u001F",
                nullToKey(patchHistory.getTitle()),
                nullToKey(patchHistory.getInfraType() != null ? patchHistory.getInfraType().name() : null),
                nullToKey(patchHistory.getSystemName()),
                nullToKey(patchHistory.getCustomerName()),
                nullToKey(patchHistory.getVersionInfo()),
                nullToKey(patchHistory.getStatus() != null ? patchHistory.getStatus().name() : null),
                nullToKey(patchHistory.getSymptomSummary()),
                nullToKey(patchHistory.getSymptomDetail()),
                nullToKey(patchHistory.getCauseDetail()),
                nullToKey(patchHistory.getActionDetail()),
                nullToKey(patchHistory.getAuthorName()),
                nullToKey(patchHistory.getCategory()),
                nullToKey(patchHistory.getDeploymentVersion()),
                nullToKey(patchHistory.getCompletedDate() != null ? patchHistory.getCompletedDate().toString() : null)
        );
    }

    private String nullToKey(String value) {
        return value == null ? "\u0000" : value;
    }

    private Map<String, Integer> findHeaderRow(Sheet sheet) {
        for (Row row : sheet) {
            Map<String, Integer> headerMap = buildHeaderMap(row);

            if (headerMap.containsKey(HEADER_NO)
                    && headerMap.containsKey(HEADER_INFRA)
                    && headerMap.containsKey(HEADER_PATCH_CONTENT)) {
                headerMap.put("_HEADER_ROW", row.getRowNum());
                return headerMap;
            }
        }

        return Map.of();
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();

        for (Cell cell : headerRow) {
            String header = normalizeHeader(getCellValue(cell));

            if (!isBlank(header)) {
                headerMap.put(header, cell.getColumnIndex());
            }
        }

        return headerMap;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", "").trim();
    }

    private String getCellValue(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null) {
            return "";
        }

        return getCellValue(row.getCell(columnIndex));
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

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

    private String makeTitle(String patchContent, String sheetName, int rowNum) {
        String summary = makeSummary(patchContent);

        if (isBlank(summary)) {
            summary = sheetName + " patch history " + rowNum;
        }

        return limit(summary, 200);
    }

    private String makeMinorPatchTitle(String content, String sheetName, int rowNum) {
        String firstSentence = firstSentence(content);

        if (isBlank(firstSentence)) {
            firstSentence = sheetName + " minor patch " + rowNum;
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

    private String makeSummary(String text) {
        if (isBlank(text)) {
            return "";
        }

        String firstLine = text.split("\\R")[0].trim();
        return limit(firstLine, 300);
    }

    private String makeTags(String infra, String category, String historyType, String sheetName) {
        StringBuilder sb = new StringBuilder();

        appendTag(sb, "excel");
        appendTag(sb, sheetName);
        appendTag(sb, infra);
        appendTag(sb, category);
        appendTag(sb, historyType);

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

    private String combineCategoryAndType(String category, String historyType) {
        if (isBlank(category)) {
            return historyType;
        }

        if (isBlank(historyType)) {
            return category;
        }

        return category.trim() + "(" + historyType.trim() + ")";
    }

    private String combineWithBlankLine(String first, String second) {
        if (isBlank(first)) {
            return second;
        }

        if (isBlank(second)) {
            return first;
        }

        return first.trim() + "\n\n" + second.trim();
    }

    private String combineWithBlankLine(String first, String second, String third, String fourth) {
        List<String> values = new ArrayList<>();

        if (!isBlank(first)) {
            values.add(first.trim());
        }

        if (!isBlank(second)) {
            values.add(second.trim());
        }

        if (!isBlank(third)) {
            values.add(third.trim());
        }

        if (!isBlank(fourth)) {
            values.add(fourth.trim());
        }

        return String.join("\n\n", values);
    }

    private LocalDate extractPatchListCompletedDate(Sheet sheet) {
        Row row = sheet.getRow(3);

        if (row == null) {
            return null;
        }

        for (Cell cell : row) {
            String value = getCellValue(cell);
            Matcher matcher = DATE_PATTERN.matcher(value);

            if (matcher.find()) {
                return parseDate(matcher.group(1));
            }
        }

        return null;
    }

    private LocalDate getCellDate(Row row, Integer columnIndex) {
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
                    .toLocalDate();
        }

        return parseDate(getCellValue(cell));
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        String normalized = value.trim().replace(".", "-").replace("/", "-");

        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy-M-d"),
                DateTimeFormatter.ofPattern("yy-MM-dd"),
                DateTimeFormatter.ofPattern("yy-M-d")
        )) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        throw new IllegalArgumentException("Unsupported date format: " + value);
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
