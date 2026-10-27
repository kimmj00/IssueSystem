package com.example.issuesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 기존 검색 테이블은 유지하고, 배포한 JAR에 포함된 함수 버전을 한 번만 적용한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchFunctionInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SearchFunctionInitializer.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SearchFunctionInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equals(database)) return;
        String sql = new ClassPathResource("db/search-functions.sql").getContentAsString(StandardCharsets.UTF_8);
        String version = "issue-system-search:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
        transaction.executeWithoutResult(status -> {
            // 여러 앱이 동시에 기동해도 함수 교체를 한 번만 수행한다.
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(current_database() || ':' || current_schema() || ':issue-search', 0))");
            String installed = jdbc.queryForObject("select obj_description(to_regprocedure('app_search_rank(text,text,text,text,bigint[])'), 'pg_proc')", String.class);
            if (version.equals(installed)) return;
            jdbc.execute(sql);
            jdbc.execute("comment on function app_search_rank(text,text,text,text,bigint[]) is '" + version + "'");
            log.info("검색 함수 업데이트 완료: {}", version);
        });
    }
}
