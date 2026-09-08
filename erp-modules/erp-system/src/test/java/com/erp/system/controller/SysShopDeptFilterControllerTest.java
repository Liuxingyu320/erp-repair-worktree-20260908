package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.support.SysShopDeptFilterSupport;

@DisplayName("用户和角色控制器店铺请求头过滤")
class SysShopDeptFilterControllerTest
{
    @AfterEach
    void tearDown()
    {
        System.clearProperty("erp.security.legacy-user-id-admin");
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("用户列表店铺请求头授权后写入查询部门")
    void userFilterShouldSetDeptIdAfterScopeCheck()
    {
        setLoginUser(9L, Set.of("user"), Set.of("system:user:list"));
        List<ScopeCheck> checks = new ArrayList<>();
        SysShopDeptFilterSupport support = shopDeptFilterSupport(checks::add);
        SysUser user = new SysUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "101");

        support.applyTo(user, request);

        assertThat(user.getDeptId()).isEqualTo(101L);
        assertThat(checks).containsExactly(new ScopeCheck(9L, 101L, false));
    }

    @Test
    @DisplayName("角色授权用户列表店铺请求头授权后写入查询部门")
    void roleFilterShouldSetDeptIdAfterScopeCheck()
    {
        setLoginUser(9L, Set.of("user"), Set.of("system:role:list"));
        List<ScopeCheck> checks = new ArrayList<>();
        SysShopDeptFilterSupport support = shopDeptFilterSupport(checks::add);
        SysUser user = new SysUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "202");

        support.applyTo(user, request);

        assertThat(user.getDeptId()).isEqualTo(202L);
        assertThat(checks).containsExactly(new ScopeCheck(9L, 202L, false));
    }

    @Test
    @DisplayName("非授权店铺请求头拒绝并不写入查询部门")
    void shopHeaderShouldRejectUnauthorizedDept()
    {
        setLoginUser(9L, Set.of("user"), Set.of("system:user:list"));
        SysShopDeptFilterSupport support = shopDeptFilterSupport(check -> {
            throw new ServiceException("当前用户无权选择该店铺或仓库");
        });
        SysUser user = new SysUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "303");

        assertThatThrownBy(() -> support.applyTo(user, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前用户无权选择该店铺或仓库");
        assertThat(user.getDeptId()).isNull();
    }

    @Test
    @DisplayName("管理员店铺请求头不覆盖用户管理查询部门")
    void adminShopHeaderShouldNotOverrideUserQueryDept()
    {
        System.setProperty("erp.security.legacy-user-id-admin", "true");
        setLoginUser(1L, Set.of("admin"), Set.of("system:user:list"));
        List<ScopeCheck> checks = new ArrayList<>();
        SysShopDeptFilterSupport support = shopDeptFilterSupport(checks::add);
        SysUser user = new SysUser();
        user.setDeptId(202L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "505");

        support.applyTo(user, request);

        assertThat(user.getDeptId()).isEqualTo(202L);
        assertThat(checks).isEmpty();
    }

    @Test
    @DisplayName("空店铺请求头保持原查询条件且不触发授权校验")
    void emptyShopHeaderShouldNotCheckScope()
    {
        setLoginUser(9L, Set.of("user"), Set.of("system:user:list"));
        List<ScopeCheck> checks = new ArrayList<>();
        SysShopDeptFilterSupport support = shopDeptFilterSupport(checks::add);
        SysUser user = new SysUser();
        user.setDeptId(404L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        support.applyTo(user, request);

        assertThat(user.getDeptId()).isEqualTo(404L);
        assertThat(checks).isEmpty();
    }

    @Test
    @DisplayName("用户和角色控制器统一依赖店铺过滤支持类")
    void controllersShouldUseSharedShopDeptFilterSupport()
    {
        assertThat(hasFieldType(SysUserController.class, SysShopDeptFilterSupport.class)).isTrue();
        assertThat(hasFieldType(SysRoleController.class, SysShopDeptFilterSupport.class)).isTrue();
        assertThat(hasDeclaredMethod(SysUserController.class, "applyShopDeptFilter")).isFalse();
        assertThat(hasDeclaredMethod(SysRoleController.class, "applyShopDeptFilter")).isFalse();
    }

    private static SysShopDeptFilterSupport shopDeptFilterSupport(Consumer<ScopeCheck> checkConsumer)
    {
        SysShopDeptFilterSupport support = new SysShopDeptFilterSupport();
        ReflectionTestUtils.setField(support, "userShopService", userShopService(checkConsumer));
        return support;
    }

    private static boolean hasFieldType(Class<?> type, Class<?> fieldType)
    {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getType().equals(fieldType));
    }

    private static boolean hasDeclaredMethod(Class<?> type, String methodName)
    {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().equals(methodName));
    }

    private static void setLoginUser(Long userId, Set<String> roles, Set<String> permissions)
    {
        SecurityContextHolder.setUserId(String.valueOf(userId));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername("shop-filter-user");
        loginUser.setRoles(roles);
        loginUser.setPermissions(permissions);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("shop-filter-user");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static ISysUserShopService userShopService(Consumer<ScopeCheck> checkConsumer)
    {
        return (ISysUserShopService) Proxy.newProxyInstance(ISysUserShopService.class.getClassLoader(),
                new Class<?>[] { ISysUserShopService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    if ("checkUserShopScope".equals(method.getName()))
                    {
                        checkConsumer.accept(new ScopeCheck((Long) args[0], (Long) args[1], (Boolean) args[2]));
                        return null;
                    }
                    throw new AssertionError("Unexpected service call: " + method.getName());
                });
    }

    private record ScopeCheck(Long userId, Long deptId, boolean admin)
    {
    }
}
