package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.datascope.aspect.DataScopeAspect;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.domain.vo.HrOnboardingConfirmRequest;
import com.erp.system.domain.vo.HrOnboardingConfirmResult;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.HrOnboardingMapper;
import com.erp.system.mapper.HrOnboardingOperationLogMapper;
import com.erp.system.mapper.HrOnboardingPositionConfigMapper;
import com.erp.system.mapper.SysDeptMapper;
import com.erp.system.mapper.SysPostMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserPostMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.mapper.SysUserShopMapper;
import com.erp.system.service.IHrOnboardingPositionConfigService;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDictTypeService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.support.HrEmployeeNoGenerator;
import com.erp.system.support.HrSensitiveFieldMasker;

@ActiveProfiles("hr-onboarding-it")
@SpringBootTest(classes = HrOnboardingTransactionIT.ItConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = { "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false" })
@DisplayName("HR 入职 MySQL 事务集成")
class HrOnboardingTransactionIT
{
    @RegisterExtension
    static final MySqlTransactionTestDatabase MYSQL =
            new MySqlTransactionTestDatabase("onboarding", "hr.onboarding.it");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry)
    {
        registry.add("hr.onboarding.it.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("hr.onboarding.it.username", MYSQL::getUsername);
        registry.add("hr.onboarding.it.password", MYSQL::getPassword);
    }

    @Autowired private HrOnboardingConfirmationService confirmationService;
    @Autowired private HrOnboardingAccessService accessService;
    @Autowired private IHrOnboardingPositionConfigService positionConfigService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() throws Exception
    {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            executeScript(connection, resource("hr-onboarding-it-schema.sql"));
            executeScript(connection, Files.readString(migration(), StandardCharsets.UTF_8));
            executeScript(connection, Files.readString(positionNumberMigration(), StandardCharsets.UTF_8));
        }
    }

    @BeforeEach
    void seed()
    {
        jdbc.execute("drop trigger if exists fail_hr_profile_insert");
        for (String table : List.of("hr_employee_position_no_history", "hr_onboarding_operation_log", "sys_user_shop", "sys_user_post",
                "sys_user_role", "sys_user_profile", "sys_user", "hr_onboarding_position_config_role",
                "hr_onboarding_position_config", "hr_onboarding", "sys_role_dept", "sys_role", "sys_post", "sys_dept"))
            jdbc.execute("delete from " + table);
        jdbc.update("update hr_employee_no_sequence set current_value=0 where sequence_key='GLOBAL'");

        jdbc.update("insert into sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,dept_type) values "
                + "(10,0,'0','A公司',1,'COMPANY'),(20,0,'0','B公司',2,'COMPANY')");
        jdbc.update("insert into sys_post(post_id,post_code,post_name,post_sort,status) values (100,'P100','顾问',1,'0')");
        jdbc.update("insert into sys_role(role_id,role_name,role_key,role_sort,data_scope,status,del_flag) "
                + "values (500,'员工','employee',1,'3','0','0')");
        jdbc.update("insert into sys_user(user_id,dept_id,user_name,nick_name,phonenumber,status,del_flag) values "
                + "(101,10,'supervisor-a','主管A','13900000101','0','0'),"
                + "(201,20,'supervisor-b','主管B','13900000201','0','0')");
        jdbc.update("insert into hr_onboarding_position_config(config_id,post_id,employee_category,"
                + "data_scope_strategy,contract_type_mode,social_type_mode,probation_period_mode,"
                + "account_enabled,status,version) values (700,100,'FULL_TIME','TARGET_DEPT',"
                + "'OPTIONAL','OPTIONAL','NOT_APPLICABLE','1','0',0)");
        jdbc.update("insert into hr_onboarding_position_config_role(config_id,role_id,create_by) values (700,500,'seed')");
        for (long id = 1; id <= 5; id++) seedOnboarding(id, id == 3 ? 20L : 10L);
    }

    @AfterEach
    void clearSecurity()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("确认成功应一次性创建账号、档案、角色、岗位、门店和确认日志")
    void successfulConfirmationCreatesEveryAssociation()
    {
        login(900L, 10L);
        assertThat(positionConfigService.resolveActive(100L, "FULL_TIME").getRoleIds())
                .as("生产配置服务必须从 config_role mapper 水合角色")
                .containsExactly(500L);
        HrOnboardingConfirmResult result = confirmationService.confirm(1L, request("success-key"), "hr-a");

        assertThat(result.getUserId()).isNotNull();
        assertThat(count("sys_user", "user_id", result.getUserId())).isOne();
        Map<String, Object> account = jdbc.queryForMap(
                "select user_name,phonenumber from sys_user where user_id=?", result.getUserId());
        assertThat(account).containsEntry("user_name", "13800000001")
                .containsEntry("phonenumber", "13800000001");
        assertThat(count("sys_user_profile", "user_id", result.getUserId())).isOne();
        assertThat(jdbc.queryForObject("select position_no from sys_user_profile where user_id=?",
                String.class, result.getUserId())).isEqualTo("P100-E00001");
        assertThat(count("hr_employee_position_no_history", "user_id", result.getUserId())).isOne();
        assertThat(count("sys_user_role", "user_id", result.getUserId())).isOne();
        assertThat(count("sys_user_post", "user_id", result.getUserId())).isOne();
        assertThat(count("sys_user_shop", "user_id", result.getUserId())).isOne();
        assertThat(jdbc.queryForObject("select status from hr_onboarding where onboarding_id=1", String.class))
                .isEqualTo("CONFIRMED");
        assertThat(count("hr_onboarding_operation_log", "onboarding_id", 1L)).isOne();
    }

    @Test
    @DisplayName("档案写失败应回滚账号、关系、风险和确认状态")
    void profileFailureRollsBackWholeAggregate()
    {
        login(900L, 10L);
        jdbc.execute("create trigger fail_hr_profile_insert before insert on sys_user_profile for each row "
                + "signal sqlstate '45000' set message_text='injected profile failure'");

        assertThatThrownBy(() -> confirmationService.confirm(2L, request("rollback-key"), "hr-a"))
                .hasRootCauseMessage("injected profile failure");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_user where user_name='13800000002'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_profile", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_role", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_post", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_shop", Integer.class)).isZero();
        Map<String, Object> row = jdbc.queryForMap("select status,linked_user_id,account_risk_code from hr_onboarding where onboarding_id=2");
        assertThat(row).containsEntry("status", "READY").containsEntry("linked_user_id", null)
                .containsEntry("account_risk_code", null);
    }

    @Test
    @DisplayName("本部门 HR 可确认，另一部门主管不可读、锁定或确认")
    void departmentScopeProtectsReadLockAndConfirm()
    {
        login(900L, 10L);
        assertThat(accessService.findScoped(query(1L)).getOnboardingId()).isEqualTo(1L);

        login(101L, 10L);
        assertThatThrownBy(() -> accessService.findScoped(query(3L))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> accessService.lockScopedForUpdate(query(3L))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> confirmationService.confirm(3L, request("scope-key"), "supervisor-a"))
                .isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select status from hr_onboarding where onboarding_id=3", String.class))
                .isEqualTo("READY");
    }

    @Test
    @DisplayName("相同幂等键并发确认只创建一个账号")
    void concurrentSameKeyCreatesExactlyOneUser() throws Exception
    {
        List<Object> outcomes = race(4L, "same-key", "same-key");
        assertThat(outcomes).allSatisfy(value -> assertThat(value).isInstanceOf(HrOnboardingConfirmResult.class));
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_user where user_name='13800000004'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from sys_user_profile", Integer.class)).isOne();
    }

    @Test
    @DisplayName("同一入职单不同幂等键并发应稳定返回已确认冲突")
    void concurrentDifferentKeysReturnsStableConflict() throws Exception
    {
        List<Object> outcomes = race(5L, "key-a", "key-b");
        assertThat(outcomes.stream().filter(HrOnboardingConfirmResult.class::isInstance).count()).isEqualTo(1);
        Object failure = outcomes.stream().filter(Throwable.class::isInstance).findFirst().orElseThrow();
        assertThat(root(failure)).isInstanceOf(HrOnboardingValidationException.class);
        assertThat(((HrOnboardingValidationException) root(failure)).getErrorCode())
                .isEqualTo("ONBOARDING_ALREADY_CONFIRMED");
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_user where user_name='13800000005'", Integer.class)).isOne();
    }

    private List<Object> race(long onboardingId, String firstKey, String secondKey) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (Connection lockHolder = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()))
        {
            lockHolder.setAutoCommit(false);
            try (var statement = lockHolder.prepareStatement(
                    "select onboarding_id from hr_onboarding where onboarding_id=? for update"))
            {
                statement.setLong(1, onboardingId);
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            Callable<Object> first = concurrentCall(onboardingId, firstKey, ready, start);
            Callable<Object> second = concurrentCall(onboardingId, secondKey, ready, start);
            Future<Object> a = pool.submit(first); Future<Object> b = pool.submit(second);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThatThrownBy(() -> a.get(500, TimeUnit.MILLISECONDS))
                    .as("第一个确认必须等待生产 FOR UPDATE 行锁")
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> b.get(500, TimeUnit.MILLISECONDS))
                    .as("第二个确认必须等待生产 FOR UPDATE 行锁")
                    .isInstanceOf(TimeoutException.class);
            lockHolder.commit();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    private Callable<Object> concurrentCall(long onboardingId, String key, CountDownLatch ready, CountDownLatch start)
    {
        return () -> {
            login(900L, 10L); ready.countDown(); start.await(10, TimeUnit.SECONDS);
            try { return confirmationService.confirm(onboardingId, request(key), "hr-a"); }
            catch (Throwable failure) { return failure; }
            finally { SecurityContextHolder.remove(); }
        };
    }

    private Throwable root(Object value)
    {
        Throwable result = (Throwable) value;
        while (result.getCause() != null) result = result.getCause();
        return result;
    }

    private void seedOnboarding(long id, long deptId)
    {
        long owner = deptId == 10 ? 101 : 201;
        jdbc.update("insert into hr_onboarding(onboarding_id,onboarding_no,status,version,employee_name,phone_number,"
                + "target_dept_id,target_post_id,direct_supervisor_user_id,owner_user_id,employee_category,"
                + "expected_entry_date,source_type,company_name,position_name,legal_entity) "
                + "values (?,?, 'READY',0,?,?,?,100,?,?, 'FULL_TIME','2026-07-11','MANUAL',?,?,?)",
                id, "ONB-IT-" + id, "员工" + id, "1380000000" + id, deptId, owner, owner,
                deptId == 10 ? "A公司" : "B公司", "顾问", deptId == 10 ? "A公司" : "B公司");
    }

    private HrOnboardingConfirmRequest request(String key)
    {
        HrOnboardingConfirmRequest request = new HrOnboardingConfirmRequest();
        request.setVersion(0); request.setActualEntryDate(Date.from(Instant.parse("2026-07-11T00:00:00Z")));
        request.setConflictAction("CREATE_NEW"); request.setIdempotencyKey(key); return request;
    }

    private HrOnboardingQuery query(Long id)
    {
        HrOnboardingQuery query = new HrOnboardingQuery(); query.setOnboardingId(id); return query;
    }

    private int count(String table, String column, Long value)
    {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?", Integer.class, value);
    }

    private void login(Long userId, Long deptId)
    {
        SysRole scope = new SysRole(); scope.setRoleId(800L); scope.setRoleKey("hr");
        scope.setDataScope("3"); scope.setStatus("0"); scope.setPermissions(Collections.emptySet());
        SysUser user = new SysUser(); user.setUserId(userId); user.setDeptId(deptId);
        user.setUserName("actor-" + userId); user.setRoles(new ArrayList<>(List.of(scope)));
        LoginUser login = new LoginUser(); login.setUserid(userId); login.setUsername(user.getUserName());
        login.setSysUser(user); login.setRoles(Set.of("hr")); login.setPermissions(Set.of("hr:onboarding:confirm"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);
        SecurityContextHolder.setUserId(String.valueOf(userId));
        SecurityContextHolder.setUserName(user.getUserName());
    }

    private static String resource(String name) throws IOException
    {
        try (var input = HrOnboardingTransactionIT.class.getClassLoader().getResourceAsStream(name))
        {
            if (input == null) throw new IOException("missing test resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path migration()
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve("sql/erp_user_hr_onboarding_20260710.sql");
        if (!Files.exists(path)) path = root.resolve("../../sql/erp_user_hr_onboarding_20260710.sql").normalize();
        if (!Files.exists(path)) throw new IllegalStateException("missing onboarding migration: " + path);
        return path;
    }

    private static Path positionNumberMigration()
    {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve("sql/erp_hr_employee_position_number_20260714.sql");
        if (!Files.exists(path))
            path = root.resolve("../../sql/erp_hr_employee_position_number_20260714.sql").normalize();
        if (!Files.exists(path))
            throw new IllegalStateException("missing employee position-number migration: " + path);
        return path;
    }

    private static void executeScript(Connection connection, String sql) throws SQLException
    {
        String delimiter = ";"; StringBuilder statement = new StringBuilder();
        try (Statement executor = connection.createStatement())
        {
            for (String line : sql.split("\\R", -1))
            {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("#"))
                {
                    continue;
                }
                if (trimmed.toUpperCase().startsWith("DELIMITER "))
                {
                    delimiter = trimmed.substring("DELIMITER ".length()).trim(); continue;
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
            if (!statement.toString().trim().isEmpty()) executor.execute(statement.toString());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @MapperScan("com.erp.system.mapper")
    static class ItConfiguration
    {
        @Bean
        DataSource dataSource(@Value("${hr.onboarding.it.jdbc-url}") String url,
                @Value("${hr.onboarding.it.username}") String username,
                @Value("${hr.onboarding.it.password}") String password)
        {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver"); return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.erp.system.api.domain;com.erp.system.domain");
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            factory.setMapperLocations(
                    resolver.getResource("classpath:mapper/system/HrOnboardingMapper.xml"),
                    resolver.getResource("classpath:mapper/system/HrOnboardingOperationLogMapper.xml"),
                    resolver.getResource("classpath:mapper/system/HrOnboardingPositionConfigMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysDeptMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysPostMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysRoleMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysUserMapper.xml"),
                    resolver.getResource("classpath:mapper/system/HrHealthCertificateMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysUserPostMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysUserProfileMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysUserRoleMapper.xml"),
                    resolver.getResource("classpath:mapper/system/SysUserShopMapper.xml"));
            return factory.getObject();
        }

        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean DataScopeAspect dataScopeAspect() { return new DataScopeAspect(); }
        @Bean HrSensitiveFieldMasker masker() { return new HrSensitiveFieldMasker(); }

        @Bean
        SysUserProfileDerivationService derivationService()
        {
            SysUserProfileDerivationService service = mock(SysUserProfileDerivationService.class);
            SysUserProfile derived = new SysUserProfile(); derived.setCompanyName("公司");
            derived.setPositionNames("顾问"); derived.setDepartmentSupervisor("主管");
            when(service.preview(any())).thenReturn(derived); return service;
        }

        @Bean
        HrOnboardingAccessService accessService(HrOnboardingMapper onboarding, SysDeptMapper dept,
                SysPostMapper post, SysUserMapper user, SysUserProfileDerivationService derivation)
        { return new HrOnboardingAccessService(onboarding, dept, post, user, derivation); }

        @Bean ISysUserService userService() { return mock(ISysUserService.class); }
        @Bean ISysRoleService roleService() { return mock(ISysRoleService.class); }

        @Bean ISysDictTypeService dictTypeService() { return mock(ISysDictTypeService.class); }

        @Bean
        IHrOnboardingPositionConfigService positionConfigService(HrOnboardingPositionConfigMapper mapper,
                SysPostMapper posts, ISysRoleService roles, ISysConfigService configs,
                ISysDictTypeService dictionaries)
        { return new HrOnboardingPositionConfigServiceImpl(mapper, posts, roles, configs, dictionaries); }

        @Bean
        HrOnboardingRuleService rules()
        {
            HrOnboardingRuleService rules = mock(HrOnboardingRuleService.class);
            when(rules.evaluateConfirm(any(), any())).thenReturn(new HrOnboardingCompletionVo()); return rules;
        }

        @Bean
        ISysConfigService sysConfigService()
        {
            ISysConfigService service = mock(ISysConfigService.class);
            when(service.selectConfigByKey("hr.employee.no.prefix")).thenReturn("E"); return service;
        }

        @Bean
        HrEmployeeNoGenerator employeeNoGenerator(ISysConfigService config, SysUserMapper users,
                SysUserProfileMapper profiles)
        { return new HrEmployeeNoGenerator(config, users, profiles, Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)); }

        @Bean
        ISysUserShopService userShopService(SysUserShopMapper mapper)
        {
            ISysUserShopService service = mock(ISysUserShopService.class);
            doAnswer(call -> {
                Long userId = call.getArgument(0); Long[] shops = call.getArgument(1); String operator = call.getArgument(2);
                int count = 0; for (Long shop : shops) count += mapper.insertUserShopIfAbsent(userId, shop, operator); return count;
            }).when(service).saveUserShops(anyLong(), any(Long[].class), anyString(), anyLong(), anyBoolean());
            return service;
        }

        @Bean
        HrOnboardingConflictService conflictService(HrOnboardingAccessService access, HrSensitiveFieldMasker masker,
                ISysUserService users)
        { return new HrOnboardingConflictService(access, masker, users); }

        @Bean
        HrOnboardingConfirmationService confirmationService(HrOnboardingMapper onboarding,
                HrOnboardingOperationLogMapper logs, HrOnboardingAccessService access, HrOnboardingRuleService rules,
                IHrOnboardingPositionConfigService configs, HrOnboardingConflictService conflicts,
                HrEmployeeNoGenerator numbers, SysUserMapper users, SysUserProfileMapper profiles,
                SysUserRoleMapper roles, SysUserPostMapper posts, SysUserShopMapper shops,
                ISysUserShopService shopService, ISysUserService userService, SysRoleMapper systemRoles,
                ISysRoleService roleService)
        {
            return new HrOnboardingConfirmationService(onboarding, logs, access, rules, configs, conflicts, numbers,
                    users, profiles, roles, posts, shops, shopService, userService, systemRoles, roleService,
                    Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC), () -> 900L, () -> true,
                    () -> "Aa2!Bb3@Cc4#Dd5$", raw -> "encoded:" + raw);
        }
    }
}
