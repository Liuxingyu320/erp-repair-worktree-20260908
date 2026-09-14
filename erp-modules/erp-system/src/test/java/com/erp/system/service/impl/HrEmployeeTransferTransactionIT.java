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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.SysHrLifecycleAction;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
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

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("hr-transfer-it")
@SpringBootTest(classes = HrEmployeeTransferTransactionIT.ItConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false" })
@DisplayName("HR调岗 MySQL 5.7 事务与并发")
class HrEmployeeTransferTransactionIT
{
    private static final Instant FIXED_NOW = Instant.parse("2026-12-31T16:30:00Z");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:5.7.44")
            .withDatabaseName("hr_transfer_it")
            .withUsername("hr_it")
            .withPassword("hr_it_password")
            .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry)
    {
        registry.add("hr.transfer.it.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("hr.transfer.it.username", MYSQL::getUsername);
        registry.add("hr.transfer.it.password", MYSQL::getPassword);
    }

    @Autowired private IHrLifecycleService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysUserProfileMapper profiles;
    @Autowired private SysHrLifecycleActionMapper actions;
    @Autowired private ObjectMapper objectMapper;

    @BeforeAll
    static void migrate() throws Exception
    {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            executeScript(connection, resource("hr-transfer-it-schema.sql"));
            executeScript(connection, Files.readString(
                    migration("erp_hr_lifecycle_action_20260711.sql"), StandardCharsets.UTF_8));
            String transferMigration = Files.readString(
                    migration("erp_hr_transfer_effective_date_20260713.sql"), StandardCharsets.UTF_8);
            executeScript(connection, transferMigration);
            executeScript(connection, transferMigration);
        }
    }

    @BeforeEach
    void seed()
    {
        jdbc.execute("drop trigger if exists fail_transfer_outbox_insert");
        for (String table : List.of("sys_hr_sign_event_outbox", "sys_hr_lifecycle_action",
                "sys_user_post", "sys_user_role", "sys_user_profile", "sys_config",
                "sys_user", "sys_post", "sys_dept"))
        {
            jdbc.execute("delete from " + table);
        }
        jdbc.update("insert into sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,leader,status,dept_type,del_flag) values "
                + "(20,0,'0','上海一店',1,'原部门主管','0','STORE','0'),"
                + "(30,0,'0','上海二店',2,'新部门主管','0','STORE','0'),"
                + "(40,0,'0','上海三店',3,'三店主管','0','STORE','0')");
        jdbc.update("insert into sys_post(post_id,post_code,post_name,post_sort,status) values "
                + "(401,'SALES','销售顾问',1,'0'),"
                + "(402,'STOREMGR','店长',2,'0'),"
                + "(403,'AREAMGR','区域店长',3,'0')");
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,phonenumber,status,del_flag) values "
                + "(9,20,'employee-9','张三','13800000009','0','0'),"
                + "(66,30,'supervisor-66','区域经理','13800000066','0','0'),"
                + "(67,40,'supervisor-67','大区经理','13800000067','0','0')");
        jdbc.update("insert into sys_user_profile(user_id,employee_no,dept_level3_name,store_name,position_names,"
                + "job_grade,department_supervisor,direct_supervisor,direct_supervisor_user_id,employee_status,"
                + "employee_category,id_type,id_number,current_address,work_location,work_city_level,legal_entity,"
                + "legal_entity_id,legal_entity_code,contract_type,contract_term,social_type,renewal_count,entry_date,"
                + "contract_start_date,contract_end_date,base_salary,post_salary,field_allowance,performance_salary,"
                + "salary_total,salary_version) values (9,'E0009','上海一店','上海一店','销售顾问','P3',"
                + "'原部门主管','原店长',55,'正式','全职','身份证','310101199001010019','上海市黄浦区',"
                + "'上海市黄浦区','一线','上海公司',301,'SH-COMPANY','LABOR_CONTRACT','THREE_YEAR',"
                + "'SOCIAL_INSURED',0,'2024-01-01','2024-01-01','2027-12-31',5500,1800,400,1300,9000,'CURRENT-2026')");
        jdbc.update("insert into sys_user_post(user_id,post_id) values (9,401)");
        jdbc.update("insert into sys_config(config_name,config_key,config_value,config_type) "
                + "values ('签约HR','sign.hr.user-id','88','Y')");
        jdbc.update("insert ignore into sys_role(role_id,role_name,role_key,status,del_flag) "
                + "values (700,'唯一HR签约','sign_single_hr','0','0')");
        jdbc.execute("call sync_sign_hr_permissions_with_transfer()");
    }

    @Test
    @DisplayName("迁移和运行时同步必须在MySQL5.7为唯一HR幂等维护调岗权限")
    void permissionMigrationAndRuntimeSyncRemainIdempotentOnMySql57()
    {
        Long menuId = jdbc.queryForObject(
                "select menu_id from sys_menu where perms='hr:employee:transfer'",
                Long.class);
        assertThat(menuId).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_menu where perms='hr:employee:transfer'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_menu where role_id=700 and menu_id=?",
                Integer.class, menuId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_sign_hr_menu_grant where role_id=700 and menu_id=? and hr_user_id=88",
                Integer.class, menuId)).isOne();

        jdbc.update("delete from sys_sign_hr_menu_grant where role_id=700 and menu_id=?", menuId);
        jdbc.update("delete from sys_role_menu where role_id=700 and menu_id=?", menuId);
        jdbc.execute("call sync_sign_hr_permissions_with_transfer()");

        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_menu where role_id=700 and menu_id=?",
                Integer.class, menuId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_sign_hr_menu_grant where role_id=700 and menu_id=? and hr_user_id=88",
                Integer.class, menuId)).isOne();
    }

    @Test
    @DisplayName("outbox写失败必须回滚员工组织档案岗位和生命周期动作")
    void outboxFailureRollsBackWholeTransferAggregate()
    {
        jdbc.execute("create trigger fail_transfer_outbox_insert before insert on sys_hr_sign_event_outbox "
                + "for each row signal sqlstate '45000' set message_text='injected transfer outbox failure'");

        assertThatThrownBy(() -> confirm(request("rollback-request", 30L), 88L))
                .hasRootCauseMessage("injected transfer outbox failure");

        assertThat(jdbc.queryForObject("select dept_id from sys_user where user_id=9", Long.class))
                .isEqualTo(20L);
        assertThat(jdbc.queryForObject(
                "select concat(store_name,'|',position_names,'|',salary_version) from sys_user_profile where user_id=9",
                String.class)).isEqualTo("上海一店|销售顾问|CURRENT-2026");
        assertThat(jdbc.queryForObject("select post_id from sys_user_post where user_id=9", Long.class))
                .isEqualTo(401L);
        assertThat(count("sys_hr_lifecycle_action")).isZero();
        assertThat(count("sys_hr_sign_event_outbox")).isZero();
    }

    @Test
    @DisplayName("相同requestId并发确认必须等待员工行锁并只落一个动作和outbox")
    void concurrentSameRequestCreatesExactlyOneTransfer() throws Exception
    {
        List<Object> outcomes = race(
                () -> request("same-transfer-request", 30L),
                () -> request("same-transfer-request", 30L));

        assertThat(outcomes.stream().filter(ServiceException.class::isInstance)
                .map(ServiceException.class::cast)
                .map(ServiceException::getDetailMessage).toList()).isEmpty();
        assertThat(outcomes).allSatisfy(value -> assertThat(value).isInstanceOf(Long.class));
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertSuccessfulSingleTransfer();
    }

    @Test
    @DisplayName("同员工同生效日不同requestId并发只能一个成功另一个稳定冲突")
    void concurrentDifferentRequestsAllowOnlyOneTransfer() throws Exception
    {
        List<Object> outcomes = race(
                () -> request("transfer-request-a", 30L),
                () -> request("transfer-request-b", 40L));

        assertThat(outcomes.stream().filter(Long.class::isInstance).count()).isOne();
        Throwable failure = (Throwable) outcomes.stream()
                .filter(Throwable.class::isInstance).findFirst().orElseThrow();
        assertThat(findCause(failure, ServiceException.class))
                .isNotNull()
                .hasMessage("该员工相同生效日期的调岗已确认");
        assertSuccessfulSingleTransfer();
    }

    @Test
    @DisplayName("已提交调岗必须能从数据库重建与冻结after一致的幂等状态")
    void committedTransferRebuildsReplayState() throws Exception
    {
        HrEmployeeTransferRequest request = request("sequential-replay", 30L);
        Long actionId = confirm(request, 88L);
        HrEmployeeSigningSnapshot current = profiles.selectSigningSnapshotByUserIdForUpdate(9L);
        SysHrLifecycleAction action = actions.selectById(actionId);
        HrEmployeeSigningSnapshot expected = objectMapper.readValue(
                action.getAfterSnapshotJson(), HrEmployeeSigningSnapshot.class);

        assertThat(current.getDeptId()).isEqualTo(expected.getDeptId());
        assertThat(current.getPostId()).isEqualTo(expected.getPostId());
        assertThat(current.getJobGradeCode()).isEqualTo(expected.getJobGradeCode());
        assertThat(current.getWorkLocation()).isEqualTo(expected.getWorkLocation());
        assertThat(current.getDirectSupervisorId()).isEqualTo(expected.getDirectSupervisorId());
        assertThat(current.getLegalEntityId()).isEqualTo(expected.getLegalEntityId());
        assertThat(current.getBaseSalary()).isEqualByComparingTo(expected.getBaseSalary());
        assertThat(current.getPostSalary()).isEqualByComparingTo(expected.getPostSalary());
        assertThat(current.getFieldAllowance()).isEqualByComparingTo(expected.getFieldAllowance());
        assertThat(current.getPerformanceSalary()).isEqualByComparingTo(expected.getPerformanceSalary());
        assertThat(current.getSalaryTotal()).isEqualByComparingTo(expected.getSalaryTotal());
        assertThat(current.getSalaryVersion()).isEqualTo(expected.getSalaryVersion());
        assertThat(confirm(request("sequential-replay", 30L), 88L)).isEqualTo(actionId);
    }

    private void assertSuccessfulSingleTransfer()
    {
        assertThat(count("sys_hr_lifecycle_action")).isOne();
        assertThat(count("sys_hr_sign_event_outbox")).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_hr_lifecycle_action where effective_date='2027-01-01' "
                        + "and actual_confirm_time='2027-01-01 00:30:00'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "select position_no from sys_user_profile where user_id=9",
                String.class)).matches("(STOREMGR|AREAMGR)-E0009");
        assertThat(jdbc.queryForObject(
                "select json_unquote(json_extract(payload_json,'$.scenario')) from sys_hr_sign_event_outbox",
                String.class)).isEqualTo("TRANSFER");
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
                    .as("第一个调岗确认必须等待生产员工档案FOR UPDATE行锁")
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .as("第二个调岗确认必须等待生产员工档案FOR UPDATE行锁")
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
            try
            {
                return confirm(factory.create(), 88L);
            }
            catch (Throwable failure)
            {
                return failure;
            }
        };
    }

    private Long confirm(HrEmployeeTransferRequest request, Long operatorId)
    {
        return service.confirmTransfer(9L, request, operatorId, "配置HR", false,
                "10.0.0.8", "transfer-it-agent");
    }

    private HrEmployeeTransferRequest request(String requestId, Long targetDeptId)
    {
        boolean second = Long.valueOf(40L).equals(targetDeptId);
        HrEmployeeTransferRequest request = new HrEmployeeTransferRequest();
        request.setRequestId(requestId);
        request.setEffectiveDate(LocalDate.of(2027, 1, 1));
        request.setTargetDeptId(targetDeptId);
        request.setTargetDeptName(second ? "上海三店" : "上海二店");
        request.setPostId(second ? 403L : 402L);
        request.setPostCode(second ? "AREAMGR" : "STOREMGR");
        request.setPostName(second ? "区域店长" : "店长");
        request.setJobGradeCode(second ? "P5" : "P4");
        request.setJobGradeName(second ? "P5" : "P4");
        request.setWorkLocation(second ? "上海市徐汇区" : "上海市浦东新区");
        request.setWorkCityLevel("一线");
        request.setDirectSupervisorId(second ? 67L : 66L);
        request.setDirectSupervisorName(second ? "大区经理" : "区域经理");
        request.setLegalEntityId(301L);
        request.setLegalEntityCode("SH-COMPANY");
        request.setLegalEntityName("上海公司");
        request.setBaseSalary(new BigDecimal("6000.00"));
        request.setPostSalary(new BigDecimal("2000.00"));
        request.setFieldAllowance(new BigDecimal("500.00"));
        request.setPerformanceSalary(new BigDecimal("1500.00"));
        request.setSalaryTotal(new BigDecimal("10000.00"));
        request.setSalaryVersion(second ? "TRANSFER-AREA-2027" : "TRANSFER-STORE-2027");
        return request;
    }

    private int count(String table)
    {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private <T extends Throwable> T findCause(Throwable failure, Class<T> type)
    {
        Throwable cursor = failure;
        while (cursor != null)
        {
            if (type.isInstance(cursor))
            {
                return type.cast(cursor);
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private static String resource(String name) throws IOException
    {
        try (var input = HrEmployeeTransferTransactionIT.class.getClassLoader()
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

    @FunctionalInterface
    private interface RequestFactory
    {
        HrEmployeeTransferRequest create();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("com.erp.system.mapper")
    static class ItConfiguration
    {
        @Bean
        DataSource dataSource(@Value("${hr.transfer.it.jdbc-url}") String url,
                @Value("${hr.transfer.it.username}") String username,
                @Value("${hr.transfer.it.password}") String password)
        {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.erp.system");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/system/*.xml"));
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
        ISysUserShopService userShopService()
        {
            return mock(ISysUserShopService.class);
        }

        @Bean
        HrLifecycleServiceImpl lifecycleService(SysConfigMapper configs,
                SysUserProfileMapper profiles, SysHrLifecycleActionMapper actions,
                SysHrSignEventOutboxMapper outboxes, SysHrRenewalGuardMapper renewalGuards,
                SysPostMapper posts, SysDeptMapper departments, SysUserMapper users,
                SysUserPostMapper userPosts, ISysUserShopService shopService,
                ObjectMapper objectMapper)
        {
            HrLifecycleServiceImpl service = new HrLifecycleServiceImpl(configs, profiles,
                    actions, outboxes, renewalGuards, posts, departments, users, userPosts,
                    shopService, objectMapper, org.mockito.Mockito.mock(com.erp.system.service.impl.HrSalarySourceService.class));
            ReflectionTestUtils.setField(service, "clock",
                    Clock.fixed(FIXED_NOW, ZoneId.of("UTC")));
            return service;
        }
    }
}
