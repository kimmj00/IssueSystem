package com.example.issuesystem.patchhistory.repository;

import com.example.issuesystem.patchhistory.domain.PatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PatchHistoryRepository extends JpaRepository<PatchHistory, Long>, PatchHistorySearch {

    /** ID 목록으로 패치이력 엔티티 재조회 */
    List<PatchHistory> findByIdIn(List<Long> ids);

    @Query(value = """
            select distinct i.deployment_version
            from issue_case i
            where i.deployment_version is not null
              and btrim(i.deployment_version) <> ''
              and (
                  :detailType is null
                  or (:detailType = 'WEB' and i.deployment_version ~ '^[0-9]{8}\\.[A-Za-z]+$')
                  or (:detailType = 'CORE' and i.deployment_version ilike 'B%')
                  or (:detailType = 'SECURITY')
              )
            order by i.deployment_version desc
            """,
            nativeQuery = true)
    List<String> findDeploymentVersions(@Param("detailType") String detailType);

    @Query(value = """
            select distinct i.category
            from issue_case i
            where i.category is not null
              and btrim(i.category) <> ''
            order by i.category
            """,
            nativeQuery = true)
    List<String> findCategories();

    @Query(value = """
            select distinct i.deployment_version
            from issue_case i
            where i.deployment_version is not null
              and btrim(i.deployment_version) <> ''
              and (:categoriesCsv is null or
                  coalesce(i.category, '') = any(string_to_array(:categoriesCsv, :filterDelimiter)))
            order by i.deployment_version desc
            """,
            nativeQuery = true)
    List<String> findDeploymentVersionsByCategories(
            @Param("categoriesCsv") String categoriesCsv,
            @Param("filterDelimiter") String filterDelimiter
    );

    @Query(value = """
            select i.completed_date
            from issue_case i
            where i.deployment_version = :deploymentVersion
              and i.completed_date is not null
            order by i.completed_date desc
            limit 1
            """,
            nativeQuery = true)
    LocalDate findReferenceCompletedDate(@Param("deploymentVersion") String deploymentVersion);

    @Query(value = """
            select exists (
                select 1
                from issue_case i
                where i.title is not distinct from :title
                  and i.infra_type is not distinct from cast(:infraType as varchar)
                  and i.system_name is not distinct from :systemName
                  and i.customer_name is not distinct from :customerName
                  and i.version_info is not distinct from :versionInfo
                  and i.status is not distinct from cast(:status as varchar)
                  and i.symptom_summary is not distinct from :symptomSummary
                  and i.symptom_detail is not distinct from :symptomDetail
                  and i.cause_detail is not distinct from :causeDetail
                  and i.action_detail is not distinct from :actionDetail
                  and i.author_name is not distinct from :authorName
                  and i.category is not distinct from :category
                  and i.deployment_version is not distinct from :deploymentVersion
                  and i.completed_date is not distinct from :completedDate
            )
            """,
            nativeQuery = true)
    boolean existsBySameContent(
            @Param("title") String title,
            @Param("infraType") String infraType,
            @Param("systemName") String systemName,
            @Param("customerName") String customerName,
            @Param("versionInfo") String versionInfo,
            @Param("status") String status,
            @Param("symptomSummary") String symptomSummary,
            @Param("symptomDetail") String symptomDetail,
            @Param("causeDetail") String causeDetail,
            @Param("actionDetail") String actionDetail,
            @Param("authorName") String authorName,
            @Param("category") String category,
            @Param("deploymentVersion") String deploymentVersion,
            @Param("completedDate") LocalDate completedDate
    );
}
