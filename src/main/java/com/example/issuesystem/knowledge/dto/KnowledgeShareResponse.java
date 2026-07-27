package com.example.issuesystem.knowledge.dto;

import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import com.example.issuesystem.knowledge.domain.KnowledgeShareAttachment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 지식공유 응답 DTO */
@Getter
@Builder
public class KnowledgeShareResponse {

    private Long id;
    private String title;
    private String customerName;
    private String authorName;
    private Long createdByAccountId;
    private String attachmentName;
    private String content;
    private Long viewCount;
    private Set<InfraType> infraTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentResponse> attachments;

    /** 첨부파일 응답 DTO */
    @Getter
    @Builder
    public static class AttachmentResponse {
        private Long id;
        private String originalFileName;
        private Long fileSize;
    }

    public static KnowledgeShareResponse from(
            KnowledgeShare knowledgeShare,
            List<KnowledgeShareAttachment> attachments
    ) {
        return KnowledgeShareResponse.builder()
                .id(knowledgeShare.getId())
                .title(knowledgeShare.getTitle())
                .customerName(knowledgeShare.getCustomerName())
                .authorName(knowledgeShare.getAuthorName())
                .createdByAccountId(knowledgeShare.getCreatedByAccountId())
                .attachmentName(knowledgeShare.getAttachmentName())
                .content(knowledgeShare.getContent())
                .viewCount(knowledgeShare.getViewCount())
                // @ElementCollection을 DTO 생성 시점에 실제 Set으로 복사한다.
                .infraTypes(
                        knowledgeShare.getInfraTypes() == null
                                ? Set.of()
                                : Set.copyOf(knowledgeShare.getInfraTypes())
                )
                // 화면의 등록일은 마지막 수정 적용 시점을 표시한다.
                // 원본 생성 시점은 DB created_at에 그대로 보존한다.
                .createdAt(knowledgeShare.getUpdatedAt())
                .updatedAt(knowledgeShare.getUpdatedAt())
                .attachments(
                        attachments == null
                                ? List.of()
                                : attachments.stream()
                                .map(file -> AttachmentResponse.builder()
                                        .id(file.getId())
                                        .originalFileName(file.getOriginalFileName())
                                        .fileSize(file.getFileSize())
                                        .build())
                                .toList()
                )
                .build();
    }
}
