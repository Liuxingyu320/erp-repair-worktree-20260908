package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.handler.GlobalExceptionHandler;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.controller.SysProfileController;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.impl.SysUserServiceImpl;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.HealthCertificateMapperFragments;
import com.erp.system.api.domain.SysUser;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.service.SessionRetentionDigest;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;
import com.erp.system.testsupport.NativeRedisIntegrationTestSupport;

/** Actual MySQL outbox/transactions/listener and supplied isolated native Redis; no production data. */
class SecuritySessionRetentionMySqlIT
{
    private static final long USER = 7987654391L;
    private static final long OTHER = 7987654392L;
    static MySQLContainer mysql;
    static NativeRedisIntegrationTestSupport redis;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    static SecuritySessionInvalidationOutboxMapper mapper;
    static SysUserMapper userMapper;
    static final String OLD_PASSWORD = "OldPass~20260913";
    static final String NEW_PASSWORD = "NewPass~20260913";
    AnnotationConfigApplicationContext context;
    TokenService tokens;
    UserSessionInvalidationService records;
    String current;
    String other;
    String foreign;
    final String index = CacheConstants.USER_LOGIN_TOKEN_KEY + USER;

    @Configuration @EnableTransactionManagement
    static class EventConfiguration { }

    @BeforeAll static void start() throws Exception
    {
        String version = System.getProperty("system.session-retention.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("session_retention_it")
                .withUsername("session_it").withPassword(UUID.randomUUID().toString()).withReuse(false)
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1");
        try
        {
            mysql.start();
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith(version);
            script("security-session-outbox-schema.sql");
            jdbc.execute("create table sys_user(user_id bigint primary key,password varchar(100) not null,status char(1) not null default '0',del_flag char(1) not null default '0',credential_state varchar(24),must_change_password char(1),temporary_password_expires_at datetime,pwd_update_date datetime,update_by varchar(64),update_time datetime) engine=InnoDB default charset=utf8mb4");
            script("db/migration/erp_security_session_retention_digest_20260913.sql");
            var config = new org.apache.ibatis.session.Configuration(
                    new Environment("session-retention", new SpringManagedTransactionFactory(), source));
            String resource = "mapper/system/SecuritySessionInvalidationOutboxMapper.xml";
            try (InputStream in = SecuritySessionRetentionMySqlIT.class.getClassLoader().getResourceAsStream(resource))
            { new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse(); }
            HealthCertificateMapperFragments.register(config);
            config.getTypeAliasRegistry().registerAlias("SysUser", SysUser.class);
            config.getTypeAliasRegistry().registerAlias("SysDept", com.erp.system.api.domain.SysDept.class);
            config.getTypeAliasRegistry().registerAlias("SysRole", com.erp.system.api.domain.SysRole.class);
            String userResource = "mapper/system/SysUserMapper.xml";
            try (InputStream in = SecuritySessionRetentionMySqlIT.class.getClassLoader().getResourceAsStream(userResource))
            { new XMLMapperBuilder(in, config, userResource, config.getSqlFragments()).parse(); }
            var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
            mapper = session.getMapper(SecuritySessionInvalidationOutboxMapper.class);
            userMapper = session.getMapper(SysUserMapper.class);
            redis = NativeRedisIntegrationTestSupport.create("session_retention");
        }
        catch (Throwable failure)
        {
            if (redis != null) redis.close();
            mysql.stop(); throw failure;
        }
    }
    static void script(String resource)
    {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource)); return null;
        });
    }
    @AfterAll static void stop()
    {
        if (redis != null) redis.close();
        if (mysql != null) { mysql.stop(); assertThat(mysql.isRunning()).isFalse(); }
    }
    @BeforeEach void fixture()
    {
        jdbc.update("delete from sys_security_session_outbox");
        jdbc.update("delete from sys_user");
        SecurityContextHolder.remove();
        redis.cleanupGeneratedKeys(); redis.getRedisService().deleteObject(index);
        current = redis.sessionId("current", 1); other = redis.sessionId("other", 1); foreign = redis.sessionId("foreign", 1);
        redis.putLoginUser(current, USER, "synthetic-current", 120);
        redis.putLoginUser(other, USER, "synthetic-other", 120);
        redis.putLoginUser(foreign, OTHER, "synthetic-foreign", 120);
        tokens = spy(redis.getTokenService());
        context = new AnnotationConfigApplicationContext(); context.register(EventConfiguration.class);
        context.registerBean("transactionManager", PlatformTransactionManager.class, () -> manager);
        context.registerBean(UserSecurityStateChangedListener.class, () -> new UserSecurityStateChangedListener(tokens, mapper));
        context.refresh(); records = new UserSessionInvalidationService(mapper, context);
    }
    @AfterEach void cleanup()
    {
        SecurityContextHolder.remove();
        if (context != null) context.close();
        redis.cleanupGeneratedKeys(); redis.getRedisService().deleteObject(index);
    }
    @Test void additiveMigrationIsRepeatableAndLegacyRowsRemainNull()
    {
        jdbc.update("insert into sys_security_session_outbox(event_id,event_type,user_id,reason_code) values('legacy-before-rerun','INVALIDATE_USER_SESSIONS',?,'PASSWORD_CHANGED')", USER);
        script("db/migration/erp_security_session_retention_digest_20260913.sql");
        script("db/migration/erp_security_session_retention_digest_20260913.sql");
        assertThat(jdbc.queryForObject("select retained_session_digest from sys_security_session_outbox where event_id='legacy-before-rerun'", String.class)).isNull();
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema=database() and table_name='sys_security_session_outbox' and column_name='retained_session_digest'", String.class)).isEqualTo("YES");
    }
    @Test void committedPasswordChangeSurvivesImmediateRedisFailureAndRestartsFromPersistedDigest()
    {
        byte[] before = redis.getRedisService().getCacheSnapshot(redis.sessionKey(current)).getBytes();
        long ttlBefore = redis.getRedisService().getExpire(redis.sessionKey(current));
        doThrow(new IllegalStateException("isolated Redis fault")).when(tokens).invalidateUserSessions(USER, current);
        String event = new TransactionTemplate(manager).execute(status -> records.record(USER,
                UserSessionInvalidationService.PASSWORD_CHANGED, current));
        assertThat(state(event)).isEqualTo("PENDING");
        String stored = jdbc.queryForObject("select retained_session_digest from sys_security_session_outbox where event_id=?", String.class, event);
        assertThat(stored).isEqualTo(SessionRetentionDigest.fromUserKey(USER, current)).doesNotContain(current);
        // New dispatcher reads only the database row, not the original in-memory event.
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(event)).isEqualTo("DONE");
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
        assertThat(redis.getRedisService().getCacheSnapshot(redis.sessionKey(current)).getBytes()).isEqualTo(before);
        assertThat(redis.getRedisService().getExpire(redis.sessionKey(current))).isBetween(1L, ttlBefore);
        assertThat(redis.getRedisService().hasKey(index)).isFalse();
    }
    @Test void rolledBackBusinessTransactionPublishesNoRevocationAndPersistsNoOutbox()
    {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            records.record(USER, UserSessionInvalidationService.PASSWORD_CHANGED, current); status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from sys_security_session_outbox", Integer.class)).isZero();
        verify(tokens, never()).invalidateUserSessions(anyLong(), nullable(String.class));
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isTrue();
    }
    @Test void expiredRetainedSessionIsNeverRecreatedOrReindexed()
    {
        var row = row(UserSessionInvalidationService.PASSWORD_CHANGED, SessionRetentionDigest.fromUserKey(USER, current));
        redis.getRedisService().addCacheSetValue(index, current);
        redis.getRedisService().expire(redis.sessionKey(current), 0L, TimeUnit.SECONDS);
        assertThat(exists(current)).isFalse();
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(row.getEventId())).isEqualTo("DONE");
        assertThat(exists(current)).isFalse(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
        assertThat(redis.getRedisService().hasKey(index)).isFalse();
    }
    @Test void digestCannotRetainAForeignOwnersCacheEvenWhenIndexIsWrong()
    {
        var row = row(UserSessionInvalidationService.PASSWORD_CHANGED, SessionRetentionDigest.fromUserKey(USER, foreign));
        redis.getRedisService().addCacheSetValue(index, foreign);
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(row.getEventId())).isEqualTo("DONE");
        assertThat(exists(current)).isFalse(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
        assertThat(redis.getRedisService().hasKey(index)).isFalse();
    }
    @Test void legacyNullRetentionStillInvalidatesAllOwnedSessions()
    {
        var row = row(UserSessionInvalidationService.PASSWORD_CHANGED, null);
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(row.getEventId())).isEqualTo("DONE");
        assertThat(exists(current)).isFalse(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
    }
    @ParameterizedTest
    @ValueSource(strings={"PASSWORD_RESET","USER_DISABLED","USER_DELETED","USER_ROLES_CHANGED","ROLE_GRANTS_CHANGED","MENU_GRANTS_CHANGED","LEGACY_CREDENTIAL_ROTATED"})
    void administrativeEventsInvalidateAllEvenWhenADigestWasPersisted(String reason)
    {
        var row = row(reason, SessionRetentionDigest.fromUserKey(USER, current));
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(row.getEventId())).isEqualTo("DONE");
        assertThat(exists(current)).isFalse(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
    }
    @Test void malformedDigestRemainsRetryableWithoutAnyPartialRevocation()
    {
        var row = row(UserSessionInvalidationService.PASSWORD_CHANGED, "invalid-digest");
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(state(row.getEventId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from sys_security_session_outbox where event_id=?", Integer.class, row.getEventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select last_error_code from sys_security_session_outbox where event_id=?", String.class, row.getEventId())).isEqualTo("IllegalArgumentException");
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isTrue();
    }
    @Test void twoCompensatorsClaimOneEventWithoutLosingRetainedSession() throws Exception
    {
        var row = row(UserSessionInvalidationService.PASSWORD_CHANGED, SessionRetentionDigest.fromUserKey(USER, current));
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try
        {
            List<Future<?>> results = new ArrayList<>();
            for (int i=0; i<2; i++) results.add(pool.submit(() -> {
                try { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
            }));
            start.countDown(); for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
            verify(tokens, times(1)).invalidateUserSessionsExceptDigest(USER, row.getRetainedSessionDigest());
            assertThat(state(row.getEventId())).isEqualTo("DONE");
            assertThat(exists(current)).isTrue(); assertThat(exists(other)).isFalse();
        }
        finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void actualPasswordEndpointCommitsDatabaseBeforeRefreshingCurrentCache() throws Exception
    {
        MockMvc endpoint = passwordEndpoint(records);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(SecurityUtils.matchesPassword(NEW_PASSWORD, databasePassword())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from sys_security_session_outbox", Integer.class)).isEqualTo(1);
            return call.callRealMethod();
        }).when(tokens).setLoginUser(any());
        assertThat(changePassword(endpoint)).isEqualTo(200);
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
        var cached = redis.getTokenService().getLoginUserByUserKey(current);
        assertThat(cached.getCredentialState()).isEqualTo("ACTIVE");
        assertThat(cached.getSysUser().getMustChangePassword()).isEqualTo("0");
        assertThat(cached.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(SecurityUtils.matchesPassword(NEW_PASSWORD, cached.getSysUser().getPassword())).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"zero", "throw"})
    void passwordEndpointRollsBackPasswordWhenOutboxInsertFails(String failure) throws Exception
    {
        var broken = mock(SecuritySessionInvalidationOutboxMapper.class, org.mockito.AdditionalAnswers.delegatesTo(mapper));
        if ("zero".equals(failure)) doReturn(0).when(broken).insert(any());
        else doThrow(new ServiceException("isolated outbox failure")).when(broken).insert(any());
        MockMvc endpoint = passwordEndpoint(new UserSessionInvalidationService(broken, context));
        String old = databasePassword();
        assertThat(changePassword(endpoint)).isEqualTo(500);
        assertThat(databasePassword()).isEqualTo(old);
        assertThat(jdbc.queryForObject("select credential_state from sys_user where user_id=?", String.class, USER)).isEqualTo("TEMPORARY");
        assertThat(jdbc.queryForObject("select count(*) from sys_security_session_outbox", Integer.class)).isZero();
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isTrue();
        verify(tokens, never()).setLoginUser(any());
        assertThat(redis.getTokenService().getLoginUserByUserKey(current).getSysUser().getPassword()).isEqualTo(old);
    }
    @ParameterizedTest @ValueSource(strings={"password", "disabled"})
    void passwordEndpointConditionalFailureCreatesNoTaskAndDoesNotRefreshCache(String conflict) throws Exception
    {
        MockMvc endpoint = passwordEndpoint(records);
        if ("password".equals(conflict)) jdbc.update("update sys_user set password=? where user_id=?", SecurityUtils.encryptPassword("Winner~20260913"), USER);
        else jdbc.update("update sys_user set status='1' where user_id=?", USER);
        String before = databasePassword();
        assertThat(changePassword(endpoint)).isEqualTo(409);
        assertThat(databasePassword()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from sys_security_session_outbox", Integer.class)).isZero();
        verify(tokens, never()).setLoginUser(any());
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isTrue();
    }
    @Test void passwordEndpointRedisFailureLeavesCommittedPasswordAndRecoverableTask() throws Exception
    {
        MockMvc endpoint = passwordEndpoint(records);
        doThrow(new IllegalStateException("isolated Redis fault")).when(tokens).invalidateUserSessions(USER, current);
        assertThat(changePassword(endpoint)).isEqualTo(200);
        assertThat(SecurityUtils.matchesPassword(NEW_PASSWORD, databasePassword())).isTrue();
        assertThat(jdbc.queryForObject("select status from sys_security_session_outbox", String.class)).isEqualTo("PENDING");
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isTrue();
        new SecuritySessionInvalidationOutboxDispatcher(mapper, tokens).dispatch();
        assertThat(jdbc.queryForObject("select status from sys_security_session_outbox", String.class)).isEqualTo("DONE");
        assertThat(exists(current)).isTrue(); assertThat(exists(other)).isFalse(); assertThat(exists(foreign)).isTrue();
    }
    @Test void twoPasswordEndpointRequestsUsingTheSameOldHashHaveOnlyOneWinner() throws Exception
    {
        MockMvc endpoint = passwordEndpoint(records);
        var first = redis.getTokenService().getLoginUserByUserKey(current);
        var second = redis.getTokenService().getLoginUserByUserKey(current);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try
        {
            List<Future<Integer>> results = new ArrayList<>();
            for (var login : List.of(first, second)) results.add(pool.submit(() -> {
                SecurityContextHolder.remove(); SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);
                try { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); return changePassword(endpoint); }
                finally { SecurityContextHolder.remove(); }
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from sys_security_session_outbox", Integer.class)).isEqualTo(1);
            assertThat(SecurityUtils.matchesPassword(NEW_PASSWORD, databasePassword())).isTrue();
            assertThat(exists(current)).isTrue(); assertThat(exists(other)).isFalse();
        }
        finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    MockMvc passwordEndpoint(UserSessionInvalidationService invalidations)
    {
        String hash = SecurityUtils.encryptPassword(OLD_PASSWORD);
        jdbc.update("insert into sys_user(user_id,password,credential_state,must_change_password,temporary_password_expires_at) values(?,?,'TEMPORARY','1',date_add(now(),interval 1 hour))", USER, hash);
        var login = redis.getTokenService().getLoginUserByUserKey(current);
        login.getSysUser().setPassword(hash); login.getSysUser().setCredentialState("TEMPORARY");
        login.getSysUser().setMustChangePassword("1"); login.setCredentialState("TEMPORARY");
        login.setTemporaryPasswordExpiresAt(new Date(System.currentTimeMillis()+3600000));
        login.getSysUser().setTemporaryPasswordExpiresAt(login.getTemporaryPasswordExpiresAt());
        redis.getTokenService().setLoginUser(login);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, redis.getTokenService().getLoginUserByUserKey(current));
        var target = new SysUserServiceImpl();
        ReflectionTestUtils.setField(target, "userMapper", userMapper);
        ReflectionTestUtils.setField(target, "userSessionInvalidationService", invalidations);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "userService", proxy.getProxy());
        ReflectionTestUtils.setField(controller, "tokenService", tokens);
        var config = mock(ISysConfigService.class); when(config.selectConfigByKey("sys.account.chrtype")).thenReturn("0");
        ReflectionTestUtils.setField(controller, "configService", config);
        // Actual route/JSON/controller and service transaction; gateway/authentication aspects are outside this fixture.
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    int changePassword(MockMvc endpoint) throws Exception
    {
        String response = endpoint.perform(put("/user/profile/updatePwd").contentType("application/json")
                .content(JSON.toJSONString(Map.of("oldPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return JSON.parseObject(response).getIntValue("code");
    }
    String databasePassword() { return jdbc.queryForObject("select password from sys_user where user_id=?", String.class, USER); }

    SecuritySessionInvalidationOutbox row(String reason, String digest)
    {
        var row = new SecuritySessionInvalidationOutbox(); row.setEventId(UUID.randomUUID().toString().replace("-", ""));
        row.setUserId(USER); row.setReasonCode(reason); row.setRetainedSessionDigest(digest);
        assertThat(mapper.insert(row)).isEqualTo(1); return row;
    }
    boolean exists(String id) { return redis.getRedisService().hasKey(redis.sessionKey(id)); }
    String state(String event) { return jdbc.queryForObject("select status from sys_security_session_outbox where event_id=?", String.class, event); }
}
