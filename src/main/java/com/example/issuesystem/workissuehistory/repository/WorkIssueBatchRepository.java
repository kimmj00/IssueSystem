package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorkIssueBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 프로젝트 현황을 JDBC batch insert로 저장합니다.
     *
     * JPA saveAll은 행 수만큼 Entity 상태를 관리하고, IDENTITY 전략에서는 insert batch 효과가 제한될 수 있습니다.
     * 이 테이블은 업로드 후 개별 행의 엔티티 ID를 즉시 사용할 필요가 없으므로 JdbcTemplate batchUpdate가 더 적합합니다.
     */
    public int saveProjects(Long uploadId, List<WorkProjectHistory> projects) {
        if (projects == null || projects.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO work_project_history (
                    upload_id,
                    row_no,
                    excel_no,
                    sales_rep,
                    client_name,
                    scope,
                    oz,
                    dashboard,
                    apm,
                    location,
                    start_date,
                    project_scale,
                    executors,
                    visits,
                    md,
                    progress_logs,
                    remaining_issues,
                    site_code,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime now = LocalDateTime.now();

        int[] result = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                WorkProjectHistory item = projects.get(i);
                int index = 1;

                ps.setLong(index++, uploadId);
                ps.setInt(index++, item.getRowNo());
                ps.setString(index++, item.getNo());
                ps.setString(index++, item.getSalesRep());
                ps.setString(index++, item.getClientName());
                ps.setString(index++, item.getScope());
                ps.setString(index++, item.getOz());
                ps.setString(index++, item.getDashboard());
                ps.setString(index++, item.getApm());
                ps.setString(index++, item.getLocation());
                ps.setString(index++, item.getStartDate());
                ps.setString(index++, item.getProjectScale());
                ps.setString(index++, item.getExecutors());
                ps.setObject(index++, item.getVisits());
                ps.setObject(index++, item.getMd());
                ps.setString(index++, item.getProgressLogs());
                ps.setString(index++, item.getRemainingIssues());
                ps.setString(index++, item.getSiteCode());
                ps.setTimestamp(index++, Timestamp.valueOf(now));
                ps.setTimestamp(index, Timestamp.valueOf(now));
            }

            @Override
            public int getBatchSize() {
                return projects.size();
            }
        });

        return countAffectedRows(result);
    }

    /**
     * 유지보수 현황을 JDBC batch insert로 저장합니다.
     * 컬럼 수가 많기 때문에 JPA saveAll보다 batch prepared statement 방식이 업로드 체감 속도에 유리합니다.
     */
    public int saveMaintenance(Long uploadId, List<WorkMaintenanceHistory> maintenanceItems) {
        if (maintenanceItems == null || maintenanceItems.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO work_maintenance_history (
                    upload_id,
                    row_no,
                    excel_no,
                    maintenance_name,
                    version,
                    pg_version,
                    web_version,
                    status_date,
                    is_uploaded,
                    sms_status,
                    nms_status,
                    oz,
                    dashboard,
                    siem,
                    apm,
                    sales_grade,
                    contract_type,
                    visit_type,
                    cycle,
                    method,
                    contract_start,
                    contract_end,
                    visits,
                    md,
                    region,
                    inspection_dates,
                    progress_issues,
                    sales_rep,
                    main_dev,
                    sub_dev,
                    remarks,
                    site_code,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime now = LocalDateTime.now();

        int[] result = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                WorkMaintenanceHistory item = maintenanceItems.get(i);
                int index = 1;

                ps.setLong(index++, uploadId);
                ps.setInt(index++, item.getRowNo());
                ps.setString(index++, item.getNo());
                ps.setString(index++, item.getMaintenanceName());
                ps.setString(index++, item.getVersion());
                ps.setString(index++, item.getPgVersion());
                ps.setString(index++, item.getWebVersion());
                ps.setString(index++, item.getStatusDate());
                ps.setString(index++, item.getIsUploaded());
                ps.setString(index++, item.getSmsStatus());
                ps.setString(index++, item.getNmsStatus());
                ps.setString(index++, item.getOz());
                ps.setString(index++, item.getDashboard());
                ps.setString(index++, item.getSiem());
                ps.setString(index++, item.getApm());
                ps.setString(index++, item.getSalesGrade());
                ps.setString(index++, item.getContractType());
                ps.setString(index++, item.getVisitType());
                ps.setString(index++, item.getCycle());
                ps.setString(index++, item.getMethod());
                ps.setString(index++, item.getContractStart());
                ps.setString(index++, item.getContractEnd());
                ps.setObject(index++, item.getVisits());
                ps.setObject(index++, item.getMd());
                ps.setString(index++, item.getRegion());
                ps.setString(index++, item.getInspectionDates());
                ps.setString(index++, item.getProgressIssues());
                ps.setString(index++, item.getSalesRep());
                ps.setString(index++, item.getMainDev());
                ps.setString(index++, item.getSubDev());
                ps.setString(index++, item.getRemarks());
                ps.setString(index++, item.getSiteCode());
                ps.setTimestamp(index++, Timestamp.valueOf(now));
                ps.setTimestamp(index, Timestamp.valueOf(now));
            }

            @Override
            public int getBatchSize() {
                return maintenanceItems.size();
            }
        });

        return countAffectedRows(result);
    }

    /**
     * summary 조회는 전체 목록을 가져와 Java에서 합산하지 않고 DB에서 바로 count/sum을 계산합니다.
     * 업로드 건별 행 수가 늘어날수록 이 방식이 훨씬 가볍습니다.
     */
    public SummaryStats getSummaryStats(Long uploadId) {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM work_project_history WHERE upload_id = ?) AS project_count,
                    (SELECT COALESCE(SUM(md), 0) FROM work_project_history WHERE upload_id = ?) AS project_md_total,
                    (SELECT COUNT(*) FROM work_maintenance_history WHERE upload_id = ?) AS maintenance_count,
                    (SELECT COALESCE(SUM(md), 0) FROM work_maintenance_history WHERE upload_id = ?) AS maintenance_md_total
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new SummaryStats(
                        rs.getInt("project_count"),
                        rs.getInt("maintenance_count"),
                        rs.getDouble("project_md_total"),
                        rs.getDouble("maintenance_md_total")
                ),
                uploadId,
                uploadId,
                uploadId,
                uploadId
        );
    }

    /**
     * JDBC 드라이버에 따라 SUCCESS_NO_INFO(-2)가 올 수 있어, 그 경우 batch 크기만큼 성공으로 간주합니다.
     */
    private int countAffectedRows(int[] result) {
        int count = 0;

        for (int value : result) {
            if (value > 0) {
                count += value;
            } else if (value == PreparedStatement.SUCCESS_NO_INFO) {
                count++;
            }
        }

        return count;
    }

    public record SummaryStats(
            int projectCount,
            int maintenanceCount,
            double projectMdTotal,
            double maintenanceMdTotal
    ) {
    }
}
