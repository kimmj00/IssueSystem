package com.example.issuesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 기존 검색 테이블은 유지하고, JAR에 포함된 검색 함수와 보조 인덱스를 한 번만 적용한다. */
@Component
public class SearchFunctionInitializer implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(SearchFunctionInitializer.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SearchFunctionInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            initialize();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("검색 함수 리소스를 읽지 못했습니다", e);
        }
    }

    private void initialize() throws IOException, NoSuchAlgorithmException {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equals(database)) return;
        String sql = new ClassPathResource("db/search-functions.sql").getContentAsString(StandardCharsets.UTF_8);
        String version = "issue-system-search:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
        Boolean needsMaintenance = transaction.execute(status -> {
            // 여러 앱이 동시에 기동해도 함수 교체를 한 번만 수행한다.
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(current_database() || ':' || current_schema() || ':issue-search', 0))");
            String installed = jdbc.queryForObject("select obj_description(to_regprocedure('app_search_rank(text,text,text,text,bigint[])'), 'pg_proc')", String.class);
            if (version.equals(installed)) return false;
            boolean missingMetadata = !Boolean.TRUE.equals(jdbc.queryForObject("""
                    select count(*)=2 and coalesce(bool_and(attnotnull),false)
                    from pg_attribute where attrelid='app_search_posting'::regclass
                      and attname in ('word_length','char_signature') and not attisdropped
                    """, Boolean.class));
            long started = System.nanoTime();
            log.info("검색 구조 업데이트 시작: {} (최초 적용 시 단어 연결의 길이·글자 정보와 인덱스를 준비합니다)", version);
            jdbc.execute(sql);
            jdbc.execute("comment on function app_search_rank(text,text,text,text,bigint[]) is '" + version + "'");
            log.info("검색 함수 업데이트 완료: {}, elapsedMs={}", version, (System.nanoTime() - started) / 1_000_000);
            return missingMetadata;
        });
        if (Boolean.TRUE.equals(needsMaintenance)) {
            // 갱신 트랜잭션이 끝난 뒤 정리하여 새 인덱스가 원문 테이블을 다시 읽지 않게 한다.
            log.info("검색 인덱스 정리 시작");
            try {
                jdbc.execute("vacuum (analyze, parallel 0) app_search_posting");
                log.info("검색 인덱스 정리 완료");
            } catch (DataAccessException e) {
                // 검색 구조는 이미 적용됐다. 권한/접속 환경에 따른 정리 실패로 검색을 막지 않는다.
                log.warn("검색 인덱스 정리 미완료: {}", e.getMostSpecificCause().getMessage());
            }
        }
        var engine = jdbc.queryForMap("""
                select current_database() as database_name, current_schema() as schema_name,
                       current_setting('server_version') as postgres_version,
                       obj_description(to_regprocedure('app_search_rank(text,text,text,text,bigint[])'), 'pg_proc') as version,
                       coalesce((select indisvalid from pg_index
                           where indexrelid=to_regclass('idx_app_search_posting_scope_terms')),false) as scope_index_ready
                """);
        if (!Boolean.TRUE.equals(engine.get("scope_index_ready"))) {
            throw new IllegalStateException("검색 범위 인덱스가 준비되지 않았습니다: idx_app_search_posting_scope_terms");
        }
        log.info("검색 엔진 준비: database={}, schema={}, PostgreSQL={}, version={}, scopeIndexReady={}",
                engine.get("database_name"), engine.get("schema_name"), engine.get("postgres_version"), engine.get("version"),
                engine.get("scope_index_ready"));
    }
}
