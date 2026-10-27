package com.example.issuesystem.issue.repository;

import com.example.issuesystem.search.SearchPageExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import java.sql.Types;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class IssueCaseSearchImpl implements IssueCaseSearch {
    private final SearchPageExecutor executor;
    @Override
    public Page<Long> searchIds(
            String keyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        var params = new MapSqlParameterSource();
        params.addValue("infraType", infraType, Types.VARCHAR);
        params.addValue("status", status, Types.VARCHAR);
        params.addValue("customerName", customerName, Types.VARCHAR);
        params.addValue("category", category, Types.VARCHAR);
        params.addValue("deploymentVersion", deploymentVersion, Types.VARCHAR);
        params.addValue("startDate", startDate, Types.TIMESTAMP);
        params.addValue("endDate", endDate, Types.TIMESTAMP);
        return executor.search("PATCH", """
                select i.id from issue_case i where (:infraType is null or i.infra_type = cast(:infraType as varchar))
                            and (:status is null or i.status = cast(:status as varchar))
                            and (:customerName is null or :customerName = '' or
                                coalesce(i.customer_name, '') ilike concat('%', :customerName, '%'))
                            and (:category is null or :category = '' or
                                coalesce(i.category, '') ilike concat('%', :category, '%'))
                            and (:deploymentVersion is null or :deploymentVersion = '' or
                                coalesce(i.deployment_version, '') ilike concat('%', :deploymentVersion, '%'))
                            and i.created_at >= :startDate
                            and i.created_at < :endDate
                """, keyword, null, null, params, pageable);
    }
}