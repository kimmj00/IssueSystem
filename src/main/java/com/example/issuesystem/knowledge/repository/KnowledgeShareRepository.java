package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.issue.domain.InfraType;
import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** 지식공유 Repository */
public interface KnowledgeShareRepository extends JpaRepository<KnowledgeShare, Long> {

    /**
     * 지식공유 검색
     *
     * keyword: 제목 또는 내용 검색
     * customerName: 고객사 검색
     * infraType: 등록된 인프라 목록 중 해당 값이 포함되면 조회
     * startDate/endDate: 등록일 범위
     */
    @Query(
            value = """
                select distinct ks
                from KnowledgeShare ks
                left join ks.infraTypes infra
                where
                    (
                        :keyword is null
                        or :keyword = ''
                        or lower(ks.title) like lower(concat('%', :keyword, '%'))
                        or lower(ks.content) like lower(concat('%', :keyword, '%'))
                    )
                    and (
                        :customerName is null
                        or :customerName = ''
                        or lower(coalesce(ks.customerName, '')) like lower(concat('%', :customerName, '%'))
                    )
                    and (
                        :infraType is null
                        or infra = :infraType
                    )
                    and ks.createdAt >= :startDate
                    and ks.createdAt < :endDate
                order by ks.id desc
            """,
            countQuery = """
                select count(distinct ks)
                from KnowledgeShare ks
                left join ks.infraTypes infra
                where
                    (
                        :keyword is null
                        or :keyword = ''
                        or lower(ks.title) like lower(concat('%', :keyword, '%'))
                        or lower(ks.content) like lower(concat('%', :keyword, '%'))
                    )
                    and (
                        :customerName is null
                        or :customerName = ''
                        or lower(coalesce(ks.customerName, '')) like lower(concat('%', :customerName, '%'))
                    )
                    and (
                        :infraType is null
                        or infra = :infraType
                    )
                    and ks.createdAt >= :startDate
                    and ks.createdAt < :endDate
            """
    )
    Page<KnowledgeShare> search(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("infraType") InfraType infraType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
