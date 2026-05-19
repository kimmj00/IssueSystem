package com.example.issuesystem.global.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 통합검색 응답 DTO
 *
 * 변경 사항:
 * - 기존에는 이슈/지식공유 결과를 최대 50건까지만 내려줬다.
 * - 이제 이슈 결과와 지식공유 결과를 각각 페이징할 수 있도록 페이지 메타 정보를 함께 내려준다.
 */
@Getter
@Builder
public class GlobalSearchResponse {

    /** 이슈관리 시스템 전체 검색 결과 수 */
    private long issueTotal;

    /** 지식공유 DB 전체 검색 결과 수 */
    private long knowledgeTotal;

    /** 이슈 + 지식공유 전체 검색 결과 수 */
    private long total;

    /** 현재 페이지에 표시할 이슈 결과 목록 */
    private List<GlobalSearchItemResponse> issues;

    /** 현재 페이지에 표시할 지식공유 결과 목록 */
    private List<GlobalSearchItemResponse> knowledgeShares;

    /** 이슈 결과 현재 페이지 번호. 0부터 시작한다. */
    private int issuePage;

    /** 이슈 결과 페이지당 표시 개수 */
    private int issueSize;

    /** 이슈 결과 전체 페이지 수 */
    private int issueTotalPages;

    /** 이슈 결과 다음 페이지 존재 여부 */
    private boolean issueHasNext;

    /** 이슈 결과 이전 페이지 존재 여부 */
    private boolean issueHasPrevious;

    /** 지식공유 결과 현재 페이지 번호. 0부터 시작한다. */
    private int knowledgePage;

    /** 지식공유 결과 페이지당 표시 개수 */
    private int knowledgeSize;

    /** 지식공유 결과 전체 페이지 수 */
    private int knowledgeTotalPages;

    /** 지식공유 결과 다음 페이지 존재 여부 */
    private boolean knowledgeHasNext;

    /** 지식공유 결과 이전 페이지 존재 여부 */
    private boolean knowledgeHasPrevious;
}
