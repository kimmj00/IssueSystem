package com.example.issuesystem.patchhistory.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface PatchHistorySearch {
    Page<Long> searchIds(
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
    );
    Page<Long> searchIdsBySeparatedKeywords(
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
    );

    Page<Long> detailSearchIds(
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
    );
}
