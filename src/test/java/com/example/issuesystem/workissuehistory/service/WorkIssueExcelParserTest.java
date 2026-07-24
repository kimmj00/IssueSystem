package com.example.issuesystem.workissuehistory.service;

import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import com.example.issuesystem.workissuehistory.domain.WorkReportUpload;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkIssueExcelParserTest {

    private final WorkIssueExcelParser parser = new WorkIssueExcelParser();

    @Test
    void 세로로_병합된_프로젝트의_모든_수행인원_행을_파싱한다() throws Exception {
        byte[] excel;

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet projectSheet = workbook.createSheet("01_프로젝트");
            String[] executors = {"김정대", "천우진", "최승훈", "임성현", "김민제"};

            for (int index = 0; index < executors.length; index++) {
                Row row = projectSheet.createRow(6 + index);
                row.createCell(9).setCellValue(index == 2 || index == 4 ? "소규모" : "단순구축");
                row.createCell(10).setCellValue(executors[index]);
                row.createCell(11).setCellValue(10 + index);
                row.createCell(12).setCellValue(4 + index);
                row.createCell(13).setCellValue("진행사항 " + executors[index]);
            }

            // 프로젝트 병합 영역 안에 있지만 수행 정보가 전혀 없는 꼬리 행은 저장 대상이 아닙니다.
            projectSheet.createRow(11).createCell(9).setCellValue("단순구축");
            projectSheet.createRow(12).createCell(9).setCellValue("단순구축");

            projectSheet.getRow(6).createCell(0).setCellValue("4");
            projectSheet.getRow(6).createCell(2).setCellValue("AI 바이브코딩\n(이슈관리시스템)");
            projectSheet.addMergedRegion(new CellRangeAddress(6, 12, 0, 0));
            projectSheet.addMergedRegion(new CellRangeAddress(6, 12, 2, 2));

            workbook.write(output);
            excel = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "weekly-report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        List<WorkProjectHistory> projects = parser.parse(file, WorkReportUpload.builder().build()).getProjects();

        assertThat(projects).hasSize(5);
        assertThat(projects)
                .extracting(WorkProjectHistory::getClientName)
                .containsOnly("AI 바이브코딩\n(이슈관리시스템)");
        assertThat(projects)
                .extracting(WorkProjectHistory::getExecutors)
                .containsExactly("김정대", "천우진", "최승훈", "임성현", "김민제");
    }
}
