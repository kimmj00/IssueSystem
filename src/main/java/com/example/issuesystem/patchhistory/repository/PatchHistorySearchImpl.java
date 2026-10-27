package com.example.issuesystem.patchhistory.repository;

import com.example.issuesystem.search.SearchPageExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class PatchHistorySearchImpl implements PatchHistorySearch {
    private final SearchPageExecutor executor;
    @Override
    public Page<Long> searchIds(
            String keyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            String infraTypesCsv,
            String categoriesCsv,
            String deploymentVersionsCsv,
            String filterDelimiter,
            String versionRelation,
            String referenceVersionNumber,
            boolean useReferenceCompletedDateComparison,
            LocalDate versionReferenceCompletedDate,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return execute(keyword, null, null, infraType, status, customerName, category, deploymentVersion, infraTypesCsv, categoriesCsv, deploymentVersionsCsv, filterDelimiter, versionRelation, referenceVersionNumber, useReferenceCompletedDateComparison, versionReferenceCompletedDate, startDate, endDate, pageable);
    }
    @Override
    public Page<Long> searchIdsBySeparatedKeywords(
            String titleKeyword,
            String contentKeyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            String infraTypesCsv,
            String categoriesCsv,
            String deploymentVersionsCsv,
            String filterDelimiter,
            String versionRelation,
            String referenceVersionNumber,
            boolean useReferenceCompletedDateComparison,
            LocalDate versionReferenceCompletedDate,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        return execute(null, titleKeyword, contentKeyword, infraType, status, customerName, category, deploymentVersion, infraTypesCsv, categoriesCsv, deploymentVersionsCsv, filterDelimiter, versionRelation, referenceVersionNumber, useReferenceCompletedDateComparison, versionReferenceCompletedDate, startDate, endDate, pageable);
    }

    private Page<Long> execute(
            String keyword, String titleKeyword, String contentKeyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            String infraTypesCsv,
            String categoriesCsv,
            String deploymentVersionsCsv,
            String filterDelimiter,
            String versionRelation,
            String referenceVersionNumber,
            boolean useReferenceCompletedDateComparison,
            LocalDate versionReferenceCompletedDate,
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
        params.addValue("infraTypesCsv", infraTypesCsv, Types.VARCHAR);
        params.addValue("categoriesCsv", categoriesCsv, Types.VARCHAR);
        params.addValue("deploymentVersionsCsv", deploymentVersionsCsv, Types.VARCHAR);
        params.addValue("filterDelimiter", filterDelimiter, Types.VARCHAR);
        params.addValue("versionRelation", versionRelation, Types.VARCHAR);
        params.addValue("referenceVersionNumber", referenceVersionNumber, Types.VARCHAR);
        params.addValue("useReferenceCompletedDateComparison", useReferenceCompletedDateComparison, Types.BOOLEAN);
        params.addValue("versionReferenceCompletedDate", versionReferenceCompletedDate, Types.DATE);
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
                            and (:infraTypesCsv is null or
                                coalesce(i.infra_type, '') = any(string_to_array(:infraTypesCsv, :filterDelimiter)))
                            and (:categoriesCsv is null or
                                coalesce(i.category, '') = any(string_to_array(:categoriesCsv, :filterDelimiter)))
                            and (:deploymentVersionsCsv is null or
                                coalesce(i.deployment_version, '') = any(string_to_array(:deploymentVersionsCsv, :filterDelimiter)))
                            and (
                                :versionRelation = 'ALL'
                                or (
                                    :useReferenceCompletedDateComparison = true
                                    and cast(:versionReferenceCompletedDate as date) is not null
                                    and (
                                        (:versionRelation = 'BEFORE'
                                            and i.completed_date <= cast(:versionReferenceCompletedDate as date))
                                        or (:versionRelation = 'AFTER'
                                            and i.completed_date >= cast(:versionReferenceCompletedDate as date))
                                    )
                                )
                                or (
                                    :useReferenceCompletedDateComparison = false
                                    and :referenceVersionNumber is not null
                                    and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '') is not null
                                    and (
                                        (:versionRelation = 'BEFORE'
                                            and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '')::numeric
                                                <= cast(:referenceVersionNumber as numeric))
                                        or (:versionRelation = 'AFTER'
                                            and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '')::numeric
                                                >= cast(:referenceVersionNumber as numeric))
                                    )
                                )
                            )
                            and i.created_at >= :startDate
                            and i.created_at < :endDate
                """, keyword, titleKeyword, contentKeyword, params, pageable);
    }

    @Override
    public Page<Long> detailSearchIds(
            String keyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            String infraTypesCsv,
            String categoriesCsv,
            String deploymentVersionsCsv,
            String filterDelimiter,
            String detailType,
            String detailDeploymentVersion,
            String detailVersionRelation,
            String selectedVersionNumber,
            boolean useCompletedDateComparison,
            LocalDate referenceCompletedDate,
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
        params.addValue("infraTypesCsv", infraTypesCsv, Types.VARCHAR);
        params.addValue("categoriesCsv", categoriesCsv, Types.VARCHAR);
        params.addValue("deploymentVersionsCsv", deploymentVersionsCsv, Types.VARCHAR);
        params.addValue("filterDelimiter", filterDelimiter, Types.VARCHAR);
        params.addValue("detailType", detailType, Types.VARCHAR);
        params.addValue("detailDeploymentVersion", detailDeploymentVersion, Types.VARCHAR);
        params.addValue("detailVersionRelation", detailVersionRelation, Types.VARCHAR);
        params.addValue("selectedVersionNumber", selectedVersionNumber, Types.VARCHAR);
        params.addValue("useCompletedDateComparison", useCompletedDateComparison, Types.BOOLEAN);
        params.addValue("referenceCompletedDate", referenceCompletedDate, Types.DATE);
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
                                and (:infraTypesCsv is null or
                                    coalesce(i.infra_type, '') = any(string_to_array(:infraTypesCsv, :filterDelimiter)))
                                and (:categoriesCsv is null or
                                    coalesce(i.category, '') = any(string_to_array(:categoriesCsv, :filterDelimiter)))
                                and (:deploymentVersionsCsv is null or
                                    coalesce(i.deployment_version, '') = any(string_to_array(:deploymentVersionsCsv, :filterDelimiter)))
                                and (
                                    (:detailType = 'WEB' and (
                                        coalesce(i.category, '') ilike '%DB%'
                                        or coalesce(i.category, '') ilike '%WEB%'
                                        or coalesce(i.category, '') ilike '%보안취약점%'
                                    ))
                                    or (:detailType = 'CORE' and coalesce(i.category, '') not ilike '%WEB%')
                                    or (:detailType = 'SECURITY' and lower(concat_ws(' ',
                                        i.title,
                                        i.system_name,
                                        i.customer_name,
                                        i.symptom_summary,
                                        i.symptom_detail,
                                        i.cause_detail,
                                        i.action_detail,
                                        i.tags,
                                        i.category,
                                        i.deployment_version,
                                        i.version_info
                                    )) like '%보안취약점%')
                                )
                                and (
                                    :detailDeploymentVersion is null
                                    or :detailVersionRelation = 'ALL'
                                    or (
                                        :useCompletedDateComparison = true
                                        and cast(:referenceCompletedDate as date) is not null
                                        and (
                                            (:detailVersionRelation = 'SAME' and i.completed_date = cast(:referenceCompletedDate as date))
                                            or (:detailVersionRelation = 'BEFORE' and i.completed_date < cast(:referenceCompletedDate as date))
                                            or (:detailVersionRelation = 'AFTER' and i.completed_date > cast(:referenceCompletedDate as date))
                                        )
                                    )
                                    or (
                                        :useCompletedDateComparison = false
                                        and :selectedVersionNumber is not null
                                        and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '') is not null
                                        and (
                                            (:detailVersionRelation = 'SAME'
                                                and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '')::numeric
                                                    = cast(:selectedVersionNumber as numeric))
                                            or (:detailVersionRelation = 'BEFORE'
                                                and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '')::numeric
                                                    < cast(:selectedVersionNumber as numeric))
                                            or (:detailVersionRelation = 'AFTER'
                                                and nullif(regexp_replace(coalesce(i.deployment_version, ''), '\\D', '', 'g'), '')::numeric
                                                    > cast(:selectedVersionNumber as numeric))
                                        )
                                    )
                                )
                                and i.created_at >= :startDate
                                and i.created_at < :endDate
                """, keyword, null, null, params, pageable);
    }
}
