package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import com.example.issuesystem.search.SearchPageExecutor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class KnowledgeShareSearchImpl implements KnowledgeShareSearch {
    private final SearchPageExecutor executor;
    private final EntityManager entityManager;

    @Override
    public Page<KnowledgeShare> search(String keyword, String customerName, String infraType,
            String infraTypesCsv, String filterDelimiter, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return execute(keyword, null, null, customerName, infraType, infraTypesCsv, filterDelimiter, startDate, endDate, pageable);
    }

    @Override
    public Page<KnowledgeShare> searchBySeparatedKeywords(String titleKeyword, String contentKeyword,
            String customerName, String infraType, String infraTypesCsv, String filterDelimiter,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return execute(null, titleKeyword, contentKeyword, customerName, infraType, infraTypesCsv, filterDelimiter, startDate, endDate, pageable);
    }

    private Page<KnowledgeShare> execute(String keyword, String title, String content, String customer,
            String infra, String infraCsv, String delimiter, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        var params = new MapSqlParameterSource().addValue("customer", customer, Types.VARCHAR)
                .addValue("infra", infra, Types.VARCHAR).addValue("infraCsv", infraCsv, Types.VARCHAR)
                .addValue("delimiter", delimiter, Types.VARCHAR)
                .addValue("start", start, Types.TIMESTAMP).addValue("end", end, Types.TIMESTAMP);
        Page<Long> page = executor.search("KNOWLEDGE", """
                select ks.id from knowledge_share ks
                where (:customer is null or :customer = '' or lower(coalesce(ks.customer_name, '')) like lower(concat('%', :customer, '%')))
                  and (:infra is null or exists (select 1 from knowledge_share_infra i
                      where i.knowledge_share_id = ks.id and i.infra_type = :infra))
                  and (:infraCsv is null or exists (select 1 from knowledge_share_infra i
                      where i.knowledge_share_id = ks.id and i.infra_type = any(string_to_array(:infraCsv, :delimiter))))
                  and ks.created_at >= :start and ks.created_at < :end
                """, keyword, title, content, params, pageable);
        if (page.isEmpty()) return new PageImpl<>(java.util.List.of(), pageable, page.getTotalElements());
        var entities = entityManager.createQuery("select k from KnowledgeShare k where k.id in :ids", KnowledgeShare.class)
                .setParameter("ids", page.getContent()).getResultList().stream()
                .collect(Collectors.toMap(KnowledgeShare::getId, Function.identity()));
        return new PageImpl<>(page.getContent().stream().map(entities::get).toList(), pageable, page.getTotalElements());
    }
}
