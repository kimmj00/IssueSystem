package com.example.issuesystem.global.service;

import com.example.issuesystem.knowledge.repository.KnowledgeShareRepository;
import com.example.issuesystem.patchhistory.repository.PatchHistoryRepository;
import com.example.issuesystem.workissuehistory.repository.WorkIssueGlobalSearchRepository;
import com.example.issuesystem.config.QueryExecutionLoggingConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import javax.sql.DataSource;

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
    void 검색_함수의_계획_설정이_다른_쿼리에_남지_않는다() {
        jdbc.execute("set local plan_cache_mode = force_custom_plan");
        jdbc.execute("set local jit = on");
        jdbc.execute("set local client_min_messages = warning");
        long id = insertKnowledge("agent", "보안", "고객");
        var page = knowledge.searchBySeparatedKeywords("agent", "보안", null, null, null, "|",
                START, END, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(item -> item.getId()).containsExactly(id);
        assertThat(jdbc.queryForObject("select current_setting('plan_cache_mode')", String.class))
                .isEqualTo("force_custom_plan");
        assertThat(jdbc.queryForObject("select current_setting('jit')", String.class)).isEqualTo("on");
        assertThat(jdbc.queryForObject("select current_setting('client_min_messages')", String.class)).isEqualTo("warning");
    }

    @Test
    void 기존_검색연결을_자동갱신하고_예전_INSERT와_단어_변경도_지원한다() throws Exception {
        // 배포 전 4열 구조를 재현한다. 테스트 전체는 종료 시 롤백된다.
        jdbc.execute("drop trigger app_search_posting_length on app_search_posting");
        jdbc.execute("alter table app_search_posting drop column char_signature cascade");
        jdbc.execute("alter table app_search_posting drop column word_length cascade");
        jdbc.update("""
                insert into app_search_document values ('MIGRATION',1,'TITLE','agent','agent',app_search_chars('agent'))
                """);
        jdbc.update("insert into app_search_term values (md5('agent'),'agent',app_search_chars('agent'),5) on conflict do nothing");
        jdbc.update("insert into app_search_posting values ('MIGRATION',1,'TITLE',md5('agent'))");
        String sql = new ClassPathResource("db/search-functions.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("select word_length from app_search_posting where source_kind='MIGRATION'", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select attnotnull from pg_attribute where attrelid='app_search_posting'::regclass and attname='word_length'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select indisvalid from pg_index where indexrelid='idx_app_search_posting_scope_terms'::regclass", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select char_signature=app_search_signature(app_search_chars('agent')) from app_search_posting where source_kind='MIGRATION'", Boolean.class)).isTrue();
        // 두 번째 적용은 기존 데이터를 다시 쓰지 않는다.
        String tuple = jdbc.queryForObject("select ctid::text from app_search_posting where source_kind='MIGRATION'", String.class);
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("select ctid::text from app_search_posting where source_kind='MIGRATION'", String.class)).isEqualTo(tuple);
        jdbc.update("delete from app_search_posting where source_kind='MIGRATION'");
        jdbc.update("insert into app_search_posting values ('MIGRATION',1,'TITLE',md5('agent'))");
        assertThat(jdbc.queryForObject("select word_length from app_search_posting where source_kind='MIGRATION'", Integer.class)).isEqualTo(5);
        jdbc.update("insert into app_search_term values (md5('보안'),'보안',app_search_chars('보안'),2) on conflict do nothing");
        jdbc.update("update app_search_posting set word_hash=md5('보안') where source_kind='MIGRATION'");
        assertThat(jdbc.queryForObject("select word_length from app_search_posting where source_kind='MIGRATION'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select char_signature=app_search_signature(app_search_chars('보안')) from app_search_posting where source_kind='MIGRATION'", Boolean.class)).isTrue();
        long id = insertKnowledge("서비스", "보안", "고객");
        assertThat(knowledge.searchBySeparatedKeywords("서지스", "보안", null, null, null, "|", START, END, PageRequest.of(0, 10))
                .getContent()).extracting(item -> item.getId()).containsExactly(id);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void PostgreSQL_진단_알림이_앱_로그로_전달된다(CapturedOutput output) throws Exception {
        var properties = new QueryExecutionLoggingConfig.QueryLoggingProperties();
        var factory = new StaticListableBeanFactory(Map.of("properties", properties));
        var processor = QueryExecutionLoggingConfig.queryExecutionLoggingDataSourceBeanPostProcessor(
                factory.getBeanProvider(QueryExecutionLoggingConfig.QueryLoggingProperties.class));
        var source = (DataSource) processor.postProcessAfterInitialization(jdbc.getDataSource(), "diagnosticTest");
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("""
                /* app_search_rank( diagnostic transport test */
                do $$ begin raise notice 'issue_search_metrics %',
                    json_build_object('source','KNOWLEDGE','field','CONTENT','elapsedMs',1001); end $$
                """)) {
            statement.execute();
        }
        assertThat(output).contains("검색 엔진 지연:", "\"KNOWLEDGE\"", "\"CONTENT\"", "1001");
    }

    @Test
    void 제한된_편집거리도_기존_유사도와_일치판정과_점수가_같다() {
        // 짧은 문자열의 모든 조합: 삽입/삭제/치환/인접 교환, 반복 문자, 한글을 양방향 비교한다.
        Long mismatches = jdbc.queryForObject("""
                with recursive words(w,n) as (
                    select ''::text,0 union all
                    select w||ch,n+1 from words cross join unnest(array['a','b','가']) ch where n<4
                ), pairs as materialized (
                    select a.w,b.w as q from words a cross join words b
                    union all
                    select w,overlay(w placing '오류' from 4 for 2)
                    from (select repeat('가나다abc',n)||'가나다abc' as w from generate_series(1,15) n) s
                    union all
                    select w,substring(w from 2)||left(w,1)
                    from (select repeat('가나다abc',n)||'가나다abc' as w from generate_series(1,15) n) s
                    union all
                    select w,case when n%3=0 then substring(w from 2)||left(w,1)
                                  when n%3=1 then overlay(w placing '나a' from 1+n%length(w) for 2)
                                  else substring(w from 1+n%length(w)) end
                    from (select n,substring(repeat(md5(n::text)||'가나다',4) from 1 for 1+n%100) as w
                          from generate_series(1,1000) n) s
                    union all
                    select w,q from (values ('abc','abcd'),('abcd','abc'),('abcdef','abcdxefyz'),
                        ('abcdxefyz','abcdef'),('ca','abc'),('agent!!!','agant!!!'),
                        (repeat('가',50),repeat('가',37)||repeat('나',13)),
                        (repeat('a',100),repeat('a',66)||repeat('b',34))) v(w,q)
                ), scores as materialized (
                    select app_search_similarity(w,q) as old_score,
                           app_search_similarity_match(w,q) as new_score,
                           case when length(q)<=3 then 0.66 else 0.74 end::double precision as threshold
                    from pairs
                ) select count(*) from scores
                where (old_score>=threshold) is distinct from (new_score>=threshold)
                   or (old_score>=threshold and old_score is distinct from new_score)
                """, Long.class);
        assertThat(mismatches).isZero();
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

    @Test
    void 공용사전과_검색범위가_달라도_모든단어를_평가한_결과와_점수가_동일하다() throws Exception {
        jdbc.execute(new ClassPathResource("db/search-reference.sql").getContentAsString(StandardCharsets.UTF_8));
        jdbc.execute("set local plan_cache_mode = force_generic_plan");
        // 압축률에 의존하지 않고 큰 본문에서 쓰는 경로도 검증한다. 종료 시 롤백된다.
        jdbc.execute("alter table app_search_document alter column normalized_text set storage external");
        String[] texts = {"agent", "agnet", "agnt", "agant!!!", "서비스", "서지스", "보안", "보완",
                "abcdef", "abcdeff", "abcdxefyz", "aaaaa", "aaaba", "aabaa", "a g e n t", "에이전트 설치", "일반 안내", "!!!"};
        var allowed = new ArrayList<Long>();
        for (int i = 0; i < texts.length; i++) {
            long id = insertKnowledge(texts[i], "x".repeat(10000) + " abcde " + texts[(i + 5) % texts.length], "고객");
            if (i % 2 == 0) allowed.add(id);
        }
        // 본문 전용 단어가 공용 사전에 많이 남은 상황에서도 문서 범위를 벗어나지 않아야 한다.
        jdbc.update("""
                insert into app_search_term(word_hash,word,characters,word_length)
                select md5(w),w,app_search_chars(w),length(w)
                from (select 'abcde'||lpad(to_hex(n),4,'0') as w from generate_series(1,6500) n) s
                on conflict do nothing
                """);
        String subset = "{" + allowed.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "}";
        // NULL=전체, 빈 배열=없음, 중복 ID=집합이라는 기존 범위 의미도 검증한다.
        for (String scope : new String[]{null, "{}", subset, subset.replace("}", "," + allowed.get(0) + "}")}) {
            for (String field : new String[]{"TITLE", "CONTENT", "ANY"}) {
                for (String keyword : new String[]{null, "", " \t ", "agent", "agnet", "agnt", "agant!!!", "a", "aa", "aaaaa",
                        "서비스", "서지스", "보안", "보완", "설치", "에이전트설치", "ABCDEF", "abcdxefyz", "없는단어", "!!!"}) {
                    var actual = jdbc.queryForList("select * from app_search_candidates_scope('KNOWLEDGE',?,?,cast(? as bigint[])) order by document_id",
                            field, keyword, scope);
                    var expected = jdbc.queryForList("select * from pg_temp.search_reference('KNOWLEDGE',?,?,cast(? as bigint[])) order by document_id",
                            field, keyword, scope);
                    assertThat(actual).as("field=%s, keyword=%s, scope=%s", field, keyword, scope)
                            .containsExactlyElementsOf(expected);
                }
            }
        }
    }

    private long insertKnowledge(String title, String body, String customer) {
        return jdbc.queryForObject("insert into knowledge_share(title,content,customer_name,author_name,created_at,updated_at) values (?,?,?,'test',?,?) returning id", Long.class, title, body, customer, START, START);
    }

    private long insertPatch(String title, String body) {
        return jdbc.queryForObject("insert into issue_case(title,infra_type,system_name,status,symptom_summary,symptom_detail,author_name,created_at,updated_at) values (?,'EMS','test','OPEN','',?,'test',?,?) returning id", Long.class, title, body, START, START);
    }
}
