package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysRole;
import com.erp.system.config.DriveFeatureProperties;
import com.erp.system.domain.SysMenu;
import com.erp.system.domain.dto.SysSortBatchRequest;
import com.erp.system.mapper.SysMenuMapper;
import com.erp.system.mapper.SysRoleMapper;
import com.erp.system.mapper.SysRoleMenuMapper;
import com.erp.system.service.support.UserSessionInvalidationService;

@DisplayName("菜单业务服务")
class SysMenuServiceImplTest
{
    @Test
    @DisplayName("拥有admin角色的用户可获取全量菜单树")
    void selectMenuTreeShouldUseAdminRoleKeyInsteadOfUserId()
    {
        AtomicBoolean selectedAll = new AtomicBoolean(false);
        SysMenuMapper menuMapper = mapper(SysMenuMapper.class, (method, args) -> {
            if ("selectMenuTreeAll".equals(method))
            {
                selectedAll.set(true);
                return Collections.emptyList();
            }
            if ("selectMenuTreeByUserId".equals(method))
            {
                throw new AssertionError("admin role user should not use user-scoped menu query");
            }
            throw unexpected(method);
        });
        SysRole adminRole = new SysRole();
        adminRole.setRoleKey("admin");
        SysRoleMapper roleMapper = mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRolePermissionByUserId".equals(method))
            {
                assertThat(args[0]).isEqualTo(42L);
                return List.of(adminRole);
            }
            throw unexpected(method);
        });
        SysMenuServiceImpl service = newService(menuMapper, roleMapper);

        List<SysMenu> menus = service.selectMenuTreeByUserId(42L);

        assertThat(menus).isEmpty();
        assertThat(selectedAll).isTrue();
    }

    @Test
    @DisplayName("云盘关闭时过滤运行时菜单和权限")
    void shouldFilterCloudDriveRuntimeMenuAndPermissionsWhenDisabled()
    {
        SysMenuMapper menuMapper = cloudDriveMapper();
        SysMenuServiceImpl service = newService(menuMapper, adminRoleMapper(), false);

        List<SysMenu> menus = service.selectMenuTreeByUserId(42L);
        Set<String> permissions = service.selectMenuPermsByUserId(42L);

        assertThat(menus).extracting(SysMenu::getPath).containsExactly("system");
        assertThat(permissions).containsExactly("system:user:list");
    }

    @Test
    @DisplayName("云盘开启时保留运行时菜单和权限")
    void shouldKeepCloudDriveRuntimeMenuAndPermissionsWhenEnabled()
    {
        SysMenuServiceImpl service = newService(cloudDriveMapper(), adminRoleMapper(), true);

        List<SysMenu> menus = service.selectMenuTreeByUserId(42L);
        Set<String> permissions = service.selectMenuPermsByUserId(42L);

        assertThat(menus).extracting(SysMenu::getPath).containsExactly("system", "drive");
        assertThat(permissions).containsExactlyInAnyOrder(
                "system:user:list", "drive:access", "drive:quota:manage");
    }

    @Test
    @DisplayName("已退役独立菜单即使数据库尚未清理也不会下发")
    void shouldHideRetiredBusinessMenusBeforeDatabaseCleanup()
    {
        SysMenuMapper menuMapper = mapper(SysMenuMapper.class, (method, args) -> {
            if ("selectMenuTreeAll".equals(method))
            {
                return List.of(
                        menu(100L, "system", "system/index"),
                        menu(101L, "team", "hr/team/index"),
                        menu(102L, "oe", "inventory/oeReplenishment/index"),
                        menu(103L, "customer", "inventory/customer/index"));
            }
            throw unexpected(method);
        });
        SysMenuServiceImpl service = newService(menuMapper, adminRoleMapper(), false);

        List<SysMenu> menus = service.selectMenuTreeByUserId(42L);

        assertThat(menus).extracting(SysMenu::getPath)
                .containsExactly("system", "customer");
    }

    @Test
    @DisplayName("菜单权限字符变化为继承用户写入会话失效任务")
    void updateMenuPermissionShouldInvalidateAffectedUserSessions()
    {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        UserSessionInvalidationService invalidationService = mock(UserSessionInvalidationService.class);
        SysMenu current = new SysMenu();
        current.setMenuId(100L);
        current.setPerms("system:user:list");
        current.setStatus("0");
        current.setMenuType("F");
        SysMenu update = new SysMenu();
        update.setMenuId(100L);
        update.setPerms("system:user:query");
        when(menuMapper.selectMenuById(100L)).thenReturn(current);
        when(roleMenuMapper.selectUserIdsByMenuId(100L)).thenReturn(List.of(88L, 99L));
        when(menuMapper.updateMenu(update)).thenReturn(1);
        SysMenuServiceImpl service = newService(menuMapper, adminRoleMapper());
        ReflectionTestUtils.setField(service, "roleMenuMapper", roleMenuMapper);
        ReflectionTestUtils.setField(service, "userSessionInvalidationService", invalidationService);

        service.updateMenu(update);

        verify(invalidationService).recordAll(List.of(88L, 99L),
                UserSessionInvalidationService.MENU_GRANTS_CHANGED);
    }

    private static SysMenuServiceImpl newService(SysMenuMapper menuMapper, SysRoleMapper roleMapper)
    {
        return newService(menuMapper, roleMapper, false);
    }

    private static SysMenuServiceImpl newService(SysMenuMapper menuMapper, SysRoleMapper roleMapper, boolean enabled)
    {
        SysMenuServiceImpl service = new SysMenuServiceImpl();
        ReflectionTestUtils.setField(service, "menuMapper", menuMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "roleMenuMapper", mapper(SysRoleMenuMapper.class, (method, args) -> {
            throw unexpected(method);
        }));
        DriveFeatureProperties properties = new DriveFeatureProperties();
        properties.setEnabled(enabled);
        ReflectionTestUtils.setField(service, "driveFeatureProperties", properties);
        ReflectionTestUtils.setField(service, "userSessionInvalidationService",
                mock(UserSessionInvalidationService.class));
        return service;
    }

    private static SysMenuMapper cloudDriveMapper()
    {
        return mapper(SysMenuMapper.class, (method, args) -> {
            if ("selectMenuTreeAll".equals(method))
            {
                return List.of(menu(100L, "system", "system/index"), menu(9600L, "drive", "drive/index"));
            }
            if ("selectMenuPermsByUserId".equals(method))
            {
                return List.of("system:user:list", "drive:access,drive:quota:manage");
            }
            throw unexpected(method);
        });
    }

    private static SysRoleMapper adminRoleMapper()
    {
        SysRole adminRole = new SysRole();
        adminRole.setRoleKey("admin");
        return mapper(SysRoleMapper.class, (method, args) -> {
            if ("selectRolePermissionByUserId".equals(method))
            {
                return List.of(adminRole);
            }
            throw unexpected(method);
        });
    }

    private static SysMenu menu(Long menuId, String path, String component)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(menuId);
        menu.setParentId(0L);
        menu.setPath(path);
        menu.setComponent(component);
        return menu;
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
