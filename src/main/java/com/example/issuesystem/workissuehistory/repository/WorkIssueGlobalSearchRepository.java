package com.example.issuesystem.workissuehistory.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 두 종류의 작업이력을 DB에서 함께 정렬·페이징하여 후보 누락과 추정 건수를 없앤다. */
@Repository
@RequiredArgsConstructor
public class WorkIssueGlobalSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record Row(String type, long id) {}
    public record Result(List<Row> rows, long projectTotal, long maintenanceTotal) {}

    public Result search(String keyword, String titleKeyword, String contentKeyword, String customerName,
                         LocalDateTime start, LocalDateTime end, String type, int page, int size) {
        List<String> sources = new ArrayList<>();
        if (!"MAINTENANCE".equalsIgnoreCase(type)) {
            sources.add(sourceQuery("PROJECT", "work_project_history", "client_name"));
        }
        if (!"PROJECT".equalsIgnoreCase(type)) {
            sources.add(sourceQuery("MAINTENANCE", "work_maintenance_history", "maintenance_name"));
        }
        String sql = """
                /* WorkIssueGlobalSearchRepository.search */
                with matches as materialized (
                %s
                ), totals as (
                    select count(*) filter (where type = 'PROJECT') as project_total,
                           count(*) filter (where type = 'MAINTENANCE') as maintenance_total from matches
                ), paged as (
                    select * from matches order by score desc, id desc, type asc limit :limit offset :offset
                )
                select p.type, p.id, t.project_total, t.maintenance_total
                from totals t left join paged p on true
                order by p.score desc, p.id desc, p.type asc
                """.formatted(String.join("\nunion all\n", sources));
        var params = new MapSqlParameterSource()
                .addValue("keyword", keyword, Types.VARCHAR)
                .addValue("titleKeyword", titleKeyword, Types.VARCHAR)
                .addValue("contentKeyword", contentKeyword, Types.VARCHAR)
                .addValue("customerName", customerName, Types.VARCHAR)
                .addValue("start", start, Types.TIMESTAMP).addValue("end", end, Types.TIMESTAMP)
                .addValue("limit", size).addValue("offset", (long) page * size);
        return jdbc.query(sql, params, rs -> {
            List<Row> rows = new ArrayList<>();
            long projects = 0;
            long maintenance = 0;
            while (rs.next()) {
                projects = rs.getLong("project_total");
                maintenance = rs.getLong("maintenance_total");
                if (rs.getString("type") != null) rows.add(new Row(rs.getString("type"), rs.getLong("id")));
            }
            return new Result(rows, projects, maintenance);
        });
    }

    private String sourceQuery(String type, String table, String customerColumn) {
        // 테이블/컬럼 식별자는 위의 고정된 두 자료원에서만 선택한다.
        return """
                select '%s' as type, m.document_id as id, m.score
                from app_search_rank('%s', :keyword, :titleKeyword, :contentKeyword,
                    array(select h.id from %s h
                        where h.created_at >= :start and h.created_at < :end
                          and (:customerName is null or btrim(:customerName) = ''
                               or lower(h.%s) like concat('%%', lower(btrim(:customerName)), '%%')))) m
                """.formatted(type, type, table, customerColumn);
    }
}