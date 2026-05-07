package com.example.issuesystem.knowledge.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지식공유 첨부파일 엔티티
 *
 * 실제 파일은 서버 디스크에 저장하고,
 * DB에는 원본 파일명, 저장 파일명, 저장 경로, 파일 크기만 저장한다.
 *
 * 저장된 실제 파일은 KnowledgeFileStorageService에서 압축 후 암호화한다.
 */
@Getter
@Entity
@Table(name = "knowledge_share_attachment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeShareAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 첨부파일이 속한 지식공유 글 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_share_id", nullable = false)
    private KnowledgeShare knowledgeShare;

    /** 사용자가 업로드한 원본 파일명 */
    @Column(nullable = false, length = 255)
    private String originalFileName;

    /** 서버에 저장된 확장자 없는 랜덤 파일명 */
    @Column(nullable = false, length = 255)
    private String storedFileName;

    /** 서버 실제 저장 경로 */
    @Column(nullable = false, length = 500)
    private String storedPath;

    /** 원본 파일 크기(byte). 압축/암호화된 디스크 파일 크기와 다를 수 있다. */
    @Column(nullable = false)
    private Long fileSize;

    @Builder
    public KnowledgeShareAttachment(
            KnowledgeShare knowledgeShare,
            String originalFileName,
            String storedFileName,
            String storedPath,
            Long fileSize
    ) {
        this.knowledgeShare = knowledgeShare;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
    }
}
