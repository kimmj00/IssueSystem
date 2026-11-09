package com.example.issuesystem.global.service;

import com.example.issuesystem.workissuehistory.repository.WorkIssueGlobalSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 전용 PostgreSQL에서만 명시적으로 실행하는 실제 규모 회귀 검증. 전체 삽입은 테스트 종료 시 롤백한다. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "search.test.postgres.url", matches = "jdbc:postgresql:.*")
@EnabledIfEnvironmentVariable(named = "SEARCH_PERFORMANCE_TEST", matches = "true")
class SearchPerformanceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired WorkIssueGlobalSearchRepository work;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getProperty("search.test.postgres.url"));
        p.add("spring.datasource.username", () -> System.getProperty("search.test.postgres.user", "search_test"));
        p.add("spring.datasource.password", () -> System.getProperty("search.test.postgres.password", ""));
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void 공용사전_120만단어와_작업이력_18000건에서_검색조합을_검증한다() throws Exception {
        jdbc.execute("alter table work_project_history disable trigger app_search_sync");
        jdbc.execute("alter table work_maintenance_history disable trigger app_search_sync");
        jdbc.execute("alter table knowledge_share disable trigger app_search_sync");
        // 삭제된 글의 단어도 사전에 남는다. 숫자 글자는 상위 집합으로 넣어 후보 필터에 불리한 조건을 만든다.
        jdbc.update("""
                insert into app_search_term(word_hash,word,characters,word_length)
                select md5(w),w,ARRAY['a','0','1','2','3','4','5','6','7','8','9']::text[],length(w)
                from (select 'a' || n::text as w from generate_series(1,1200000) n) s
                on conflict do nothing
                """);
        long upload = jdbc.queryForObject("insert into work_report_upload(original_file_name,project_count,maintenance_count,created_at,updated_at) values ('performance.xlsx',9000,9000,now(),now()) returning id", Long.class);
        jdbc.update("""
                insert into work_project_history(upload_id,row_no,client_name,scope,created_at,updated_at)
                select ?,n,case when n%20=0 then 'agent 설치' else '일반 프로젝트' end,
                       case when n%3=0 then '보안 점검' else '일반 작업' end,'2026-01-01','2026-01-01'
                from generate_series(1,9000) n
                """, upload);
        jdbc.update("""
                insert into work_maintenance_history(upload_id,row_no,maintenance_name,progress_issues,created_at,updated_at)
                select ?,n,case when n%20=0 then 'agent 설치' else '일반 유지보수' end,
                       case when n%3=0 then '보안 점검' else '일반 작업' end,'2026-01-01','2026-01-01'
                from generate_series(1,9000) n
                """, upload);
        // 30개 지식공유 글 중 하나에 단어 10만 개가 든 본문을 넣는다.
        jdbc.update("""
                insert into knowledge_share(title,content,customer_name,author_name,created_at,updated_at)
                select case when n=1 then 'agent 설치' else '일반 안내' end,
                       case when n=1 then (select string_agg('ag' || i::text,' ') from generate_series(1,100000) i) || ' agnt'
                            when n%3=0 then '보안 점검' else '일반 작업' end,
                       '성능검증','test','2026-01-01','2026-01-01'
                from generate_series(1,30) n
                """);
        seedSearchIndex();
        jdbc.execute("alter table work_project_history enable trigger app_search_sync");
        jdbc.execute("alter table work_maintenance_history enable trigger app_search_sync");
        jdbc.execute("alter table knowledge_share enable trigger app_search_sync");
        jdbc.execute("analyze app_search_term");
        jdbc.execute("analyze app_search_posting");
        jdbc.execute("analyze app_search_document");
        jdbc.execute("analyze work_project_history");
        jdbc.execute("analyze work_maintenance_history");
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String[] input : new String[][]{{"agent", ""}, {"agent", "보안"}, {"", "보안"}, {"", "agent"}, {"agnet", "보안"},
                {"agent", "agent"}, {"", "aaaaa"}, {"", "서비스"}, {"없는단어", "보안"}, {"", ""}}) {
            long started = System.nanoTime();
            var result = mvc.perform(get("/api/global-search").param("titleKeyword", input[0]).param("contentKeyword", input[1])
                            .param("startDate", "2026-01-01").param("endDate", "2026-12-31"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
            double ms = (System.nanoTime() - started) / 1_000_000.0;
            System.out.printf("대용량 통합검색 title=%s content=%s: %.1f ms, 응답 %d bytes%n", input[0], input[1], ms, result.getResponse().getContentAsByteArray().length);
            // 성능 회귀를 탐지하는 여유 있는 상한이다. 실제 서버 응답 시간을 보장하는 수치는 아니다.
            assertThat(ms).isLessThan(5000);
        }
        var expected = work.search(null, "agent", "보안", null,
                LocalDateTime.of(2026,1,1,0,0), LocalDateTime.of(2027,1,1,0,0), "ALL", 0, 7);
        assertThat(expected.projectTotal()).isEqualTo(150);
        assertThat(expected.maintenanceTotal()).isEqualTo(150);
        assertThat(expected.rows()).hasSize(7);
        // 조사 LIMIT은 경로 선택용이다. 큰 본문 끝의 오타 단어도 전체 검색에서 찾아야 한다.
        assertThat(jdbc.queryForObject("""
                select count(*) from app_search_rank('KNOWLEDGE',null,null,'agent',
                    array(select id from knowledge_share)) m
                join knowledge_share k on k.id=m.document_id where k.title='agent 설치'
                """, Long.class)).isEqualTo(1);
    }

    @Test
    void 대상15건의_두글자검색은_긴단어와_무관한사전을_읽지_않는다() {
        jdbc.execute("""
                create temp table short_scope_words on commit drop as
                select n,'z'||md5(('scope-'||n)::text)||'x' as word from generate_series(1,12000) n
                """);
        jdbc.execute("""
                insert into app_search_term
                select md5(w),w,app_search_chars('0123456789abcdefxz'),length(w)
                from (select repeat('z'||md5(n::text)||'x',64) as w from generate_series(1,70000) n) s
                on conflict do nothing
                """);
        jdbc.execute("""
                insert into app_search_term
                select md5(word),word,app_search_chars('0123456789abcdefxz'),length(word)
                from short_scope_words on conflict do nothing
                """);
        jdbc.execute("""
                insert into app_search_document
                select 'SHORT_SCOPE',d.n,'CONTENT',s.body||suffix,replace(s.body||suffix,' ',''),app_search_chars('0123456789abcdefxz')
                from (select n,case when n=15 then ' zz' when n=14 then ' z1z' else '' end as suffix from generate_series(1,15) n) d
                cross join (select string_agg(word,' ') as body from short_scope_words) s
                """);
        jdbc.execute("""
                insert into app_search_posting
                select 'SHORT_SCOPE',d.n,'CONTENT',md5(w.word),length(w.word)
                from generate_series(1,15) d(n) cross join short_scope_words w
                """);
        jdbc.execute("insert into app_search_term values (md5('zz'),'zz',app_search_chars('zz'),2),(md5('z1z'),'z1z',app_search_chars('z1z'),3) on conflict do nothing");
        jdbc.execute("insert into app_search_posting values ('SHORT_SCOPE',15,'CONTENT',md5('zz'),2),('SHORT_SCOPE',14,'CONTENT',md5('z1z'),3)");
        jdbc.execute("analyze app_search_posting");
        long started = System.nanoTime();
        var rows = jdbc.queryForList("""
                select * from app_search_rank('SHORT_SCOPE',null,null,'zz',
                    array(select document_id from app_search_document where source_kind='SHORT_SCOPE'))
                order by document_id
                """);
        double ms = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf("대상 15건/긴 단어 연결 18만개/두 글자 내용: %.1f ms%n", ms);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("document_id",14L).containsEntry("score",401666);
        assertThat(rows.get(1)).containsEntry("document_id",15L).containsEntry("score",601000);
        assertThat(ms).isLessThan(3000);
    }

    @Test
    void 긴_본문의_다양한_단어도_기준_점수와_동일하게_검색한다() throws Exception {
        // 같은 문구를 반복하는 자료만으로는 오타 평가 비용을 검증할 수 없다.
        // 글자는 같지만 순서가 다른 단어 1,200개를 30개 긴 본문에 나눠 넣는다.
        jdbc.execute("""
                create temp table diverse_words on commit drop as
                select n,word,md5(word) as word_hash from (
                    select n,string_agg(ch,'' order by md5(n::text||ch)) as word
                    from generate_series(1,1200) n
                    cross join unnest(string_to_array('abcdefghijklmnopqrstuvwxyz012345',null)) ch
                    group by n
                ) s
                """);
        jdbc.execute("""
                insert into app_search_document
                select 'DIVERSE',n,'CONTENT',body,replace(body,' ',''),app_search_chars(body)
                from (select d.document_id as n,string_agg(word,' ' order by w.n)||repeat(' 로그 내용 ',2000) as body
                      from generate_series(1,30) d(document_id) join diverse_words w on w.n%30=d.document_id-1
                      group by d.document_id) s
                """);
        jdbc.execute("""
                insert into app_search_term select word_hash,word,app_search_chars(word),length(word)
                from diverse_words on conflict do nothing
                """);
        jdbc.execute("""
                insert into app_search_posting select 'DIVERSE',n%30+1,'CONTENT',word_hash,length(word) from diverse_words
                """);
        jdbc.execute(new org.springframework.core.io.ClassPathResource("db/search-reference.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String keyword = "abcdefghijklmnopqrstuvwxyz012345";
        var expected = jdbc.queryForList("select * from pg_temp.search_reference('DIVERSE','CONTENT',?,null) order by document_id", keyword);
        long started = System.nanoTime();
        var actual = jdbc.queryForList("select document_id,score from app_search_rank('DIVERSE',null,null,?,null) order by document_id", keyword);
        double ms = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf("긴 본문 30건/서로 다른 단어 1200개: %.1f ms%n", ms);
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual).hasSize(30);
        assertThat(ms).isLessThan(5000);
    }

    @Test
    void 일반실행계획에서도_제목본문_동시검색이_문서수의_제곱으로_느려지지_않는다() {
        jdbc.execute("alter table work_project_history disable trigger app_search_sync");
        jdbc.execute("alter table work_maintenance_history disable trigger app_search_sync");
        long upload = jdbc.queryForObject("insert into work_report_upload(original_file_name,project_count,maintenance_count,created_at,updated_at) values ('generic-plan.xlsx',12000,12000,now(),now()) returning id", Long.class);
        jdbc.update("""
                insert into work_project_history(upload_id,row_no,client_name,scope,created_at,updated_at)
                select ?,n,case when n%3=0 then 'agent 고객' when n%3=1 then 'agnet 고객' else 'agnt 고객' end,
                       '보안 설치','2026-01-01','2026-01-01' from generate_series(1,12000) n
                """, upload);
        jdbc.update("""
                insert into work_maintenance_history(upload_id,row_no,maintenance_name,progress_issues,created_at,updated_at)
                select ?,n,case when n%3=0 then 'agent 고객' when n%3=1 then 'agnet 고객' else 'agnt 고객' end,
                       '보안 점검','2026-01-01','2026-01-01' from generate_series(1,12000) n
                """, upload);
        seedSearchIndex();
        jdbc.execute("alter table work_project_history enable trigger app_search_sync");
        jdbc.execute("alter table work_maintenance_history enable trigger app_search_sync");
        jdbc.execute("analyze app_search_document");
        jdbc.execute("analyze app_search_posting");
        jdbc.execute("analyze app_search_term");
        // PL/pgSQL이 연결별로 계획을 재사용한 뒤 발생하던 운영 환경의 회귀를 재현한다.
        jdbc.execute("set local plan_cache_mode = force_generic_plan");
        long started = System.nanoTime();
        var result = work.search(null, "agent", "보안", null,
                LocalDateTime.of(2026,1,1,0,0), LocalDateTime.of(2027,1,1,0,0), "ALL", 0, 7);
        double ms = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf("일반 실행계획 제목+본문 24000건: %.1f ms%n", ms);
        assertThat(result.projectTotal()).isEqualTo(12000);
        assertThat(result.maintenanceTotal()).isEqualTo(12000);
        assertThat(result.rows()).hasSize(7);
        assertThat(ms).isLessThan(5000);
    }

    private void seedSearchIndex() {
        // 등록 트리거는 일반 통합 테스트로 검증하고, 여기서는 같은 검색 데이터를 일괄 생성한다.
        jdbc.execute("""
                create temp table performance_fields on commit drop as
                select source_kind, document_id, field_kind,
                       lower(btrim(regexp_replace(coalesce(raw_text,''),'\\s+',' ','g'))) as normalized_text
                from (
                    select 'PROJECT' as source_kind,h.id as document_id,f.* from work_project_history h
                    cross join lateral (values ('TITLE',h.client_name),('CONTENT',h.scope),('AUX','')) f(field_kind,raw_text)
                    union all
                    select 'MAINTENANCE',h.id,f.* from work_maintenance_history h
                    cross join lateral (values ('TITLE',h.maintenance_name),('CONTENT',h.progress_issues),('AUX','')) f(field_kind,raw_text)
                    union all
                    select 'KNOWLEDGE',h.id,f.* from knowledge_share h
                    cross join lateral (values ('TITLE',h.title),('CONTENT',h.content),('AUX',concat_ws(' ',h.customer_name,h.author_name))) f(field_kind,raw_text)
                ) s
                """);
        jdbc.execute("""
                insert into app_search_document
                select source_kind,document_id,field_kind,normalized_text,replace(normalized_text,' ',''),app_search_chars(normalized_text)
                from performance_fields
                """);
        jdbc.execute("""
                create temp table performance_words on commit drop as
                select distinct f.source_kind,f.document_id,f.field_kind,w.word,md5(w.word) as word_hash
                from performance_fields f cross join lateral (
                    select regexp_split_to_table(f.normalized_text,'[^[:alnum:]가-힣]+') as word
                    union select replace(f.normalized_text,' ','')
                ) w where w.word <> ''
                """);
        jdbc.execute("""
                insert into app_search_term
                select word_hash,word,app_search_chars(word),length(word)
                from (select distinct word_hash,word from performance_words) w on conflict do nothing
                """);
        jdbc.execute("insert into app_search_posting select source_kind,document_id,field_kind,word_hash,length(word) from performance_words");
    }
}
