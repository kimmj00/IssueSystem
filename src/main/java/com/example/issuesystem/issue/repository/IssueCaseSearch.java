package com.example.issuesystem.issue.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface IssueCaseSearch {
    Page<Long> searchIds(
            String keyword,
            String infraType,
            String status,
            String customerName,
            String category,
            String deploymentVersion,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
}
