package com.example.issuesystem.knowledge.dto;

import com.example.issuesystem.common.domain.InfraType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 지식공유 등록 요청 DTO
 *
 * multipart/form-data 등록 시에도 request 파트에 JSON으로 들어간다.
 */
@Getter
@Setter
public class KnowledgeShareCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    private String customerName;

    @NotBlank(message = "담당자는 필수입니다.")
    private String authorName;

    /**
     * 기존 화면 호환용.
     * 실제 첨부파일은 MultipartFile files로 받는다.
     */
    private String attachmentName;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    /** 등록 시 인프라 다중 선택 */
    @NotEmpty(message = "인프라는 하나 이상 선택해야 합니다.")
    private Set<InfraType> infraTypes;
}
