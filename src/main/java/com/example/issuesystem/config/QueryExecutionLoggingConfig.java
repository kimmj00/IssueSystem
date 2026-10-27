package com.example.issuesystem.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(QueryExecutionLoggingConfig.QueryLoggingProperties.class)
public class QueryExecutionLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionLoggingConfig.class);

    @Bean
    public static BeanPostProcessor queryExecutionLoggingDataSourceBeanPostProcessor(
            ObjectProvider<QueryLoggingProperties> propertiesProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof DataSource dataSource) || bean instanceof QueryLoggingProxyMarker) {
                    return bean;
                }
                // 후처리기 등록 중 설정 빈이 먼저 생성되지 않도록 DataSource 처리 시점에 조회한다.
                QueryLoggingProperties properties = propertiesProvider.getObject();
                if (!properties.isEnabled()) {
                    return bean;
                }

                return Proxy.newProxyInstance(
                        bean.getClass().getClassLoader(),
                        collectProxyInterfaces(bean.getClass(), DataSource.class, QueryLoggingProxyMarker.class),
                        new DataSourceInvocationHandler(dataSource, beanName, properties)
                );
            }
        };
    }

    public interface QueryLoggingProxyMarker {
    }

    @ConfigurationProperties(prefix = "app.logging.query")
    public static class QueryLoggingProperties {
        private boolean enabled = true;
        private boolean logAll = false;
        private long slowThresholdMs = 1000L;
        private boolean includeSql = false;
        private int maxSqlLength = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogAll() {
            return logAll;
        }

        public void setLogAll(boolean logAll) {
            this.logAll = logAll;
        }

        public long getSlowThresholdMs() {
            return slowThresholdMs;
        }

        public void setSlowThresholdMs(long slowThresholdMs) {
            this.slowThresholdMs = slowThresholdMs;
        }

        public boolean isIncludeSql() {
            return includeSql;
        }

        public void setIncludeSql(boolean includeSql) {
            this.includeSql = includeSql;
        }

        public int getMaxSqlLength() {
            return maxSqlLength;
        }

        public void setMaxSqlLength(int maxSqlLength) {
            this.maxSqlLength = maxSqlLength;
        }
    }

    private static class DataSourceInvocationHandler implements InvocationHandler {
        private final DataSource target;
        private final String beanName;
        private final QueryLoggingProperties properties;

        private DataSourceInvocationHandler(DataSource target, String beanName, QueryLoggingProperties properties) {
            this.target = target;
            this.beanName = beanName;
            this.properties = properties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isObjectMethod(method)) {
                return invokeObjectMethod(proxy, method, args, target);
            }

            if (isWrapperMethod(method, args, target)) {
                return handleWrapperMethod(method, args, target);
            }

            Object result = invokeTarget(target, method, args);
            if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                return wrapConnection(connection, beanName, properties);
            }
            return result;
        }
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {
        private final Connection target;
        private final String dataSourceName;
        private final QueryLoggingProperties properties;

        private ConnectionInvocationHandler(Connection target, String dataSourceName, QueryLoggingProperties properties) {
            this.target = target;
            this.dataSourceName = dataSourceName;
            this.properties = properties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isObjectMethod(method)) {
                return invokeObjectMethod(proxy, method, args, target);
            }

            if (isWrapperMethod(method, args, target)) {
                return handleWrapperMethod(method, args, target);
            }

            Object result = invokeTarget(target, method, args);
            if (result instanceof Statement statement && shouldWrapStatement(method)) {
                String sql = firstStringArgument(args);
                return wrapStatement(statement, sql, dataSourceName, properties);
            }
            return result;
        }

        private boolean shouldWrapStatement(Method method) {
            return "prepareStatement".equals(method.getName())
                    || "prepareCall".equals(method.getName())
                    || "createStatement".equals(method.getName());
        }
    }

    private static class StatementInvocationHandler implements InvocationHandler {
        private static final Set<String> EXECUTE_METHOD_NAMES = Set.of(
                "execute",
                "executeQuery",
                "executeUpdate",
                "executeLargeUpdate",
                "executeBatch",
                "executeLargeBatch"
        );

        private final Statement target;
        private final String preparedSql;
        private final String dataSourceName;
        private final QueryLoggingProperties properties;

        private StatementInvocationHandler(Statement target, String preparedSql, String dataSourceName, QueryLoggingProperties properties) {
            this.target = target;
            this.preparedSql = preparedSql;
            this.dataSourceName = dataSourceName;
            this.properties = properties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isObjectMethod(method)) {
                return invokeObjectMethod(proxy, method, args, target);
            }

            if (isWrapperMethod(method, args, target)) {
                return handleWrapperMethod(method, args, target);
            }

            if (!EXECUTE_METHOD_NAMES.contains(method.getName())) {
                return invokeTarget(target, method, args);
            }

            String sql = resolveSql(args);
            long startedAt = System.nanoTime();
            try {
                Object result = invokeTarget(target, method, args);
                logSuccess(method, System.nanoTime() - startedAt, result, sql);
                return result;
            } catch (Throwable error) {
                logFailure(method, System.nanoTime() - startedAt, error, sql);
                throw error;
            }
        }

        private String resolveSql(Object[] args) {
            String sql = firstStringArgument(args);
            return sql != null ? sql : preparedSql;
        }

        private void logSuccess(Method method, long elapsedNanos, Object result, String sql) {
            boolean slow = isSlow(elapsedNanos, properties);
            if (!properties.isLogAll() && !slow) {
                return;
            }

            Object[] logArgs = {
                    dataSourceName,
                    method.getName(),
                    formatElapsedMs(elapsedNanos),
                    summarizeResult(result),
                    queryName(sql),
                    sqlId(sql),
                    sqlDetail(sql, properties, slow)
            };

            if (slow) {
                log.warn("DB query slow: dataSource={}, method={}, elapsedMs={}, result={}, queryName={}, sqlId={}{}", logArgs);
            } else {
                log.info("DB query executed: dataSource={}, method={}, elapsedMs={}, result={}, queryName={}, sqlId={}{}", logArgs);
            }
        }

        private void logFailure(Method method, long elapsedNanos, Throwable error, String sql) {
            log.error(
                    "DB query failed: dataSource={}, method={}, elapsedMs={}, reason={}, queryName={}, sqlId={}{}",
                    dataSourceName,
                    method.getName(),
                    formatElapsedMs(elapsedNanos),
                    errorSummary(error),
                    queryName(sql),
                    sqlId(sql),
                    sqlDetail(sql, properties, true),
                    error
            );
        }
    }

    private static Connection wrapConnection(Connection connection, String dataSourceName, QueryLoggingProperties properties) {
        if (connection instanceof QueryLoggingProxyMarker) {
            return connection;
        }

        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                collectProxyInterfaces(connection.getClass(), Connection.class, QueryLoggingProxyMarker.class),
                new ConnectionInvocationHandler(connection, dataSourceName, properties)
        );
    }

    private static Statement wrapStatement(Statement statement, String sql, String dataSourceName, QueryLoggingProperties properties) {
        if (statement instanceof QueryLoggingProxyMarker) {
            return statement;
        }

        Class<?> statementInterface = Statement.class;
        if (statement instanceof CallableStatement) {
            statementInterface = CallableStatement.class;
        } else if (statement instanceof PreparedStatement) {
            statementInterface = PreparedStatement.class;
        }

        return (Statement) Proxy.newProxyInstance(
                statement.getClass().getClassLoader(),
                collectProxyInterfaces(statement.getClass(), statementInterface, QueryLoggingProxyMarker.class),
                new StatementInvocationHandler(statement, sql, dataSourceName, properties)
        );
    }

    private static Class<?>[] collectProxyInterfaces(Class<?> type, Class<?> requiredInterface, Class<?> markerInterface) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Arrays.stream(current.getInterfaces())
                    .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                    .forEach(interfaces::add);
        }
        interfaces.add(requiredInterface);
        interfaces.add(markerInterface);
        return interfaces.toArray(Class<?>[]::new);
    }

    private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private static boolean isObjectMethod(Method method) {
        return Object.class.equals(method.getDeclaringClass());
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args, Object target) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "QueryLoggingProxy{" + target + '}';
            default -> throw new IllegalStateException("Unsupported Object method: " + method.getName());
        };
    }

    private static boolean isWrapperMethod(Method method, Object[] args, Object target) {
        return ("unwrap".equals(method.getName()) || "isWrapperFor".equals(method.getName()))
                && args != null
                && args.length == 1
                && args[0] instanceof Class<?>
                && target != null;
    }

    private static Object handleWrapperMethod(Method method, Object[] args, Object target) throws Throwable {
        Class<?> type = (Class<?>) args[0];
        if ("unwrap".equals(method.getName()) && type.isInstance(target)) {
            return type.cast(target);
        }
        if ("isWrapperFor".equals(method.getName()) && type.isInstance(target)) {
            return true;
        }
        return invokeTarget(target, method, args);
    }

    private static String firstStringArgument(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String sql)) {
            return null;
        }
        return sql;
    }

    private static boolean isSlow(long elapsedNanos, QueryLoggingProperties properties) {
        long thresholdMs = Math.max(0L, properties.getSlowThresholdMs());
        return elapsedNanos >= TimeUnit.MILLISECONDS.toNanos(thresholdMs);
    }

    private static String formatElapsedMs(long elapsedNanos) {
        return String.format(Locale.ROOT, "%.2f", elapsedNanos / 1_000_000.0d);
    }

    private static String summarizeResult(Object result) {
        if (result instanceof int[] updates) {
            return "batchSize=" + updates.length + ", affectedRows=" + affectedRows(updates);
        }
        if (result instanceof long[] updates) {
            return "batchSize=" + updates.length + ", affectedRows=" + affectedRows(updates);
        }
        if (result instanceof Number number) {
            return "affectedRows=" + number;
        }
        if (result instanceof Boolean hasResultSet) {
            return "resultSet=" + hasResultSet;
        }
        if (result instanceof ResultSet) {
            return "resultSet";
        }
        return result == null ? "none" : result.getClass().getSimpleName();
    }

    private static String affectedRows(int[] updates) {
        long total = 0L;
        boolean hasUnknown = false;
        for (int update : updates) {
            if (update >= 0) {
                total += update;
            } else {
                hasUnknown = true;
            }
        }
        return hasUnknown ? total + "+" : Long.toString(total);
    }

    private static String affectedRows(long[] updates) {
        long total = 0L;
        boolean hasUnknown = false;
        for (long update : updates) {
            if (update >= 0L) {
                total += update;
            } else {
                hasUnknown = true;
            }
        }
        return hasUnknown ? total + "+" : Long.toString(total);
    }

    private static String sqlId(String sql) {
        String normalizedSql = normalizeSql(sql);
        if (normalizedSql.isBlank()) {
            return "none";
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalizedSql.hashCode());
        }
    }

    private static String sqlDetail(String sql, QueryLoggingProperties properties, boolean forceInclude) {
        if (!forceInclude && !properties.isIncludeSql()) {
            return "";
        }

        String normalizedSql = normalizeSql(sql);
        if (normalizedSql.isBlank()) {
            return "";
        }

        int maxLength = Math.max(50, properties.getMaxSqlLength());
        if (normalizedSql.length() > maxLength) {
            normalizedSql = normalizedSql.substring(0, maxLength) + "...";
        }
        return ", sql=" + normalizedSql;
    }

    private static String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.trim().replaceAll("\\s+", " ");
    }

    private static String queryName(String sql) {
        String normalizedSql = normalizeSql(sql);
        if (!normalizedSql.startsWith("/*")) {
            return "none";
        }

        int endIndex = normalizedSql.indexOf("*/");
        if (endIndex < 0) {
            return "none";
        }

        String comment = normalizedSql.substring(2, endIndex).trim();
        return comment.isBlank() ? "none" : oneLine(comment);
    }

    private static String errorSummary(Throwable error) {
        SQLException sqlException = findSqlException(error);
        if (sqlException != null) {
            SQLException nextException = sqlException.getNextException();
            String nextMessage = nextException == null ? "" : ", next=" + oneLine(nextException.getMessage());
            return sqlException.getClass().getSimpleName()
                    + "(sqlState=" + sqlException.getSQLState()
                    + ", errorCode=" + sqlException.getErrorCode()
                    + "): " + oneLine(sqlException.getMessage())
                    + nextMessage;
        }

        Throwable root = rootCause(error);
        return root.getClass().getSimpleName() + ": " + oneLine(root.getMessage());
    }

    private static SQLException findSqlException(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String oneLine(String message) {
        if (message == null || message.isBlank()) {
            return "no message";
        }

        String oneLineMessage = message.replaceAll("\\s+", " ").trim();
        return oneLineMessage.length() > 500 ? oneLineMessage.substring(0, 500) + "..." : oneLineMessage;
    }
}
