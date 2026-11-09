package com.example.issuesystem.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class QueryExecutionLoggingConfigTest {
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @Test
    void 검색_진단만_기록하고_다른_경고와_파라미터는_노출하지_않는다(CapturedOutput output) throws Exception {
        var warning = new SQLWarning("로그에 남기지 않을 다른 경고");
        warning.setNextWarning(new SQLWarning("issue_search_metrics {\"source\":\"PATCH\",\"elapsedMs\":1234}"));
        when(statement.getWarnings()).thenReturn(warning);
        var result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        var wrapped = wrap("/* SearchPageExecutor.PATCH */ select * from app_search_rank(?,null,null,null,null)");
        wrapped.setString(1, "원문 검색어 비밀");
        assertThat(wrapped.executeQuery()).isSameAs(result);
        assertThat(output).contains("검색 엔진 지연:", "SearchPageExecutor.PATCH", "\"elapsedMs\":1234")
                .doesNotContain("로그에 남기지 않을 다른 경고", "원문 검색어 비밀");
    }

    @Test
    void 진단을_읽지_못해도_검색_결과는_반환한다() throws Exception {
        var result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(statement.getWarnings()).thenThrow(new SQLException("warning unavailable"));
        assertThat(wrap("select * from app_search_candidates('PATCH','CONTENT',?)").executeQuery()).isSameAs(result);
    }

    @Test
    void 일반_쿼리는_검색_진단을_읽지_않는다() throws Exception {
        wrap("select 1").executeQuery();
        verify(statement, never()).getWarnings();
    }

    @Test
    void DB_실패는_원래_예외를_유지한다() throws Exception {
        var failure = new SQLException("test failure", "08006");
        when(statement.executeQuery()).thenThrow(failure);
        var wrapped = wrap("select * from app_search_rank('PATCH',null,null,null,null)");
        assertThatThrownBy(wrapped::executeQuery).isSameAs(failure);
        verify(statement, never()).getWarnings();
    }

    private PreparedStatement wrap(String sql) throws Exception {
        var properties = new QueryExecutionLoggingConfig.QueryLoggingProperties();
        var factory = new StaticListableBeanFactory(Map.of("properties", properties));
        var postProcessor = QueryExecutionLoggingConfig.queryExecutionLoggingDataSourceBeanPostProcessor(
                factory.getBeanProvider(QueryExecutionLoggingConfig.QueryLoggingProperties.class));
        var connection = mock(Connection.class);
        var source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(sql)).thenReturn(statement);
        return ((DataSource) postProcessor.postProcessAfterInitialization(source, "testDataSource"))
                .getConnection().prepareStatement(sql);
    }
}
