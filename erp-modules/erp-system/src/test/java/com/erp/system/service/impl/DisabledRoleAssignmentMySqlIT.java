package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysUserRole;
import com.erp.system.mapper.*;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.UserSessionInvalidationService;

/** Real service transactions and role/association mapper SQL. Only user-profile/scope collaborators are synthetic. */
class DisabledRoleAssignmentMySqlIT
{
    static MySQLContainer mysql;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    SysRoleMapper roleMapper;
    SysUserRoleMapper relations;
    SysUserServiceImpl users;
    SysRoleServiceImpl roles;
    UserSessionInvalidationService invalidation;

    @BeforeAll static void startDatabase()
    {
        String version = System.getProperty("s06.mysql.version", "5.7.44");
        assertThat(version).isIn("5.7.44", "8.0.36");
        mysql = new MySQLContainer("mysql:" + version).withDatabaseName("s06_roles")
                .withUsername("s06_it").withPassword(UUID.randomUUID().toString())
                .withReuse(false).withLabels(Map.of("erp.task", "s06-d13"))
                .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
                .withEnv("MYSQL_INITDB_SKIP_TZINFO", "1");
        try
        {
            mysql.start();
            assertThat(mysql.getMappedPort(3306)).isNotEqualTo(3306);
            var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
            assertThat(jdbc.queryForObject("select version()", String.class)).startsWith(version);
            jdbc.execute("create table sys_sign_hr_state(state_id bigint primary key) engine=InnoDB");
            jdbc.update("insert into sys_sign_hr_state values(1)");
            jdbc.execute("create table sys_user(user_id bigint primary key auto_increment, nick_name varchar(80), del_flag char(1) default '0') engine=InnoDB");
            jdbc.execute("create table sys_role(role_id bigint primary key, role_name varchar(80), role_key varchar(80), role_sort int, data_scope char(1), menu_check_strictly bit, dept_check_strictly bit, status char(1), del_flag char(1), create_by varchar(80), create_time datetime, update_by varchar(80), update_time datetime, remark varchar(200)) engine=InnoDB");
            jdbc.execute("create table sys_user_role(user_id bigint not null,role_id bigint not null,primary key(user_id,role_id),key role_idx(role_id)) engine=InnoDB");
            System.out.printf("S06 isolated image=%s container=%s host=%s port=%s%n", version, mysql.getContainerId(), mysql.getHost(), mysql.getMappedPort(3306));
        }
        catch (Throwable failure) { mysql.stop(); throw failure; }
    }

    @AfterAll static void stopDatabase()
    {
        if (mysql != null)
        {
            String id = mysql.getContainerId(); mysql.stop();
            assertThat(mysql.isRunning()).isFalse(); System.out.println("S06 cleaned container=" + id);
        }
    }

    @BeforeEach void fixture() throws Exception
    {
        jdbc.update("delete from sys_user_role"); jdbc.update("delete from sys_user"); jdbc.update("delete from sys_role");
        jdbc.update("insert into sys_user(user_id,nick_name) values(100,'old-name'),(200,'other')");
        jdbc.update("insert into sys_role(role_id,status,del_flag) values(20,'0','0'),(30,'1','0'),(40,'0','0')");
        jdbc.update("insert into sys_user_role values(100,20),(100,30)");
        Configuration config = new Configuration(new Environment("s06", new SpringManagedTransactionFactory(), manager.getDataSource()));
        config.getTypeAliasRegistry().registerAlias("SysRole", SysRole.class);
        config.getTypeAliasRegistry().registerAlias("SysUserRole", SysUserRole.class);
        for (String name : List.of("SysRoleMapper", "SysUserRoleMapper"))
        {
            String resource = "mapper/system/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            { new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse(); }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        roleMapper = session.getMapper(SysRoleMapper.class); relations = session.getMapper(SysUserRoleMapper.class);
        SysConfigMapper state = mock(SysConfigMapper.class);
        when(state.lockSignHrState()).thenAnswer(call -> jdbc.queryForObject("select state_id from sys_sign_hr_state where state_id=1 for update", Long.class));
        invalidation = mock(UserSessionInvalidationService.class);
        SysUserServiceImpl userTarget = new ScopedUsers();
        SysRoleServiceImpl roleTarget = new ScopedRoles();
        for (Object target : List.of(userTarget, roleTarget))
        {
            ReflectionTestUtils.setField(target, "roleMapper", roleMapper);
            ReflectionTestUtils.setField(target, "userRoleMapper", relations);
            ReflectionTestUtils.setField(target, "configMapper", state);
            ReflectionTestUtils.setField(target, "userSessionInvalidationService", invalidation);
        }
        SysUserMapper userSql = mock(SysUserMapper.class);
        when(userSql.updateUser(any())).thenAnswer(call -> {
            SysUser user = call.getArgument(0); return jdbc.update("update sys_user set nick_name=? where user_id=?", user.getNickName(), user.getUserId());
        });
        when(userSql.insertUser(any())).thenAnswer(call -> {
            SysUser user = call.getArgument(0); user.setUserId(300L);
            return jdbc.update("insert into sys_user(user_id,nick_name) values(300,?)", user.getNickName());
        });
        ReflectionTestUtils.setField(userTarget, "userMapper", userSql);
        ReflectionTestUtils.setField(userTarget, "profileMapper", mock(SysUserProfileMapper.class));
        ReflectionTestUtils.setField(userTarget, "userPostMapper", mock(SysUserPostMapper.class));
        ReflectionTestUtils.setField(roleTarget, "userService", mock(ISysUserService.class));
        ReflectionTestUtils.setField(roleTarget, "roleMenuMapper", mock(SysRoleMenuMapper.class));
        ReflectionTestUtils.setField(roleTarget, "roleDeptMapper", mock(SysRoleDeptMapper.class));
        users = proxy(userTarget); roles = proxy(roleTarget);
    }

    @Test void profileSaveKeepsDisabledAndOnlyChangesName()
    {
        users.updateUser(user(100L, 20L, 30L)); assertThat(ids(100L)).containsExactly(20L, 30L);
        assertThat(jdbc.queryForObject("select nick_name from sys_user where user_id=100", String.class)).isEqualTo("edited");
    }

    @Test void omittedRolesDoNotClearExistingDisabledMembership()
    {
        SysUser user = user(100L); user.setRoleIds(null); users.updateUser(user);
        assertThat(ids(100L)).containsExactly(20L, 30L);
    }

    @Test void mixedDisabledAdditionIsRejectedWithoutDeletingOrChangingProfile()
    {
        assertThatThrownBy(() -> users.updateUser(user(200L, 20L, 30L))).isInstanceOf(ServiceException.class);
        assertThat(ids(200L)).isEmpty();
        assertThat(jdbc.queryForObject("select nick_name from sys_user where user_id=200", String.class)).isEqualTo("other");
    }

    @Test void newUserCannotBeInsertedWithDisabledRole()
    {
        assertThatThrownBy(() -> users.insertUser(user(null, 30L))).isInstanceOf(ServiceException.class);
        assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isEqualTo(2);
        assertThat(ids(300L)).isEmpty();
    }

    @Test void newUserEnabledAssignmentIsDeduplicated()
    {
        users.insertUser(user(null, 40L, 20L, 40L)); assertThat(ids(300L)).containsExactly(20L, 40L);
    }

    @Test void unbindingDisabledRoleIsAllowedButReaddingIsRejected()
    {
        SysUserRole binding = new SysUserRole(); binding.setUserId(100L); binding.setRoleId(30L);
        assertThat(roles.deleteAuthUser(binding)).isEqualTo(1);
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 20L, 30L })).isInstanceOf(ServiceException.class);
        assertThat(ids(100L)).containsExactly(20L);
    }

    @Test void bulkDisabledCannotPartiallyAddNewMembers()
    {
        assertThatThrownBy(() -> roles.insertAuthUsers(30L, new Long[] { 100L, 200L })).isInstanceOf(ServiceException.class);
        assertThat(ids(100L)).contains(30L); assertThat(ids(200L)).isEmpty();
        assertThat(roles.insertAuthUsers(30L, new Long[] { 100L, 100L })).isEqualTo(1);
    }

    @Test void databaseFailureAfterDifferentialWriteRollsBackOriginalBindings()
    {
        doThrow(new ServiceException("synthetic after-write failure")).when(invalidation).record(anyLong(), anyString());
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 40L })).isInstanceOf(ServiceException.class);
        assertThat(ids(100L)).containsExactly(20L, 30L);
    }

    @Test void missingOrDeletedTargetsCannotProduceOrphanBindings()
    {
        jdbc.update("update sys_user set del_flag='2' where user_id=200");
        assertThatThrownBy(() -> roles.insertAuthUsers(20L, new Long[] { 200L })).isInstanceOf(ServiceException.class);
        jdbc.update("update sys_role set del_flag='2' where role_id=40");
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 40L })).isInstanceOf(ServiceException.class);
        assertThat(ids(100L)).containsExactly(20L, 30L); assertThat(ids(200L)).isEmpty();
    }

    @ParameterizedTest @ValueSource(booleans = { false, true })
    void disableCommittedFirstRejectsLaterGrant(boolean viaEdit) throws Exception
    {
        Object outcome = heldTransaction(() -> disable(viaEdit), () -> roles.insertAuthUsers(20L, new Long[] { 200L }));
        assertThat(outcome).isInstanceOf(ServiceException.class);
        assertThat(ids(200L)).isEmpty(); assertThat(ids(100L)).containsExactly(20L, 30L); disabled();
    }

    @ParameterizedTest @ValueSource(booleans = { false, true })
    void grantCommittedFirstRemainsExistingAfterDisable(boolean viaEdit) throws Exception
    {
        Object outcome = heldTransaction(() -> users.insertUserAuth(200L, new Long[] { 20L }), () -> disable(viaEdit));
        assertThat(outcome).isEqualTo("success"); assertThat(ids(200L)).containsExactly(20L); disabled();
        assertThatThrownBy(() -> users.insertUserAuth(200L, new Long[] { 20L, 30L })).isInstanceOf(ServiceException.class);
        assertThat(ids(200L)).containsExactly(20L);
    }

    @Test void onboardingOrderWithoutGlobalMutexStillSerializesDisableAndInvalidatesNewMember() throws Exception
    {
        AtomicReference<List<Long>> affected = new AtomicReference<>();
        doAnswer(call -> { affected.set(new ArrayList<>(call.getArgument(0))); return null; })
                .when(invalidation).recordAll(anyCollection(), anyString());
        Object outcome = heldTransaction(() -> {
            assertThat(roleMapper.selectRoleByIdForUpdate(20L).getStatus()).isEqualTo("0");
            relations.lockUserForRoleAssignment(200L);
            assertThat(relations.insertUserRoleIfAbsent(200L, 20L)).isEqualTo(1);
        }, () -> disable(false));
        assertThat(outcome).isEqualTo("success"); assertThat(affected.get()).contains(100L, 200L);
        assertThat(ids(200L)).containsExactly(20L); disabled();
    }

    private void disabled() { assertThat(jdbc.queryForObject("select status from sys_role where role_id=20", String.class)).isEqualTo("1"); }
    private void disable(boolean edit)
    {
        SysRole role = new SysRole(20L); role.setStatus("1"); role.setDataScope("1");
        if (edit) roles.updateRole(role); else roles.updateRoleStatus(role);
    }
    private List<Long> ids(Long userId) { return jdbc.queryForList("select role_id from sys_user_role where user_id=? order by role_id", Long.class, userId); }

    private Object heldTransaction(Runnable first, Runnable second) throws Exception
    {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        try
        {
            Future<?> firstResult = executor.submit(() -> new TransactionTemplate(manager).execute(status -> {
                first.run(); held.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Object> secondResult = executor.submit(() -> {
                started.countDown();
                try { second.run(); return "success"; } catch (Exception result) { return result; }
            });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> secondResult.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); firstResult.get(10, TimeUnit.SECONDS); return secondResult.get(10, TimeUnit.SECONDS);
        }
        finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }
    static class ScopedUsers extends SysUserServiceImpl {
        @Override public void checkUserAllowed(SysUser user) { }
        @Override public void checkUserDataScope(Long userId) { }
    }
    static class ScopedRoles extends SysRoleServiceImpl {
        @Override public void checkRoleAllowed(SysRole role) { }
        @Override public void checkRoleDataScope(Long... roleIds) { }
    }
    @SuppressWarnings("unchecked") static <T> T proxy(T target)
    {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private static SysUser user(Long id, Long... roleIds)
    { SysUser user = new SysUser(id); user.setRoleIds(roleIds); user.setNickName("edited"); user.setStatus("0"); return user; }
}
