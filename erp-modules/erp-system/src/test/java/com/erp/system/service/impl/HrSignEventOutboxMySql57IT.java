package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.erp.system.testsupport.MySqlTransactionTestDatabase;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.ClassPathResource;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.SysHrSignEventOutbox;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;

@ActiveProfiles("hr-sign-outbox-it")
@SpringBootTest(classes = HrSignEventOutboxMySql57IT.ItConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.discovery.enabled=false" })
@DisplayName("HR签约事件 Outbox MySQL 5.7")
class HrSignEventOutboxMySql57IT
{
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @RegisterExtension
    static final MySqlTransactionTestDatabase MYSQL =
            new MySqlTransactionTestDatabase("sign_outbox", "hr.sign.outbox.it");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry)
    {
        registry.add("hr.sign.outbox.it.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("hr.sign.outbox.it.username", MYSQL::getUsername);
        registry.add("hr.sign.outbox.it.password", MYSQL::getPassword);
    }

    @Autowired
    private SysHrSignEventOutboxMapper outboxMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HrSignEventPayloadFactory payloadFactory;

    @BeforeAll
    static void migrate() throws Exception
    {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            executeScript(connection, resource("hr-transfer-it-schema.sql"));
            executeScript(connection, Files.readString(migration(
                    "erp_hr_lifecycle_action_20260711.sql"),
                    StandardCharsets.UTF_8));
        }
    }

    @BeforeEach
    void clean()
    {
        jdbc.execute("delete from sys_hr_sign_event_outbox");
        jdbc.execute("delete from sys_hr_lifecycle_action");
    }

    @Test
    void twoConcurrentClaimsHaveExactlyOneWinner() throws Exception
    {
        SysHrSignEventOutbox row = insertRow(101L, "PENDING", 0, null, 0L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            Future<Integer> first = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return outboxMapper.claimForSending(row.getOutboxId(),
                        "PENDING", 0L);
            });
            Future<Integer> second = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return outboxMapper.claimForSending(row.getOutboxId(),
                        "PENDING", 0L);
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)
                    + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select concat(status,':',version) "
                            + "from sys_hr_sign_event_outbox where outbox_id=?",
                    String.class, row.getOutboxId()))
                    .isEqualTo("SENDING:1");
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void onlyClaimedVersionCanMarkSent()
    {
        SysHrSignEventOutbox row = insertRow(102L, "PENDING", 0, null, 0L);
        assertThat(outboxMapper.claimForSending(row.getOutboxId(),
                "PENDING", 0L)).isOne();

        assertThat(outboxMapper.markSent(row.getOutboxId(), 0L, 701L, 200))
                .isZero();
        assertThat(outboxMapper.markSent(row.getOutboxId(), 1L, 701L, 200))
                .isOne();
        assertThat(jdbc.queryForObject(
                "select concat(status,':',version,':',remote_task_id) "
                        + "from sys_hr_sign_event_outbox where outbox_id=?",
                String.class, row.getOutboxId()))
                .isEqualTo("SENT:2:701");
    }

    @Test
    void onlySendingOlderThanFiveMinutesIsDue()
    {
        SysHrSignEventOutbox stale = insertRow(103L, "SENDING", 0, null, 1L);
        SysHrSignEventOutbox recent = insertRow(104L, "SENDING", 0, null, 1L);
        jdbc.update("update sys_hr_sign_event_outbox set update_time=? "
                        + "where outbox_id=?",
                Date.from(NOW.minusSeconds(301)), stale.getOutboxId());
        jdbc.update("update sys_hr_sign_event_outbox set update_time=? "
                        + "where outbox_id=?",
                Date.from(NOW.minusSeconds(299)), recent.getOutboxId());

        List<SysHrSignEventOutbox> due = outboxMapper.selectDueOutboxes(
                Date.from(NOW), Date.from(NOW.minusSeconds(300)), 100);

        assertThat(due).extracting(SysHrSignEventOutbox::getOutboxId)
                .containsExactly(stale.getOutboxId());
    }

    @Test
    void uniqueActionVersionAndCompensationReturnCanonicalRow()
    {
        SysHrSignEventOutbox first = insertRow(105L, "PENDING", 0, null, 0L);
        SysHrSignEventOutbox duplicate = row(105L, "PENDING", 0, null, 0L);

        assertThatThrownBy(() -> outboxMapper.insertOutbox(duplicate))
                .isInstanceOf(DuplicateKeyException.class);

        HrSignEventCompensationService service =
                new HrSignEventCompensationService(outboxMapper,
                        payloadFactory);
        SysHrSignEventOutbox canonical = service.ensureOutbox(
                action(105L));
        assertThat(canonical.getOutboxId()).isEqualTo(first.getOutboxId());
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_hr_sign_event_outbox "
                        + "where action_id=105 and event_version=1",
                Integer.class)).isOne();
    }

    @Test
    void dueRowsUseRetryOrCreateTimeThenOutboxIdOrdering()
    {
        SysHrSignEventOutbox first = insertRow(106L, "PENDING", 0, null, 0L);
        SysHrSignEventOutbox third = insertRow(107L, "RETRY", 1,
                Date.from(NOW.minusSeconds(60)), 2L);
        SysHrSignEventOutbox second = insertRow(108L, "RETRY", 1,
                Date.from(NOW.minusSeconds(120)), 2L);
        jdbc.update("update sys_hr_sign_event_outbox set create_time=? "
                        + "where outbox_id=?",
                Date.from(NOW.minusSeconds(180)), first.getOutboxId());

        List<SysHrSignEventOutbox> due = outboxMapper.selectDueOutboxes(
                Date.from(NOW), Date.from(NOW.minusSeconds(300)), 100);

        assertThat(due).extracting(SysHrSignEventOutbox::getOutboxId)
                .containsExactly(first.getOutboxId(), second.getOutboxId(),
                        third.getOutboxId());
    }

    @Test
    void databaseUsesConfiguredMySqlVersion()
    {
        assertThat(jdbc.queryForObject("select version()", String.class))
                .startsWith(MYSQL.getExpectedVersionPrefix());
    }

    private SysHrSignEventOutbox insertRow(Long actionId, String status,
            int retryCount, Date nextRetryTime, Long version)
    {
        SysHrSignEventOutbox row = row(actionId, status, retryCount,
                nextRetryTime, version);
        assertThat(outboxMapper.insertOutbox(row)).isOne();
        assertThat(row.getOutboxId()).isPositive();
        return row;
    }

    private SysHrSignEventOutbox row(Long actionId, String status,
            int retryCount, Date nextRetryTime, Long version)
    {
        SysHrSignEventOutbox row = new SysHrSignEventOutbox();
        row.setActionId(actionId);
        row.setEventVersion(1L);
        row.setPayloadJson("{}");
        row.setStatus(status);
        row.setRetryCount(retryCount);
        row.setNextRetryTime(nextRetryTime);
        row.setVersion(version);
        return row;
    }

    private SysHrLifecycleAction action(Long actionId)
    {
        HrEmployeeSigningSnapshot snapshot = new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(7L);
        snapshot.setEntryDate(LocalDate.of(2026, 7, 13));
        SysHrLifecycleAction action = new SysHrLifecycleAction();
        action.setActionId(actionId);
        action.setActionType("ONBOARD_CONFIRMED");
        action.setEmployeeId(7L);
        action.setVersion(1L);
        action.setActualConfirmTime(Date.from(NOW));
        try
        {
            action.setBeforeSnapshotJson(
                    objectMapper.writeValueAsString(snapshot));
            action.setAfterSnapshotJson(
                    objectMapper.writeValueAsString(snapshot));
        }
        catch (Exception exception)
        {
            throw new AssertionError(exception);
        }
        return action;
    }

    private static String resource(String name) throws IOException
    {
        try (var input = HrSignEventOutboxMySql57IT.class.getClassLoader()
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

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("com.erp.system.mapper")
    static class ItConfiguration
    {
        @Bean
        DataSource dataSource(
                @Value("${hr.sign.outbox.it.jdbc-url}") String url,
                @Value("${hr.sign.outbox.it.username}") String username,
                @Value("${hr.sign.outbox.it.password}") String password)
        {
            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource)
                throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.erp.system");
            factory.setMapperLocations(
                    new ClassPathResource(
                            "mapper/system/SysHrLifecycleActionMapper.xml"),
                    new ClassPathResource(
                            "mapper/system/SysHrSignEventOutboxMapper.xml"));
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory)
        {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source)
        {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source)
        {
            return new JdbcTemplate(source);
        }

        @Bean
        ObjectMapper objectMapper()
        {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        HrSignEventPayloadFactory payloadFactory(ObjectMapper objectMapper)
        {
            return new HrSignEventPayloadFactory(objectMapper);
        }
    }
}
