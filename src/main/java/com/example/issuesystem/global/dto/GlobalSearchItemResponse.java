package com.example.issuesystem.global.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통합검색 결과 1건 응답 DTO
 *
 * sourceType:
 * - PATCH_HISTORY: 패치이력 결과
 * - KNOWLEDGE: 지식공유 DB 결과
 */
@Getter
@Builder
public class GlobalSearchItemResponse {
    private String sourceType;
    private String sourceLabel;
    private Long id;
    private String title;
    private String summary;
    private String detail;
    private List<String> infraTypes;
    private String customerName;
    private String authorName;
    private LocalDateTime createdAt;
    private int matchScore;
    private String matchLevel;
}
