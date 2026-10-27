package com.example.issuesystem.search;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;

/** 후보/전체 건수/현재 페이지를 같은 SQL과 같은 스냅샷에서 한 번 계산한다. */
@Repository
@RequiredArgsConstructor
public class SearchPageExecutor {
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    public Page<Long> search(String source, String eligibleSql, String keyword, String title, String content,
                             MapSqlParameterSource params, Pageable pageable) {
        if (entityManager.isJoinedToTransaction()) entityManager.flush();
        params.addValue("searchSource", source, Types.VARCHAR)
                .addValue("searchKeyword", keyword, Types.VARCHAR)
                .addValue("searchTitle", title, Types.VARCHAR)
                .addValue("searchContent", content, Types.VARCHAR)
                .addValue("pageLimit", pageable.getPageSize()).addValue("pageOffset", pageable.getOffset());
        String sql = """
                /* SearchPageExecutor.%s */
                with eligible as materialized (%s), matches as materialized (
                    select * from app_search_rank(:searchSource, :searchKeyword, :searchTitle, :searchContent,
                        array(select id from eligible))
                ), totals as (select count(*) as total from matches), paged as (
                    select document_id, score, tie_score from matches
                    order by score desc, tie_score desc, document_id desc limit :pageLimit offset :pageOffset
                )
                select p.document_id, t.total from totals t left join paged p on true
                order by p.score desc, p.tie_score desc, p.document_id desc
                """.formatted(source, eligibleSql);
        return jdbc.query(sql, params, rs -> {
            var ids = new ArrayList<Long>();
            long total = 0;
            while (rs.next()) {
                total = rs.getLong("total");
                long id = rs.getLong("document_id");
                if (!rs.wasNull()) ids.add(id);
            }
            return new PageImpl<>(ids, pageable, total);
        });
    }
}
