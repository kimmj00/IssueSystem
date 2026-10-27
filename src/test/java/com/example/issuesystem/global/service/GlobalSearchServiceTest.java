package com.example.issuesystem.global.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.global.dto.GlobalSearchResponse;
import com.example.issuesystem.knowledge.dto.KnowledgeShareResponse;
import com.example.issuesystem.knowledge.service.KnowledgeShareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalSearchServiceTest {
    private final KnowledgeShareService knowledgeService = mock(KnowledgeShareService.class);
    private final GlobalSearchService service = new GlobalSearchService(null, knowledgeService, null, null, null);

    @ParameterizedTest
    @CsvSource({
            "설치, 설치 안내, 없음, 100000",
            "에이전트설치, 에이전트 설치, 없음, 90000",
            "agent, agnet, 없음, 80800",
            "abcde, abcxy, 없음, 1003",
            "abcdef, abcdxy, 없음, 80746",
            "설치 오류, 설치 안내, 없음, 70001",
            "설치, 없음, 설치 안내, 50000",
            "에이전트설치, 없음, 에이전트 설치, 45000",
            "agent, 없음, agnet, 40800",
            "설치 오류, 없음, 설치 안내, 30001",
            "설치, '', '', 0",
            "'', 설치, 설치, 0"
    })
    void 검색_일치_등급과_오타_동점_점수를_유지한다(String keyword, String title, String body, int expected) {
        assertThat(score(keyword, title, body)).isEqualTo(expected);
    }

    @Test
    void 긴_본문_끝의_오타도_검색한다() {
        assertThat(score("agent", "없음", "x".repeat(500_000) + " agnet")).isEqualTo(40800);
    }

    @Test
    void 한글_인접문자_교환과_짧은_검색어_임계값을_유지한다() {
        assertThat(score("가나다", "가다나", "")).isEqualTo(80666);
        assertThat(score("가나", "나다", "")).isZero();
    }

    @Test
    void 긴_본문_열건의_검색_응답을_빠르게_만들고_미리보기와_페이징을_유지한다() {
        String longBody = "x".repeat(500_000) + " agnet";
        List<KnowledgeShareResponse> rows = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> KnowledgeShareResponse.builder()
                        .id(id).title("기술 안내").content(longBody).build())
                .toList();
        when(knowledgeService.search("agent", null, null, null, null, null, null, null, 0, 10))
                .thenReturn(PageResponse.<KnowledgeShareResponse>builder()
                        .content(rows).page(0).size(10).totalElements(11).totalPages(2).hasNext(true).build());

        GlobalSearchResponse response = assertTimeout(Duration.ofSeconds(3), () -> service.search(
                "agent", null, null, null, null, null, null,
                0, 10, 0, 10, 0, 7, null, Set.of(GlobalSearchService.SearchTarget.KNOWLEDGE)));

        assertThat(response.getKnowledgeTotal()).isEqualTo(11);
        assertThat(response.isKnowledgeHasNext()).isTrue();
        assertThat(response.getKnowledgeShares()).hasSize(10).allSatisfy(item -> {
            assertThat(item.getMatchScore()).isEqualTo(40800);
            assertThat(item.getSummary().length()).isLessThanOrEqualTo(506);
            assertThat(item.getDetail().length()).isLessThanOrEqualTo(1206);
        });
    }

    private int score(String keyword, String title, String body) {
        return ReflectionTestUtils.invokeMethod(service, "calculateStructuredMatchScore",
                keyword, List.of(title), List.of(body), List.of());
    }
}
