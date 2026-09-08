package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = false)
@DisplayName("健康证迁移 MySQL 5.7 发布门禁")
class HrHealthCertificateMySqlIT
{
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
            .withDatabaseName("hr_health_certificate_it")
            .withUsername("health_it")
            .withPassword("health_it_password")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci");

    @BeforeAll
    static void migrateTwice() throws Exception
    {
        try (Connection connection = connection())
        {
            executeScript(connection, resource(
                    "hr-health-certificate-it-schema.sql"));
            String migration = Files.readString(migration(
                    "erp_hr_health_certificate_20260713.sql"),
                    StandardCharsets.UTF_8);
            executeScript(connection, migration);
            int firstMenuCount = count(connection,
                    "select count(*) from sys_menu");
            int firstRoleMenuCount = count(connection,
                    "select count(*) from sys_role_menu");

            executeScript(connection, migration);

            assertThat(count(connection, "select count(*) from sys_menu"))
                    .isEqualTo(firstMenuCount);
            assertThat(count(connection,
                    "select count(*) from sys_role_menu"))
                    .isEqualTo(firstRoleMenuCount);
        }
    }

    @Test
    void migrationDefaultsOffAndCreatesSingleConfigRows() throws Exception
    {
        try (Connection connection = connection())
        {
            assertThat(value(connection,
                    "select config_value from sys_config "
                            + "where config_key="
                            + "'feature.hr.health-certificate.enabled'"))
                    .isEqualTo("false");
            assertThat(count(connection,
                    "select count(*) from sys_config where config_key in ("
                            + "'feature.hr.health-certificate.enabled',"
                            + "'todo.health-certificate.warning-days')"))
                    .isEqualTo(2);
            assertThat(count(connection,
                    "select count(distinct index_name) "
                            + "from information_schema.statistics "
                            + "where table_schema=database() and table_name="
                            + "'hr_employee_health_certificate' and index_name="
                            + "'uk_hr_health_user_current'"))
                    .isEqualTo(1);
            assertThat(value(connection,
                    "select group_concat(column_name order by seq_in_index) "
                            + "from information_schema.statistics "
                            + "where table_schema=database() and table_name="
                            + "'hr_employee_health_certificate' and index_name="
                            + "'uk_hr_health_user_current'"))
                    .isEqualTo("user_id,current_flag");
        }
    }

    @Test
    void exactlyOneConcurrentCurrentCertificateWins() throws Exception
    {
        try (Connection connection = connection();
                Statement statement = connection.createStatement())
        {
            statement.executeUpdate(
                    "delete from hr_employee_health_certificate "
                            + "where user_id=7001");
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            Future<Boolean> first = pool.submit(() -> insertCurrent(start,
                    "HC-7001-A"));
            Future<Boolean> second = pool.submit(() -> insertCurrent(start,
                    "HC-7001-B"));
            start.countDown();

            int winners = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(winners).isEqualTo(1);
            try (Connection connection = connection())
            {
                assertThat(count(connection,
                        "select count(*) from "
                                + "hr_employee_health_certificate "
                                + "where user_id=7001 and current_flag='Y'"))
                        .isEqualTo(1);
            }
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseIsRealMySql5744() throws Exception
    {
        try (Connection connection = connection())
        {
            assertThat(value(connection, "select version()"))
                    .startsWith("5.7.44");
        }
    }

    private static boolean insertCurrent(CountDownLatch start,
            String certificateNo) throws Exception
    {
        start.await(5, TimeUnit.SECONDS);
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into hr_employee_health_certificate "
                                + "(user_id, certificate_no, issued_date, "
                                + "expires_on, review_status, current_flag) "
                                + "values (7001, ?, '2026-07-01', "
                                + "'2027-06-30', 'APPROVED', 'Y')"))
        {
            statement.setString(1, certificateNo);
            statement.executeUpdate();
            return true;
        }
        catch (SQLException exception)
        {
            if ("23000".equals(exception.getSQLState()))
            {
                return false;
            }
            throw exception;
        }
    }

    private static Connection connection() throws SQLException
    {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static int count(Connection connection, String sql)
            throws SQLException
    {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getInt(1);
        }
    }

    private static String value(Connection connection, String sql)
            throws SQLException
    {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql))
        {
            result.next();
            return result.getString(1);
        }
    }

    private static String resource(String name) throws IOException
    {
        try (var input = HrHealthCertificateMySqlIT.class.getClassLoader()
                .getResourceAsStream(name))
        {
            if (input == null)
            {
                throw new IOException("missing test resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path migration(String name)
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve("sql").resolve(name);
        if (!Files.exists(path))
        {
            path = root.resolve("../../sql").resolve(name).normalize();
        }
        if (!Files.exists(path))
        {
            throw new IllegalStateException("missing migration: " + path);
        }
        return path;
    }

    private static void executeScript(Connection connection, String sql)
            throws SQLException
    {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Statement executor = connection.createStatement())
        {
            for (String line : sql.split("\\R", -1))
            {
                String trimmed = line.trim();
                if (trimmed.toUpperCase().startsWith("DELIMITER "))
                {
                    delimiter = trimmed.substring("DELIMITER ".length())
                            .trim();
                    continue;
                }
                statement.append(line).append('\n');
                int end;
                while ((end = statement.indexOf(delimiter)) >= 0)
                {
                    String command = statement.substring(0, end).trim();
                    statement.delete(0, end + delimiter.length());
                    if (!command.isEmpty())
                    {
                        executor.execute(command);
                    }
                }
            }
            if (!statement.toString().trim().isEmpty())
            {
                executor.execute(statement.toString());
            }
        }
    }
}
