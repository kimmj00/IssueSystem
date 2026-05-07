package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.knowledge.domain.KnowledgeShareAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 지식공유 첨부파일 Repository */
public interface KnowledgeShareAttachmentRepository extends JpaRepository<KnowledgeShareAttachment, Long> {

    /** 지식공유 글 ID 기준 첨부파일 목록 조회 */
    List<KnowledgeShareAttachment> findByKnowledgeShareId(Long knowledgeShareId);
}
