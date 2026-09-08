package com.erp.system.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 原生 MySQL 集成测试隔离库支持。
 *
 * <p>只创建并删除当前测试生成的 {@code erp_it_} 数据库；默认拒绝远端地址，
 * 远端非生产实例必须通过环境变量显式确认。</p>
 */
public final class NativeMySqlIntegrationTestSupport implements AutoCloseable
{
    private static final String DATABASE_PREFIX = "erp_it_";

    private static final Pattern SAFE_HOST = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z0-9_]+$");

    private static final Set<String> RESERVED_DATABASES = Set.of(
            "information_schema", "mysql", "performance_schema", "sys", "erp", "erp_prod", "ry_cloud");

    private final String host;

    private final int port;

    private final String username;

    private final String password;

    private final String databaseName;

    private final String serverJdbcUrl;

    private final String jdbcUrl;

    private final String propertyPrefix;

    private final String serverVersion;

    private boolean closed;

    private NativeMySqlIntegrationTestSupport(String suiteName, String propertyPrefix)
    {
        this.host = requireEnvironment("ERP_IT_MYSQL_HOST");
        this.port = parsePort(environmentOrDefault("ERP_IT_MYSQL_PORT", "3306"));
        this.username = requireEnvironment("ERP_IT_MYSQL_USER");
        this.password = System.getenv().getOrDefault("ERP_IT_MYSQL_PASSWORD", "");
        this.propertyPrefix = requirePropertyPrefix(propertyPrefix);
        validateHost(host, System.getenv("ERP_IT_ALLOW_REMOTE_NONPROD"));

        String runId = sanitizeIdentifier(environmentOrDefault("ERP_IT_RUN_ID",
                "manual_" + randomSuffix(8)), 20, "run id");
        String suite = sanitizeIdentifier(suiteName, 20, "suite name");
        this.databaseName = DATABASE_PREFIX + runId + "_" + suite + "_" + randomSuffix(8);
        validateDatabaseName(databaseName);

        String jdbcHost = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        String options = "useUnicode=true&characterEncoding=UTF-8&useSSL=false"
                + "&allowPublicKeyRetrieval=true&serverTimezone=Asia%2FShanghai";
        this.serverJdbcUrl = "jdbc:mysql://" + jdbcHost + ":" + port + "/?" + options;
        this.jdbcUrl = "jdbc:mysql://" + jdbcHost + ":" + port + "/" + databaseName + "?" + options;

        String detectedVersion = null;
        boolean databaseCreated = false;
        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, username, password);
                Statement statement = connection.createStatement())
        {
            try (ResultSet result = statement.executeQuery("SELECT VERSION()"))
            {
                if (!result.next())
                {
                    throw new IllegalStateException("原生 MySQL 未返回版本信息");
                }
                detectedVersion = result.getString(1);
            }
            validateExpectedVersion(detectedVersion, System.getenv("ERP_IT_EXPECTED_MYSQL_VERSION_PREFIX"));
            statement.executeUpdate("CREATE DATABASE `" + databaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            databaseCreated = true;
        }
        catch (SQLException | RuntimeException e)
        {
            if (databaseCreated)
            {
                dropDatabaseQuietly();
            }
            throw new IllegalStateException("无法为原生 MySQL 集成测试创建隔离库（host="
                    + host + ":" + port + "）", e);
        }
        this.serverVersion = detectedVersion;

        System.setProperty(propertyPrefix + ".jdbc-url", jdbcUrl);
        System.setProperty(propertyPrefix + ".username", username);
        System.setProperty(propertyPrefix + ".password", password);
        System.out.printf("[native-mysql-it] suite=%s version=%s database=%s host=%s:%d%n",
                suite, serverVersion, databaseName, host, port);
    }

    public static NativeMySqlIntegrationTestSupport create(String suiteName, String propertyPrefix)
    {
        return new NativeMySqlIntegrationTestSupport(suiteName, propertyPrefix);
    }

    public String getJdbcUrl()
    {
        return jdbcUrl;
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getDatabaseName()
    {
        return databaseName;
    }

    public String getServerVersion()
    {
        return serverVersion;
    }

    @Override
    public synchronized void close()
    {
        if (closed)
        {
            return;
        }
        validateDatabaseName(databaseName);
        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, username, password);
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
            closed = true;
            System.out.printf("[native-mysql-it] cleaned database=%s%n", databaseName);
        }
        catch (SQLException e)
        {
            throw new IllegalStateException("无法清理原生 MySQL 集成测试隔离库 " + databaseName, e);
        }
    }

    static boolean isLocalHost(String value)
    {
        return "127.0.0.1".equals(value) || "localhost".equalsIgnoreCase(value) || "::1".equals(value);
    }

    static void validateDatabaseName(String value)
    {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(DATABASE_PREFIX) || normalized.length() <= DATABASE_PREFIX.length()
                || normalized.length() > 64 || !SAFE_IDENTIFIER.matcher(normalized).matches()
                || RESERVED_DATABASES.contains(normalized))
        {
            throw new IllegalArgumentException("拒绝操作非隔离测试数据库: " + value);
        }
    }

    private void dropDatabaseQuietly()
    {
        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, username, password);
                Statement statement = connection.createStatement())
        {
            validateDatabaseName(databaseName);
            statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
        }
        catch (Exception ignored)
        {
            // 构造失败时保留原始异常；外层脚本仍会按本次 run id 兜底清理。
        }
    }

    private static void validateHost(String value, String remoteConfirmation)
    {
        if (!SAFE_HOST.matcher(value).matches())
        {
            throw new IllegalArgumentException("MySQL 主机格式无效");
        }
        if (!isLocalHost(value) && !"1".equals(remoteConfirmation))
        {
            throw new IllegalArgumentException(
                    "默认拒绝远端 MySQL；确认是非生产实例后设置 ERP_IT_ALLOW_REMOTE_NONPROD=1");
        }
    }

    private static void validateExpectedVersion(String actual, String expectedPrefix)
    {
        if (expectedPrefix != null && !expectedPrefix.isBlank() && !actual.startsWith(expectedPrefix))
        {
            throw new IllegalStateException("MySQL 版本不符合门禁，期望前缀=" + expectedPrefix + "，实际=" + actual);
        }
    }

    private static int parsePort(String value)
    {
        try
        {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65535)
            {
                throw new IllegalArgumentException("MySQL 端口超出范围");
            }
            return parsed;
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("MySQL 端口必须是数字", e);
        }
    }

    private static String requireEnvironment(String name)
    {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("缺少原生 MySQL 集成测试环境变量: " + name);
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requirePropertyPrefix(String value)
    {
        if (value == null || !value.matches("^[a-z][a-z0-9.]+$"))
        {
            throw new IllegalArgumentException("Spring 属性前缀无效");
        }
        return value;
    }

    private static String sanitizeIdentifier(String value, int maxLength, String label)
    {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private static String randomSuffix(int length)
    {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
