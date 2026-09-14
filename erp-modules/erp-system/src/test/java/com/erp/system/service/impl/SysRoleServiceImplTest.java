package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.SysUserRole;
import com.erp.system.domain.SysRoleMenu;
import com.erp.system.mapper.SysRoleDeptMapper;
import com.erp.system.mapper.SysConfigMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRoleMenuMapper;
import com.erp.system.mapper.SysUserRoleMapper;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.UserSessionInvalidationService;

@DisplayName("角色业务服务")
class SysRoleServiceImplTest
{
    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("200");
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, new LoginUser());
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("禁止操作roleKey为admin的角色")
    void checkRoleAllowedShouldRejectAdminRoleKey()
    {
        SysRole adminRole = new SysRole();
        adminRole.setRoleId(88L);
        adminRole.setRoleKey("admin");
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRoleById".equals(method))
            {
                return adminRole;
            }
            throw unexpected(method);
        });
        SysRoleServiceImpl service = newService(roleMapper);

        assertThatThrownBy(() -> service.checkRoleAllowed(new SysRole(88L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超级管理员角色");
    }

    @Test
    @DisplayName("授权用户前校验角色范围和所有目标用户范围")
    void insertAuthUsersShouldValidateRoleAndEachTargetUserBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, 202L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        assertThatThrownBy(() -> service.insertAuthUsers(20L, new Long[] { 101L, 202L }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问用户数据");

        assertThat(events).containsExactly("role:20", "user:101", "user:202");
    }

    @Test
    @DisplayName("批量取消授权用户前校验角色范围和所有目标用户范围")
    void deleteAuthUsersShouldValidateRoleAndEachTargetUserBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, 202L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        assertThatThrownBy(() -> service.deleteAuthUsers(20L, new Long[] { 101L, 202L }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问用户数据");

        assertThat(events).containsExactly("role:20", "user:101", "user:202");
    }

    @Test
    @DisplayName("取消授权单个用户前校验角色范围和目标用户范围")
    void deleteAuthUserShouldValidateRoleAndTargetUserBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, 202L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(20L);
        userRole.setUserId(202L);

        assertThatThrownBy(() -> service.deleteAuthUser(userRole))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问用户数据");

        assertThat(events).containsExactly("role:20", "user:202");
    }

    @Test
    @DisplayName("给配置HR新增角色后触发托管权限同步")
    void insertAuthUsersShouldSyncManagedSignHrPermissions()
    {
        assertThat(Arrays.stream(SysRoleServiceImpl.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("configMapper");
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        service.insertAuthUsers(20L, new Long[] { 88L });

        assertThat(events).containsExactly("role:20", "user:88", "lock:signHrState",
                "lock:role:20", "lock:user:88", "write:batchUserRole", "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("移除配置HR角色后触发托管权限同步")
    void deleteAuthUserShouldSyncManagedSignHrPermissions()
    {
        assertThat(Arrays.stream(SysRoleServiceImpl.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("configMapper");
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        service.deleteAuthUser(userRole(88L, 20L));

        assertThat(events).containsExactly("role:20", "user:88", "lock:signHrState",
                "write:deleteUserRoleInfo", "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("授权用户不能修改超级管理员角色且不会获取配置锁")
    void authUsersShouldRejectSuperAdminRoleBeforeLocking()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        assertThatThrownBy(() -> service.insertAuthUsers(1L, new Long[] { 88L }))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超级管理员角色");

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("修改角色删除并重建菜单后触发唯一HR专用角色恢复同步")
    void updateRoleShouldSyncManagedSignHrPermissionsAfterMenuRebuild()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleWriteMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setDataScope("5");
        role.setMenuIds(new Long[] { 4600L, 4601L });

        service.updateRole(role);

        assertThat(events).containsExactly("lock:signHrState", "write:updateRole", "write:deleteRoleMenuByRoleId",
                "write:batchRoleMenu", "write:deleteRoleDeptByRoleId", "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("角色主记录更新失败时不能重建菜单和部门关联")
    void updateRoleShouldStopWhenMainRowWasConcurrentlyRemoved()
    {
        List<String> events = new ArrayList<>();
        SysRoleMapper missingRoleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("updateRole".equals(method))
            {
                events.add("write:updateRole");
                return 0;
            }
            throw unexpected(method);
        });
        SysRoleServiceImpl service = newService(missingRoleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setDataScope("5");
        role.setMenuIds(new Long[] { 4600L });

        assertThatThrownBy(() -> service.updateRole(role))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已被删除或修改");

        assertThat(events).containsExactly("lock:signHrState", "write:updateRole");
    }

    @Test
    @DisplayName("禁用角色后触发唯一HR专用角色恢复同步")
    void updateRoleStatusShouldSyncManagedSignHrPermissions()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleWriteMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setStatus("1");

        service.updateRoleStatus(role);

        assertThat(events).containsExactly("lock:signHrState", "write:updateRole",
                "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("修改角色数据范围后触发唯一HR专用角色窄范围恢复同步")
    void authDataScopeShouldSyncManagedSignHrPermissions()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleWriteMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setDataScope("5");
        role.setDeptIds(new Long[0]);

        service.authDataScope(role);

        assertThat(events).containsExactly("lock:signHrState", "write:updateRole", "write:deleteRoleDeptByRoleId",
                "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("直接删除角色后触发唯一HR专用角色重建同步")
    void deleteRoleByIdShouldSyncManagedSignHrPermissions()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleLifecycleMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        service.deleteRoleById(20L);

        assertThat(events).containsExactly("lock:signHrState", "write:deleteRoleMenuByRoleId", "write:deleteRoleDeptByRoleId",
                "write:deleteRoleById", "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("批量删除角色后触发唯一HR专用角色重建同步")
    void deleteRoleByIdsShouldSyncManagedSignHrPermissions()
    {
        SecurityContextHolder.setUserId("1");
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleLifecycleMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));

        service.deleteRoleByIds(new Long[] { 20L });

        assertThat(events).containsExactly("lock:signHrState", "read:selectRoleById", "read:selectRoleById",
                "read:countUserRoleByRoleId", "write:deleteRoleMenu", "write:deleteRoleDept", "write:deleteRoleByIds",
                "write:syncSignHrPermissions");
    }

    @Test
    @DisplayName("授权用户拒绝无效参数且不写入")
    void insertAuthUsersShouldRejectInvalidInputsBeforeWriting()
    {
        assertInvalidAuthCallDoesNotWrite(service -> service.insertAuthUsers(null, new Long[] { 101L }), "角色ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.insertAuthUsers(20L, null), "用户ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.insertAuthUsers(20L, new Long[] {}), "用户ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.insertAuthUsers(20L, new Long[] { 101L, null }),
                "用户ID不能为空");
    }

    @Test
    @DisplayName("批量取消授权用户拒绝无效参数且不写入")
    void deleteAuthUsersShouldRejectInvalidInputsBeforeWriting()
    {
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUsers(null, new Long[] { 101L }), "角色ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUsers(20L, null), "用户ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUsers(20L, new Long[] {}), "用户ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUsers(20L, new Long[] { 101L, null }),
                "用户ID不能为空");
    }

    @Test
    @DisplayName("取消授权单个用户拒绝无效参数且不写入")
    void deleteAuthUserShouldRejectInvalidInputsBeforeWriting()
    {
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUser(null), "授权关系不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUser(userRole(101L, null)), "角色ID不能为空");
        assertInvalidAuthCallDoesNotWrite(service -> service.deleteAuthUser(userRole(null, 20L)), "用户ID不能为空");
    }

    @Test
    @DisplayName("修改角色数据范围前校验所有目标部门范围")
    void authDataScopeShouldValidateEachDeptBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = newService(roleWriteMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "deptService", deptScopeService(events, 202L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setDataScope("2");
        role.setDeptIds(new Long[] { 101L, 202L });

        assertThatThrownBy(() -> service.authDataScope(role))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("没有权限访问部门数据");

        assertThat(events).containsExactly("lock:signHrState", "dept:101", "dept:202");
        assertThat(events).noneMatch(event -> event.startsWith("write:"));
    }

    @Test
    @DisplayName("新增角色缺少数据范围时显式写入本部门及以下")
    void insertRoleShouldPersistExplicitSafeDefaultDataScope()
    {
        List<String> events = new ArrayList<>();
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("insertRole".equals(method))
            {
                SysRole inserted = (SysRole) args[0];
                events.add("write:role:" + inserted.getDataScope());
                inserted.setRoleId(77L);
                return 1;
            }
            throw unexpected(method);
        });
        SysRoleServiceImpl service = newService(roleMapper);
        SysRole role = new SysRole();
        role.setMenuIds(new Long[0]);

        int rows = service.insertRole(role);

        assertThat(rows).isEqualTo(1);
        assertThat(role.getRoleId()).isEqualTo(77L);
        assertThat(role.getDataScope()).isEqualTo("4");
        assertThat(events).containsExactly("write:role:4");
    }

    @Test
    @DisplayName("新增自定义范围角色在角色菜单同一事务方法内校验并写入部门关系")
    void insertCustomScopeRoleShouldWriteRoleMenusAndDepartmentsTogether()
    {
        List<String> events = new ArrayList<>();
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("insertRole".equals(method))
            {
                SysRole inserted = (SysRole) args[0];
                inserted.setRoleId(78L);
                events.add("write:insertRole");
                return 1;
            }
            throw unexpected(method);
        });
        SysRoleServiceImpl service = newService(roleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "deptService", deptScopeService(events, -1L));
        SysRole role = new SysRole();
        role.setDataScope("2");
        role.setMenuIds(new Long[] { 11L, 12L });
        role.setDeptIds(new Long[] { 101L, 102L });

        service.insertRole(role);

        assertThat(events).containsExactly("dept:101", "dept:102", "write:insertRole",
                "write:batchRoleMenu", "write:batchRoleDept");
    }

    @Test
    @DisplayName("新增角色拒绝未知数据范围且不写数据库")
    void insertRoleShouldRejectUnknownDataScopeBeforeWriting()
    {
        List<String> events = new ArrayList<>();
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            events.add("write:" + method);
            return 1;
        });
        SysRoleServiceImpl service = newService(roleMapper);
        SysRole role = new SysRole();
        role.setDataScope("9");
        role.setMenuIds(new Long[0]);

        assertThatThrownBy(() -> service.insertRole(role))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("数据权限范围不正确");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("角色菜单变化为所有受影响用户写入会话失效任务")
    void updateRoleShouldInvalidateSessionsForAffectedUsers()
    {
        List<String> events = new ArrayList<>();
        SysUserRoleMapper affectedUsers = mapper(SysUserRoleMapper.class, (method, args) -> {
            if ("selectUserIdsByRoleId".equals(method))
            {
                assertThat(args[0]).isEqualTo(20L);
                return List.of(88L, 99L);
            }
            throw unexpected(method);
        });
        UserSessionInvalidationService invalidationService = mock(UserSessionInvalidationService.class);
        SysRoleServiceImpl service = newService(roleWriteMapper(events), affectedUsers,
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper(events));
        ReflectionTestUtils.setField(service, "roleDeptMapper", roleDeptMapper(events));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        ReflectionTestUtils.setField(service, "userSessionInvalidationService", invalidationService);
        SysRole role = new SysRole();
        role.setRoleId(20L);
        role.setDataScope("5");
        role.setMenuIds(new Long[] { 4600L });

        service.updateRole(role);

        verify(invalidationService).recordAll(List.of(88L, 99L),
                UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
    }

    @Test
    @DisplayName("批量授予角色为目标用户写入会话失效任务")
    void insertAuthUsersShouldInvalidateTargetUserSessions()
    {
        List<String> events = new ArrayList<>();
        UserSessionInvalidationService invalidationService = mock(UserSessionInvalidationService.class);
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));
        ReflectionTestUtils.setField(service, "configMapper", configMapper(events));
        ReflectionTestUtils.setField(service, "userSessionInvalidationService", invalidationService);

        service.insertAuthUsers(20L, new Long[] { 88L, 99L });

        verify(invalidationService).recordAll(List.of(88L, 99L),
                UserSessionInvalidationService.ROLE_GRANTS_CHANGED);
    }

    private static SysRoleServiceImpl newService(SysRoleMapper roleMapper)
    {
        return newService(roleMapper, mapper(SysUserRoleMapper.class, (method, args) -> {
            throw unexpected(method);
        }), mapper(ISysUserService.class, (method, args) -> {
            throw unexpected(method);
        }));
    }

    private static SysRoleServiceImpl newService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
            ISysUserService userService)
    {
        SysRoleServiceImpl service = new SysRoleServiceImpl();
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", mapper(SysRoleMenuMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "roleDeptMapper", mapper(SysRoleDeptMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "userSessionInvalidationService",
                mock(UserSessionInvalidationService.class));
        return service;
    }

    private static SysRoleServiceImpl proxiedService(SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
            ISysUserService userService)
    {
        ProxyFactory proxyFactory = new ProxyFactory(newService(roleMapper, userRoleMapper, userService));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.setExposeProxy(true);
        return (SysRoleServiceImpl) proxyFactory.getProxy();
    }

    private static void assertInvalidAuthCallDoesNotWrite(InvalidAuthCall call, String message)
    {
        List<String> events = new ArrayList<>();
        SysRoleServiceImpl service = proxiedService(roleScopeMapper(events), userRoleMapper(events),
                userScopeService(events, -1L));

        assertThatThrownBy(() -> call.invoke(service))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(message);

        assertThat(events).noneMatch(event -> event.startsWith("write:"));
    }

    private static SysUserRole userRole(Long userId, Long roleId)
    {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    private static SysRoleMapper roleScopeMapper(List<String> events)
    {
        return mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRoleList".equals(method))
            {
                SysRole query = (SysRole) args[0];
                events.add("role:" + query.getRoleId());
                SysRole role = new SysRole();
                role.setRoleId(query.getRoleId());
                return Collections.singletonList(role);
            }
            if ("selectRoleById".equals(method))
            {
                SysRole role = new SysRole();
                role.setRoleId((Long) args[0]);
                role.setRoleKey(Long.valueOf(1L).equals(args[0]) ? "admin" : "common-role");
                return role;
            }
            if ("selectRoleByIdForUpdate".equals(method))
            {
                events.add("lock:role:" + args[0]);
                SysRole role = new SysRole((Long) args[0]);
                role.setStatus("0"); role.setDelFlag("0");
                return role;
            }
            throw unexpected(method);
        });
    }

    private static SysRoleMapper roleWriteMapper(List<String> events)
    {
        return mapper(SysRoleMapper.class, (method, args) -> {
            if ("updateRole".equals(method))
            {
                events.add("write:updateRole");
                return 1;
            }
            throw unexpected(method);
        });
    }

    private static SysRoleMapper roleLifecycleMapper(List<String> events)
    {
        return mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRoleById".equals(method))
            {
                events.add("read:selectRoleById");
                SysRole role = new SysRole();
                role.setRoleId((Long) args[0]);
                role.setRoleName("普通角色");
                role.setRoleKey("common-role");
                return role;
            }
            if ("deleteRoleById".equals(method) || "deleteRoleByIds".equals(method))
            {
                events.add("write:" + method);
                return 1;
            }
            throw unexpected(method);
        });
    }

    private static SysRoleDeptMapper roleDeptMapper(List<String> events)
    {
        return mapper(SysRoleDeptMapper.class, (method, args) -> {
            if ("deleteRoleDeptByRoleId".equals(method) || "batchRoleDept".equals(method)
                    || "deleteRoleDept".equals(method))
            {
                events.add("write:" + method);
                return 1;
            }
            throw unexpected(method);
        });
    }

    private static SysRoleMenuMapper roleMenuMapper(List<String> events)
    {
        return mapper(SysRoleMenuMapper.class, (method, args) -> {
            if ("deleteRoleMenuByRoleId".equals(method) || "batchRoleMenu".equals(method)
                    || "deleteRoleMenu".equals(method))
            {
                events.add("write:" + method);
                if ("batchRoleMenu".equals(method))
                {
                    @SuppressWarnings("unchecked")
                    List<SysRoleMenu> rows = (List<SysRoleMenu>) args[0];
                    return rows.size();
                }
                return 1;
            }
            throw unexpected(method);
        });
    }

    private static SysUserRoleMapper userRoleMapper(List<String> events)
    {
        return mapper(SysUserRoleMapper.class, (method, args) -> {
            if ("batchUserRole".equals(method) || "deleteUserRoleInfos".equals(method)
                    || "deleteUserRoleInfo".equals(method))
            {
                events.add("write:" + method);
                return 1;
            }
            if ("countUserRoleByRoleId".equals(method))
            {
                events.add("read:" + method);
                return 0;
            }
            if ("selectUserIdsByRoleId".equals(method))
            {
                return Collections.emptyList();
            }
            if ("lockUserForRoleAssignment".equals(method))
            {
                events.add("lock:user:" + args[0]); return args[0];
            }
            if ("selectRoleIdsByUserId".equals(method)) return Collections.emptyList();
            throw unexpected(method);
        });
    }

    private static SysConfigMapper configMapper(List<String> events)
    {
        return mapper(SysConfigMapper.class, (method, args) -> {
            if ("lockSignHrState".equals(method))
            {
                events.add("lock:signHrState");
                return 1L;
            }
            if ("syncSignHrPermissions".equals(method))
            {
                events.add("write:" + method);
                return null;
            }
            throw unexpected(method);
        });
    }

    private static ISysUserService userScopeService(List<String> events, Long outOfScopeUserId)
    {
        return mapper(ISysUserService.class, (method, args) -> {
            if ("checkUserDataScope".equals(method))
            {
                Long userId = (Long) args[0];
                events.add("user:" + userId);
                if (outOfScopeUserId.equals(userId))
                {
                    throw new ServiceException("没有权限访问用户数据！");
                }
                return null;
            }
            throw unexpected(method);
        });
    }

    private static ISysDeptService deptScopeService(List<String> events, Long outOfScopeDeptId)
    {
        return mapper(ISysDeptService.class, (method, args) -> {
            if ("checkDeptDataScope".equals(method))
            {
                Long deptId = (Long) args[0];
                events.add("dept:" + deptId);
                if (outOfScopeDeptId.equals(deptId))
                {
                    throw new ServiceException("没有权限访问部门数据！");
                }
                return null;
            }
            throw unexpected(method);
        });
    }

    @FunctionalInterface
    private interface InvalidAuthCall
    {
        void invoke(SysRoleServiceImpl service);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(thisProxy(type), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static Object thisProxy(Class<?> type)
    {
        return new Object()
        {
            @Override
            public String toString()
            {
                return type.getSimpleName() + "TestProxy";
            }
        };
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected call: " + method);
    }
}
