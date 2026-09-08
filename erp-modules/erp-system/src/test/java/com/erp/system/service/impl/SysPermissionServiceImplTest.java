package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.Constants;
import com.erp.common.core.constant.UserConstants;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.service.ISysMenuService;
import com.erp.system.service.ISysRoleService;

@DisplayName("系统权限服务")
class SysPermissionServiceImplTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.legacy-user-id-admin");
        System.clearProperty("erp.security.admin-role-bypass-enabled");
        System.clearProperty("erp.security.wildcard-permission-bypass-enabled");
    }

    @Test
    @DisplayName("admin角色默认不自动拥有所有菜单权限")
    void adminRoleShouldNotGrantAllPermissionsByDefault()
    {
        SysPermissionServiceImpl service = permissionService();
        SysUser user = new SysUser();
        user.setUserId(200L);
        SysRole role = new SysRole();
        role.setRoleId(88L);
        role.setRoleKey("admin");
        role.setStatus(UserConstants.ROLE_NORMAL);
        user.setRoles(Collections.singletonList(role));

        Set<String> permissions = service.getMenuPermission(user);

        assertThat(permissions).doesNotContain(Constants.ALL_PERMISSION);
    }

    @Test
    @DisplayName("1号用户默认拥有所有菜单权限")
    void userIdOneShouldGrantAllPermissionsByDefault()
    {
        SysPermissionServiceImpl service = permissionService();
        SysUser user = new SysUser();
        user.setUserId(1L);

        Set<String> permissions = service.getMenuPermission(user);

        assertThat(permissions).containsExactly(Constants.ALL_PERMISSION);
    }

    private static SysPermissionServiceImpl permissionService()
    {
        SysPermissionServiceImpl service = new SysPermissionServiceImpl();
        ReflectionTestUtils.setField(service, "roleService", mapper(ISysRoleService.class, (method, args) -> {
            throw unexpected(method);
        }));
        ReflectionTestUtils.setField(service, "menuService", mapper(ISysMenuService.class, (method, args) -> {
            if ("selectMenuPermsByRoleId".equals(method))
            {
                return Collections.emptySet();
            }
            throw unexpected(method);
        }));
        return service;
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, BiFunction<String, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class)
            {
                return method.invoke(new Object(), args);
            }
            return handler.apply(method.getName(), args == null ? new Object[0] : args);
        });
    }

    private static AssertionError unexpected(String method)
    {
        return new AssertionError("Unexpected service call: " + method);
    }
}
