package com.example.issuesystem.knowledge.domain;

import com.example.issuesystem.common.BaseTimeEntity;
import com.example.issuesystem.issue.domain.InfraType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 지식공유 본문 엔티티
 *
 * 인프라는 다중 선택이 가능하므로 knowledge_share_infra 테이블에 별도로 저장한다.
 */
@Getter
@Entity
@Table(name = "knowledge_share")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeShare extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지식공유 제목 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 고객사명 */
    @Column(length = 100)
    private String customerName;

    /** 담당자 또는 작성자 */
    @Column(nullable = false, length = 100)
    private String authorName;

    /**
     * 기존 화면 호환용 필드.
     * 실제 첨부파일은 knowledge_share_attachment에 저장한다.
     */
    @Column(length = 255)
    private String attachmentName;

    /** 지식공유 내용 */
    @Column(columnDefinition = "text", nullable = false)
    private String content;

    /**
     * 등록 시 체크박스로 여러 인프라를 선택한다.
     *
     * 예:
     * knowledge_share id = 1
     * infraTypes = EMS, DBMS
     *
     * DB 저장:
     * knowledge_share_infra
     * - knowledge_share_id: 1, infra_type: EMS
     * - knowledge_share_id: 1, infra_type: DBMS
     */
    @ElementCollection(targetClass = InfraType.class)
    @CollectionTable(
            name = "knowledge_share_infra",
            joinColumns = @JoinColumn(name = "knowledge_share_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "infra_type", nullable = false, length = 50)
    private Set<InfraType> infraTypes = new LinkedHashSet<>();

    @Builder
    public KnowledgeShare(
            String title,
            String customerName,
            String authorName,
            String attachmentName,
            String content,
            Set<InfraType> infraTypes
    ) {
        this.title = title;
        this.customerName = customerName;
        this.authorName = authorName;
        this.attachmentName = attachmentName;
        this.content = content;

        if (infraTypes != null) {
            this.infraTypes = infraTypes;
        }
    }
}
