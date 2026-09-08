package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.system.api.domain.SysUser;
import com.erp.system.config.DriveFeatureProperties;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.FrontendFeatureCatalog;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("用户授权角色读取范围")
class SysUserControllerAuthRoleScopeTest
{
    @Test
    @DisplayName("读取授权角色前先校验目标用户数据范围")
    void authRoleShouldCheckUserDataScopeBeforeReadingUserOrRoles()
    {
        List<String> events = new java.util.ArrayList<>();
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "userService", userService(events));
        ReflectionTestUtils.setField(controller, "roleService", roleService(events));

        Throwable thrown = catchThrowable(() -> controller.authRole(202L));

        assertThat(thrown)
                .isInstanceOf(ServiceException.class)
                .hasMessage("没有权限访问用户数据！");
        assertThat(events).containsExactly("checkUserDataScope:202");
    }

    @Test
    @DisplayName("登录信息显式返回与角色通配权限无关的云盘开关")
    void getInfoShouldExposeExplicitDriveFeatureStateForOrdinaryAndWildcardUsers()
    {
        SysUserController controller = new SysUserController();
        DriveFeatureProperties properties = new DriveFeatureProperties();
        ReflectionTestUtils.setField(controller, "driveFeatureProperties", properties);

        AjaxResult ordinary = AjaxResult.success();
        properties.setEnabled(false);
        controller.putDriveFeatureState(ordinary);

        AjaxResult wildcardAdmin = AjaxResult.success();
        properties.setEnabled(true);
        controller.putDriveFeatureState(wildcardAdmin);

        assertThat(ordinary.get("driveEnabled")).isEqualTo(false);
        assertThat(wildcardAdmin.get("driveEnabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("登录业务能力在依赖缺失时默认全关并按服务结果显式返回")
    void getInfoShouldExposeFailClosedBusinessFeatureState()
    {
        SysUserController controller = new SysUserController();
        AjaxResult unavailable = AjaxResult.success();

        controller.putBusinessFeatureState(unavailable);

        Map<?, ?> unavailableFeatures =
                (Map<?, ?>) unavailable.get("businessFeatures");
        assertThat(unavailableFeatures)
                .isEqualTo(BusinessFeatureGate.disabledCapabilities());
        assertThat(unavailableFeatures.containsKey("team")).isFalse();
        assertThat(unavailableFeatures.get("customerServiceCard"))
                .isEqualTo(false);

        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        Map<String, Boolean> capabilities = new LinkedHashMap<>(
                BusinessFeatureGate.disabledCapabilities());
        capabilities.put("noticeWorkflow", true);
        when(gate.capabilities()).thenReturn(capabilities);
        ReflectionTestUtils.setField(controller, "businessFeatureGate", gate);
        AjaxResult available = AjaxResult.success();

        controller.putBusinessFeatureState(available);

        Map<?, ?> availableFeatures =
                (Map<?, ?>) available.get("businessFeatures");
        assertThat(availableFeatures.get("noticeWorkflow")).isEqualTo(true);
        assertThat(availableFeatures.containsKey("team")).isFalse();
        assertThat(availableFeatures.get("healthCertificate"))
                .isEqualTo(false);
    }

    @Test
    @DisplayName("登录信息功能目录依赖后端显式允许列表且缺少依赖时全关")
    @SuppressWarnings("unchecked")
    void getInfoShouldExposeFailClosedFrontendFeatureCatalog()
    {
        SysUserController controller = new SysUserController();
        AjaxResult unavailable = AjaxResult.success();

        controller.putFrontendFeatureState(unavailable, Set.of("inventory:transfer:list"));

        assertThat(unavailable.get("frontendPermissions")).isEqualTo(Set.of());
        assertThat(unavailable.get("frontendFeatures")).isEqualTo(List.of());

        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        FrontendFeatureCatalog catalog = new FrontendFeatureCatalog(gate);
        ReflectionTestUtils.setField(controller, "frontendFeatureCatalog", catalog);
        AjaxResult available = AjaxResult.success();

        controller.putFrontendFeatureState(available, Set.of("inventory:transfer:list"));

        Set<String> frontendPermissions =
                (Set<String>) available.get("frontendPermissions");
        List<String> frontendFeatures =
                (List<String>) available.get("frontendFeatures");
        assertThat(frontendPermissions)
                .contains("workbench:view", "tasks:view", "inventory:transfer:list");
        assertThat(frontendFeatures)
                .contains("workbench.view", "tasks.view", "messages.view",
                        "inventory.transfer.view")
                .doesNotContain("inventory.transfer.create");
    }

    private static ISysUserService userService(List<String> events)
    {
        return mapper(ISysUserService.class, (method, args) -> {
            if ("checkUserDataScope".equals(method))
            {
                events.add("checkUserDataScope:" + args[0]);
                throw new ServiceException("没有权限访问用户数据！");
            }
            if ("selectUserById".equals(method))
            {
                events.add("selectUserById:" + args[0]);
                SysUser user = new SysUser();
                user.setUserId((Long) args[0]);
                return user;
            }
            throw unexpected(method);
        });
    }

    private static ISysRoleService roleService(List<String> events)
    {
        return mapper(ISysRoleService.class, (method, args) -> {
            if ("selectRolesByUserId".equals(method))
            {
                events.add("selectRolesByUserId:" + args[0]);
                return Collections.emptyList();
            }
            throw unexpected(method);
        });
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
