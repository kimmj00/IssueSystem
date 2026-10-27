package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** 지식공유 Repository */
public interface KnowledgeShareRepository extends JpaRepository<KnowledgeShare, Long>, KnowledgeShareSearch {

    interface KnowledgeShareInfraTypeRow {
        Long getKnowledgeShareId();

        String getInfraType();
    }

    /** 동시에 상세 조회가 발생해도 누락되지 않도록 조회수를 DB에서 직접 증가시킨다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update knowledge_share
               set view_count = view_count + 1
             where id = :id
            """, nativeQuery = true)
    int incrementViewCount(@Param("id") Long id);

    /** 지식공유 글 ID 목록 기준 인프라 선택값 일괄 조회 */
    @Query(value = """
            select ksi.knowledge_share_id as knowledgeShareId, ksi.infra_type as infraType
            from knowledge_share_infra ksi
            where ksi.knowledge_share_id in (:knowledgeShareIds)
            order by ksi.knowledge_share_id
            """, nativeQuery = true)
    List<KnowledgeShareInfraTypeRow> findInfraTypesByKnowledgeShareIdIn(
            @Param("knowledgeShareIds") Collection<Long> knowledgeShareIds
    );

}
