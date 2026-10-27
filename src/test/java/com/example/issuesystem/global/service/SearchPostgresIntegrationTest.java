package com.example.issuesystem.global.service;

import com.example.issuesystem.knowledge.repository.KnowledgeShareRepository;
import com.example.issuesystem.patchhistory.repository.PatchHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkIssueGlobalSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** erd/table-schema.sql이 적용된 전용 PostgreSQL에서 실행한다. H2로 검색 SQL을 흉내 내지 않는다. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "search.test.postgres.url", matches = "jdbc:postgresql:.*")
class SearchPostgresIntegrationTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime END = START.plusYears(1);
    @Autowired JdbcTemplate jdbc;
    @Autowired KnowledgeShareRepository knowledge;
    @Autowired PatchHistoryRepository patches;
    @Autowired WorkIssueGlobalSearchRepository work;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getProperty("search.test.postgres.url"));
        properties.add("spring.datasource.username", () -> System.getProperty("search.test.postgres.user", "search_test"));
        properties.add("spring.datasource.password", () -> System.getProperty("search.test.postgres.password", ""));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @CsvSource({"서비스,서지스", "서지스,서비스", "agent,agnet", "agnet,agent", "agent,agnt", "agnt,agent", "agent!!!,agant!!!", "agant!!!,agent!!!", "에이전트 설치,에이전트설치"})
    void 오타와_공백차이를_양방향으로_제목과_본문에서_찾는다(String stored, String query) {
        long titleId = insertKnowledge(stored, "무관한 본문", "테스트고객");
        long bodyId = insertKnowledge("무관한 제목", "x".repeat(10000) + " " + stored, "테스트고객");
        assertThat(knowledge.searchBySeparatedKeywords(query, null, null, null, null, "|", START, END, PageRequest.of(0, 10))
                .getContent()).extracting(item -> item.getId()).contains(titleId).doesNotContain(bodyId);
        assertThat(knowledge.searchBySeparatedKeywords(null, query, null, null, null, "|", START, END, PageRequest.of(0, 10))
                .getContent()).extracting(item -> item.getId()).contains(bodyId).doesNotContain(titleId);
        assertThat(knowledge.search(query, null, null, null, "|", START, END, PageRequest.of(0, 10))
                .getContent()).extracting(item -> item.getId()).contains(titleId, bodyId);
    }

    @Test
    void 정확일치_오타_흩어진글자_순서와_전체건수_페이지를_유지한다() {
        long exact = insertKnowledge("서비스", "", "고객");
        long typo = insertKnowledge("서지스", "", "고객");
        long partial = insertKnowledge("서쪽 안내 스위치", "", "고객");
        var ids = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            var result = knowledge.searchBySeparatedKeywords("서비스", null, null, null, null, "|", START, END, PageRequest.of(page, 1));
            assertThat(result.getTotalElements()).isEqualTo(3);
            ids.add(result.getContent().get(0).getId());
        }
        assertThat(ids).containsExactly(exact, typo, partial);
        assertThat(knowledge.searchBySeparatedKeywords("서비스", null, null, null, null, "|", START, END, PageRequest.of(3, 1)).getContent()).isEmpty();
    }

    @Test
    void 제목본문_AND와_고객사_날짜_인프라_필터를_유지한다() {
        long id = insertKnowledge("서비스", "agent", "고객A");
        insertKnowledge("서비스", "무관", "고객A");
        insertKnowledge("서비스", "agent", "고객B");
        jdbc.update("insert into knowledge_share_infra values (?, 'EMS')", id);
        var result = knowledge.searchBySeparatedKeywords("서지스", "agnet", "고객A", "EMS", null, "|", START, END, PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(item -> item.getId()).containsExactly(id);
        assertThat(knowledge.searchBySeparatedKeywords("서지스", "agnet", "고객A", "NMS", null, "|", START, END, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(knowledge.searchBySeparatedKeywords("서지스", null, null, null, null, "|", END, END.plusYears(1), PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void 수정_삭제_시_검색인덱스가_자동으로_갱신된다() {
        long id = insertKnowledge("서비스", "", "고객");
        jdbc.update("update knowledge_share set title = 'agent' where id = ?", id);
        assertThat(knowledge.search("서지스", null, null, null, "|", START, END, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(knowledge.search("agnet", null, null, null, "|", START, END, PageRequest.of(0, 10)).getContent()).hasSize(1);
        jdbc.update("delete from knowledge_share where id = ?", id);
        assertThat(jdbc.queryForObject("select count(*) from app_search_document where source_kind = 'KNOWLEDGE' and document_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_search_posting where source_kind = 'KNOWLEDGE' and document_id = ?", Long.class, id)).isZero();
    }

    @Test
    void 패치_실제쿼리에서도_제목본문_오타와_count가_동일하다() {
        for (int i = 0; i < 2; i++) insertPatch("서비스", "agent");
        var result = patches.searchIdsBySeparatedKeywords("서지스", "agnet", null, null, null, null, null,
                null, null, null, "|", "ALL", null, false, null, START, END, PageRequest.of(0, 1));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 작업이력_전체에서_순위를_정한뒤_페이징하고_실제건수를_반환한다() {
        long upload = jdbc.queryForObject("insert into work_report_upload(original_file_name,project_count,maintenance_count,created_at,updated_at) values ('test.xlsx',2,1,?,?) returning id", Long.class, START, START);
        long typo = jdbc.queryForObject("insert into work_project_history(upload_id,row_no,client_name,scope,created_at,updated_at) values (?,1,'서지스','agent',?,?) returning id", Long.class, upload, START, START);
        long exact = jdbc.queryForObject("insert into work_maintenance_history(upload_id,row_no,maintenance_name,progress_issues,created_at,updated_at) values (?,1,'서비스','agent',?,?) returning id", Long.class, upload, START, START);
        var first = work.search("서비스", null, null, null, START, END, "ALL", 0, 1);
        var second = work.search("서비스", null, null, null, START, END, "ALL", 1, 1);
        assertThat(first.rows()).containsExactly(new WorkIssueGlobalSearchRepository.Row("MAINTENANCE", exact));
        assertThat(second.rows()).containsExactly(new WorkIssueGlobalSearchRepository.Row("PROJECT", typo));
        assertThat(first.projectTotal()).isEqualTo(1);
        assertThat(first.maintenanceTotal()).isEqualTo(1);
        assertThat(work.search(null, "서지스", "agnet", null, START, END, "PROJECT", 0, 10).rows()).hasSize(1);
        var outside = work.search("서비스", null, null, null, START, END, "ALL", 5, 1);
        assertThat(outside.rows()).isEmpty();
        assertThat(outside.projectTotal() + outside.maintenanceTotal()).isEqualTo(2);
    }

    @Test
    void HTTP응답까지_오타검색_결과가_전달된다() throws Exception {
        insertKnowledge("서비스", "agent", "고객");
        insertPatch("서비스", "agent");
        long started = System.nanoTime();
        mvc.perform(get("/api/global-search").param("titleKeyword", "서지스").param("contentKeyword", "agnet")
                        .param("startDate", "2026-01-01").param("endDate", "2026-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.knowledgeTotal").value(1))
                .andExpect(jsonPath("$.data.patchHistoryTotal").value(1))
                .andExpect(jsonPath("$.data.knowledgeShares[0].title").value("서비스"));
        System.out.printf("검색 통합 API 테스트: %.1f ms%n", (System.nanoTime() - started) / 1_000_000.0);
    }

    @Test
    void 필터로_제외된_글과_제목에_없는_글은_본문_후보에_포함되지_않는다() {
        long match = insertKnowledge("agent", "보안", "고객A");
        insertKnowledge("agent", "보안", "고객B");
        insertKnowledge("다른 글", "보안", "고객A");
        insertKnowledge("agent", "무관", "고객A");
        var page = knowledge.searchBySeparatedKeywords("agent", "보안", "고객A", null, null, "|",
                START, END, PageRequest.of(0, 1));
        assertThat(page.getContent()).extracting(item -> item.getId()).containsExactly(match);
        assertThat(page.getTotalElements()).isEqualTo(1);
        var outside = knowledge.searchBySeparatedKeywords("agent", "보안", "고객A", null, null, "|",
                START, END, PageRequest.of(8, 1));
        assertThat(outside.getContent()).isEmpty();
        assertThat(outside.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 빈_검색어도_전체건수와_페이지를_유지한다() {
        insertKnowledge("가", "", "고객A");
        insertKnowledge("나", "", "고객A");
        var page = knowledge.search(null, "고객A", null, null, "|", START, END, PageRequest.of(1, 1));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(knowledge.search("없는단어", "고객A", null, null, "|", START, END, PageRequest.of(0, 1))
                .getTotalElements()).isZero();
    }

    @Test
    void 날짜형_버전과_숫자형_버전_필터를_유지한다() {
        long first = insertPatch("agent", "보안");
        long second = insertPatch("agent", "보안");
        jdbc.update("update issue_case set category='WEB',deployment_version='B100',completed_date='2026-01-01' where id=?", first);
        jdbc.update("update issue_case set category='CORE',deployment_version='B200',completed_date='2026-02-01' where id=?", second);
        var numbers = patches.searchIds("agent", null, null, null, null, null, null, null, null, "|",
                "BEFORE", "150", false, null, START, END, PageRequest.of(0, 10));
        assertThat(numbers.getContent()).containsExactly(first);
        var dates = patches.searchIds("agent", null, null, null, null, null, null, null, null, "|",
                "AFTER", null, true, java.time.LocalDate.of(2026, 2, 1), START, END, PageRequest.of(0, 10));
        assertThat(dates.getContent()).containsExactly(second);
        var detail = patches.detailSearchIds("agent", null, null, null, null, null, null, null, null, "|",
                "WEB", null, "ALL", null, false, null, START, END, PageRequest.of(0, 10));
        assertThat(detail.getContent()).containsExactly(first);
    }

    private long insertKnowledge(String title, String body, String customer) {
        return jdbc.queryForObject("insert into knowledge_share(title,content,customer_name,author_name,created_at,updated_at) values (?,?,?,'test',?,?) returning id", Long.class, title, body, customer, START, START);
    }

    private long insertPatch(String title, String body) {
        return jdbc.queryForObject("insert into issue_case(title,infra_type,system_name,status,symptom_summary,symptom_detail,author_name,created_at,updated_at) values (?,'EMS','test','OPEN','',?,'test',?,?) returning id", Long.class, title, body, START, START);
    }
}
