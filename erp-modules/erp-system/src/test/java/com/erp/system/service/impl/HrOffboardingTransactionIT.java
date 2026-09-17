package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.erp.system.testsupport.MySqlTransactionTestDatabase;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.dto.HrOffboardingCompletionStatus;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.domain.dto.HrOffboardingNonCompeteDecision;
import com.erp.system.domain.dto.HrOffboardingRiskConfirmation;
import com.erp.system.domain.dto.HrOffboardingType;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysHrLifecycleActionMapper;
import com.erp.system.mapper.SysHrRenewalGuardMapper;
import com.erp.system.mapper.SysHrSignEventOutboxMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.IHrLifecycleService;
import com.erp.system.service.ISysUserShopService;

@ActiveProfiles("hr-offboarding-it")
@SpringBootTest(classes = HrOffboardingTransactionIT.ItConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.discovery.enabled=false" })
@DisplayName("HR离职 MySQL 事务与并发")
class HrOffboardingTransactionIT
{
    private static final Instant FIXED_NOW = Instant.parse("2026-07-13T02:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);
    private static final String RISK_STATEMENT =
            "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用";

    @RegisterExtension
    static final MySqlTransactionTestDatabase MYSQL =
            new MySqlTransactionTestDatabase("offboarding", "hr.offboarding.it");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry)
    {
        registry.add("hr.offboarding.it.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("hr.offboarding.it.username", MYSQL::getUsername);
        registry.add("hr.offboarding.it.password", MYSQL::getPassword);
    }

    @Autowired private IHrLifecycleService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysUserMapper users;

    @BeforeAll
    static void migrate() throws Exception
    {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            executeScript(connection, resource("hr-offboarding-it-schema.sql"));
            executeScript(connection, Files.readString(
                    migration("erp_hr_lifecycle_action_20260711.sql"),
                    StandardCharsets.UTF_8));
            executeScript(connection, Files.readString(
                    migration("erp_hr_transfer_effective_date_20260713.sql"),
                    StandardCharsets.UTF_8));
            String offboardingMigration = Files.readString(
                    migration("erp_hr_offboarding_automation_20260713.sql"),
                    StandardCharsets.UTF_8);
            executeScript(connection, offboardingMigration);
            executeScript(connection, offboardingMigration);
        }
    }

    @BeforeEach
    void seed()
    {
        jdbc.execute("drop trigger if exists fail_offboarding_outbox_insert");
        jdbc.execute("drop trigger if exists fail_offboarding_account_update");
        for (String table : List.of("sys_hr_sign_event_outbox", "sys_hr_lifecycle_action",
                "sys_user_post", "sys_user_role", "sys_user_profile", "sys_config",
                "sys_user", "sys_post", "sys_dept"))
        {
            jdbc.execute("delete from " + table);
        }
        jdbc.update("insert into sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,leader,status,dept_type,del_flag) "
                + "values (20,0,'0','上海一店',1,'部门主管','0','STORE','0')");
        jdbc.update("insert into sys_post(post_id,post_code,post_name,post_sort,status) "
                + "values (401,'SALES','销售顾问',1,'0')");
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,phonenumber,status,del_flag) "
                + "values (9,20,'employee-9','张三','13800000009','0','0')");
        jdbc.update("insert into sys_user_profile(user_id,employee_no,dept_level3_name,store_name,position_names,"
                + "job_grade,department_supervisor,employee_status,employee_category,id_type,id_number,current_address,"
                + "work_location,work_city_level,legal_entity,legal_entity_id,legal_entity_code,contract_type,contract_term,"
                + "social_type,renewal_count,entry_date,contract_start_date,contract_end_date,base_salary,post_salary,"
                + "field_allowance,performance_salary,salary_total,salary_version) values "
                + "(9,'E0009','上海一店','上海一店','销售顾问','P3','部门主管','正式','全职','身份证',"
                + "'310101199001010019','上海市黄浦区','上海市黄浦区','一线','上海公司',301,'SH-COMPANY',"
                + "'LABOR_CONTRACT','THREE_YEAR','SOCIAL_INSURED',0,'2024-01-01','2024-01-01','2027-12-31',"
                + "5500,1800,400,1300,9000,'CURRENT-2026')");
        jdbc.update("insert into sys_user_post(user_id,post_id) values (9,401)");
        jdbc.update("insert into sys_config(config_name,config_key,config_value,config_type) "
                + "values ('签约HR','sign.hr.user-id','88','Y')");
    }

    @Test
    @DisplayName("事务测试必须运行在配置的真实 MySQL")
    void usesConfiguredMySqlVersion()
    {
        assertThat(jdbc.queryForObject("select version()", String.class))
                .startsWith(MYSQL.getExpectedVersionPrefix());
    }

    @Test
    @DisplayName("离职迁移连续执行两次仍只有一个权限、一个授权和六个 OA 列")
    void offboardingMigrationIsIdempotent()
    {
        Long menuId = jdbc.queryForObject(
                "select menu_id from sys_menu where perms='hr:employee:offboard'",
                Long.class);
        assertThat(menuId).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_menu where perms='hr:employee:offboard'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_menu where role_id=700 and menu_id=?",
                Integer.class, menuId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_sign_hr_menu_grant where role_id=700 and menu_id=? and hr_user_id=88",
                Integer.class, menuId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema=database() "
                        + "and table_name='oa_sign_package' and column_name in "
                        + "('offboarding_type','salary_settlement_status','asset_handover_status',"
                        + "'non_compete_decision','compensation_amount','compensation_note')",
                Integer.class)).isEqualTo(6);

        jdbc.update("delete from sys_sign_hr_menu_grant where role_id=700 and menu_id=?", menuId);
        jdbc.update("delete from sys_role_menu where role_id=700 and menu_id=?", menuId);
        jdbc.execute("call sync_sign_hr_permissions_with_offboarding()");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_menu where role_id=700 and menu_id=?",
                Integer.class, menuId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_sign_hr_menu_grant where role_id=700 and menu_id=? and hr_user_id=88",
                Integer.class, menuId)).isOne();
    }

    @Test
    @DisplayName("未来最后工作日拒绝且四类业务表零变化")
    void futureDateHasZeroSideEffects()
    {
        HrOffboardingConfirmRequest request = request("future-request", TODAY.plusDays(1));
        assertThatThrownBy(() -> confirm(request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("未来最后工作日的离职暂不能确认，请在最后工作日操作");
        assertCurrentEmployee("正式", null, "0");
        assertThat(count("sys_hr_lifecycle_action")).isZero();
        assertThat(count("sys_hr_sign_event_outbox")).isZero();
        assertThat(count("sys_user_post")).isOne();
    }

    @Test
    @DisplayName("当天离职原子更新档案账号并保留岗位历史关系")
    void confirmsTodayAndPreservesAssignments()
    {
        Long actionId = confirm(request("today-request", TODAY));
        assertThat(actionId).isPositive();
        assertCurrentEmployee("离职", TODAY, "1");
        assertSingleAggregate();
        assertThat(count("sys_user_post")).isOne();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=9",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("历史离职必须结构化确认并保存 HIGH 风险")
    void historicalOffboardingRequiresStructuredConfirmation()
    {
        HrOffboardingConfirmRequest request = request("history-request", TODAY.minusDays(1));
        assertThatThrownBy(() -> confirm(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("二次确认");
        assertCurrentEmployee("正式", null, "0");

        request.setRiskConfirmation(confirmation(request));
        assertThat(confirm(request)).isPositive();
        assertThat(jdbc.queryForObject(
                "select concat(risk_level,'|',json_unquote(json_extract(risk_codes_json,'$[0]'))) "
                        + "from sys_hr_lifecycle_action", String.class))
                .isEqualTo("HIGH|HISTORICAL_OFFBOARDING");
    }

    @Test
    @DisplayName("Outbox 写失败回滚档案账号和生命周期动作")
    void outboxFailureRollsBackEverything()
    {
        jdbc.execute("create trigger fail_offboarding_outbox_insert before insert on sys_hr_sign_event_outbox "
                + "for each row signal sqlstate '45000' set message_text='injected offboarding outbox failure'");
        assertThatThrownBy(() -> confirm(request("rollback-outbox", TODAY)))
                .hasRootCauseMessage("injected offboarding outbox failure");
        assertCurrentEmployee("正式", null, "0");
        assertThat(count("sys_hr_lifecycle_action")).isZero();
        assertThat(count("sys_hr_sign_event_outbox")).isZero();
        assertThat(count("sys_user_post")).isOne();
    }

    @Test
    @DisplayName("账号停用步骤失败回滚先写入的 action 和档案更新")
    void accountFailureRollsBackEarlierWrites()
    {
        jdbc.execute("create trigger fail_offboarding_account_update before update on sys_user "
                + "for each row signal sqlstate '45000' set message_text='injected account update failure'");
        assertThatThrownBy(() -> confirm(request("rollback-account", TODAY)))
                .hasRootCauseMessage("injected account update failure");
        assertCurrentEmployee("正式", null, "0");
        assertThat(count("sys_hr_lifecycle_action")).isZero();
        assertThat(count("sys_hr_sign_event_outbox")).isZero();
    }

    @Test
    @DisplayName("账号条件更新使用旧状态并在不匹配时返回零")
    void accountConditionalUpdateReturnsZeroForStaleStatus()
    {
        assertThat(users.disableUserForOffboarding(9L, "1", "配置HR")).isZero();
        assertCurrentEmployee("正式", null, "0");
    }

    @Test
    @DisplayName("相同 requestId 并发等待员工锁并返回同一 actionId")
    void concurrentSameRequestCreatesOneAction() throws Exception
    {
        List<Object> outcomes = race(
                () -> request("same-request", TODAY),
                () -> request("same-request", TODAY));
        assertThat(outcomes).allSatisfy(value -> assertThat(value).isInstanceOf(Long.class));
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertSingleAggregate();
    }

    @Test
    @DisplayName("不同 requestId 同员工同最后工作日并发只有一个新 action")
    void concurrentDifferentRequestsCreateOnlyOneAction() throws Exception
    {
        List<Object> outcomes = race(
                () -> request("request-a", TODAY),
                () -> request("request-b", TODAY));
        assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isOne();
        assertThat(outcomes.stream().filter(Throwable.class::isInstance).count()).isOne();
        assertSingleAggregate();
    }

    private List<Object> race(RequestFactory firstFactory, RequestFactory secondFactory)
            throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (Connection lockHolder = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            lockHolder.setAutoCommit(false);
            try (var statement = lockHolder.prepareStatement(
                    "select profile_id from sys_user_profile where user_id=9 for update"))
            {
                try (var rows = statement.executeQuery())
                {
                    assertThat(rows.next()).isTrue();
                }
            }
            Future<Object> first = pool.submit(concurrentCall(firstFactory, ready, start));
            Future<Object> second = pool.submit(concurrentCall(secondFactory, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThatThrownBy(() -> first.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            lockHolder.commit();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    private Callable<Object> concurrentCall(RequestFactory factory,
            CountDownLatch ready, CountDownLatch start)
    {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try { return confirm(factory.create()); }
            catch (Throwable failure) { return failure; }
        };
    }

    private Long confirm(HrOffboardingConfirmRequest request)
    {
        return service.confirmOffboarding(9L, request, 88L, "配置HR", false,
                "10.0.0.8", "offboarding-it-agent");
    }

    private HrOffboardingConfirmRequest request(String requestId, LocalDate date)
    {
        HrOffboardingConfirmRequest request = new HrOffboardingConfirmRequest();
        request.setRequestId(requestId);
        request.setLastWorkingDate(date);
        request.setOffboardingType(HrOffboardingType.VOLUNTARY_EXPECTED);
        request.setReason("按计划主动离职");
        request.setSalarySettlementStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setAssetHandoverStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setNonCompeteDecision(HrOffboardingNonCompeteDecision.NOT_APPLICABLE);
        request.setCompensationAmount(new BigDecimal("0.00"));
        return request;
    }

    private HrOffboardingRiskConfirmation confirmation(HrOffboardingConfirmRequest request)
    {
        HrOffboardingRiskConfirmation value = new HrOffboardingRiskConfirmation();
        value.setConfirmed(true);
        value.setEmployeeId(9L);
        value.setEmployeeName("张三");
        value.setOffboardingType(request.getOffboardingType());
        value.setLastWorkingDate(request.getLastWorkingDate());
        value.setOperationDate(TODAY);
        value.setSalarySettlementStatus(request.getSalarySettlementStatus());
        value.setAssetHandoverStatus(request.getAssetHandoverStatus());
        value.setNonCompeteDecision(request.getNonCompeteDecision());
        value.setCompensationAmount(request.getCompensationAmount());
        value.setRiskStatement(RISK_STATEMENT);
        value.setReason("补录历史离职材料");
        return value;
    }

    private void assertCurrentEmployee(String employeeStatus, LocalDate leaveDate,
            String accountStatus)
    {
        assertThat(jdbc.queryForObject(
                "select employee_status from sys_user_profile where user_id=9", String.class))
                .isEqualTo(employeeStatus);
        assertThat(jdbc.queryForObject(
                "select leave_date from sys_user_profile where user_id=9", LocalDate.class))
                .isEqualTo(leaveDate);
        assertThat(jdbc.queryForObject(
                "select status from sys_user where user_id=9", String.class))
                .isEqualTo(accountStatus);
    }

    private void assertSingleAggregate()
    {
        assertThat(count("sys_hr_lifecycle_action")).isOne();
        assertThat(count("sys_hr_sign_event_outbox")).isOne();
        assertThat(jdbc.queryForObject(
                "select json_unquote(json_extract(payload_json,'$.scenario')) "
                        + "from sys_hr_sign_event_outbox", String.class))
                .isEqualTo("OFFBOARD");
    }

    private int count(String table)
    {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String resource(String name) throws IOException
    {
        try (var input = HrOffboardingTransactionIT.class.getClassLoader()
                .getResourceAsStream(name))
        {
            if (input == null) throw new IOException("missing test resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path migration(String name)
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve("sql").resolve(name);
        if (!Files.exists(path))
            path = root.resolve("../../sql").resolve(name).normalize();
        if (!Files.exists(path))
            throw new IllegalStateException("missing migration: " + path);
        return path;
    }

    private static void executeScript(Connection connection, String sql) throws SQLException
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
                    delimiter = trimmed.substring("DELIMITER ".length()).trim();
                    continue;
                }
                statement.append(line).append('\n');
                int end;
                while ((end = statement.indexOf(delimiter)) >= 0)
                {
                    String command = statement.substring(0, end).trim();
                    statement.delete(0, end + delimiter.length());
                    if (!command.isEmpty()) executor.execute(command);
                }
            }
            if (!statement.toString().trim().isEmpty())
                executor.execute(statement.toString());
        }
    }

    @FunctionalInterface
    private interface RequestFactory
    {
        HrOffboardingConfirmRequest create();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("com.erp.system.mapper")
    static class ItConfiguration
    {
        @Bean
        DataSource dataSource(@Value("${hr.offboarding.it.jdbc-url}") String url,
                @Value("${hr.offboarding.it.username}") String username,
                @Value("${hr.offboarding.it.password}") String password)
        {
            DriverManagerDataSource source = new DriverManagerDataSource(url, username, password);
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return source;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setTypeAliasesPackage("com.erp.system");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/system/*.xml"));
            return factory.getObject();
        }

        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory)
        { return new SqlSessionTemplate(factory); }
        @Bean PlatformTransactionManager transactionManager(DataSource source)
        { return new DataSourceTransactionManager(source); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean ObjectMapper objectMapper()
        { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean ISysUserShopService userShopService() { return mock(ISysUserShopService.class); }

        @Bean
        HrLifecycleServiceImpl lifecycleService(SysConfigMapper configs,
                SysUserProfileMapper profiles, SysHrLifecycleActionMapper actions,
                SysHrSignEventOutboxMapper outboxes, SysHrRenewalGuardMapper renewalGuards,
                SysPostMapper posts, SysDeptMapper departments, SysUserMapper users,
                SysUserPostMapper userPosts, ISysUserShopService shopService,
                ObjectMapper objectMapper)
        {
            HrLifecycleServiceImpl service = new HrLifecycleServiceImpl(configs, profiles,
                    actions, outboxes, renewalGuards, posts, departments, users,
                    userPosts, shopService, objectMapper, org.mockito.Mockito.mock(com.erp.system.service.impl.HrSalarySourceService.class));
            ReflectionTestUtils.setField(service, "clock",
                    Clock.fixed(FIXED_NOW, ZoneId.of("UTC")));
            return service;
        }
    }
}
