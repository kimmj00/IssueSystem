package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface KnowledgeShareSearch {
    Page<KnowledgeShare> search(
            String keyword,
            String customerName,
            String infraType,
            String infraTypesCsv,
            String filterDelimiter,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
    Page<KnowledgeShare> searchBySeparatedKeywords(
            String titleKeyword,
            String contentKeyword,
            String customerName,
            String infraType,
            String infraTypesCsv,
            String filterDelimiter,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );
}
