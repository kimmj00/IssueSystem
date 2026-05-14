package com.example.issuesystem.global.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 통합검색 응답 DTO */
@Getter
@Builder
public class GlobalSearchResponse {
    private long issueTotal;
    private long knowledgeTotal;
    private long total;
    private List<GlobalSearchItemResponse> issues;
    private List<GlobalSearchItemResponse> knowledgeShares;
}
