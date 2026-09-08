package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.dto.SysUserShopScopeChangeRequest;
import com.erp.system.domain.vo.SysUserShopScopePreviewVo;
import com.erp.system.domain.vo.SysUserShopScopeVo;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.service.impl.SysUserShopScopeConflictException;

@DisplayName("用户店铺授权控制器")
class SysUserShopControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("查询用户授权返回可编辑和需保留店铺元数据")
    void getInfoShouldReturnEditableAndPreservedScopeMetadata()
    {
        setLoginUser(9L, "shop-operator", false);
        AtomicReference<Long> checkedUserId = new AtomicReference<>();
        SysUserShopScopeVo scope = new SysUserShopScopeVo();
        scope.setShopIds(Arrays.asList(202L, 999L));
        scope.setEditableShopIds(Arrays.asList(202L));
        scope.setPreservedShopIds(Arrays.asList(999L));
        scope.setOutOfScopeCount(1);
        SysUserShopController controller = new SysUserShopController();
        ReflectionTestUtils.setField(controller, "userService", userService(checkedUserId));
        ReflectionTestUtils.setField(controller, "userShopService", userShopService(scope));

        AjaxResult result = controller.getInfo(20L);

        assertThat(checkedUserId.get()).isEqualTo(20L);
        assertThat(result.get("shopIds")).isEqualTo(Arrays.asList(202L, 999L));
        assertThat(result.get("editableShopIds")).isEqualTo(Arrays.asList(202L));
        assertThat(result.get("preservedShopIds")).isEqualTo(Arrays.asList(999L));
        assertThat(result.get("outOfScopeCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("预览和保存接口同时要求查询与编辑权限")
    void previewAndSaveShouldRequireQueryAndEditPermissions() throws Exception
    {
        assertPermissions(SysUserShopController.class.getMethod("preview", Long.class,
                SysUserShopScopeChangeRequest.class));
        Method save = SysUserShopController.class.getMethod("save", Long.class,
                SysUserShopScopeChangeRequest.class);
        assertPermissions(save);
        com.erp.common.log.annotation.Log log = save.getAnnotation(com.erp.common.log.annotation.Log.class);
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    @DisplayName("保存版本冲突返回稳定409业务码")
    void saveConflictShouldReturnStableBusinessCode()
    {
        setLoginUser(9L, "shop-operator", false);
        AtomicReference<Long> checkedUserId = new AtomicReference<>();
        SysUserShopController controller = new SysUserShopController();
        ReflectionTestUtils.setField(controller, "userService", userService(checkedUserId));
        ReflectionTestUtils.setField(controller, "userShopService", conflictUserShopService());
        SysUserShopScopeChangeRequest request = new SysUserShopScopeChangeRequest();
        request.setShopDeptIds(Collections.singletonList(202L));
        request.setScopeVersion("a".repeat(64));

        AjaxResult result = controller.save(20L, request);

        assertThat(checkedUserId.get()).isEqualTo(20L);
        assertThat(result.get("code")).isEqualTo(409);
        assertThat(result.get("businessCode")).isEqualTo("USER_SHOP_SCOPE_CONFLICT");
    }

    @Test
    @DisplayName("预览接口返回服务端标准化结果和版本")
    void previewShouldReturnAuthoritativeScopeResult()
    {
        setLoginUser(9L, "shop-operator", false);
        AtomicReference<Long> checkedUserId = new AtomicReference<>();
        SysUserShopScopePreviewVo preview = new SysUserShopScopePreviewVo();
        preview.setNormalizedShopIds(Collections.singletonList(202L));
        preview.setScopeVersion("b".repeat(64));
        SysUserShopController controller = new SysUserShopController();
        ReflectionTestUtils.setField(controller, "userService", userService(checkedUserId));
        ReflectionTestUtils.setField(controller, "userShopService", previewUserShopService(preview));
        SysUserShopScopeChangeRequest request = new SysUserShopScopeChangeRequest();
        request.setShopDeptIds(Arrays.asList(202L, 202L));

        AjaxResult result = controller.preview(20L, request);

        assertThat(checkedUserId.get()).isEqualTo(20L);
        assertThat(result.get("data")).isSameAs(preview);
    }

    private static void setLoginUser(Long userId, String username, boolean admin)
    {
        SecurityContextHolder.setUserId(String.valueOf(userId));
        SecurityContextHolder.setUserName(username);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername(username);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(username);
        loginUser.setSysUser(user);
        loginUser.setRoles(admin ? Collections.singleton("admin") : Collections.singleton("user"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static ISysUserService userService(AtomicReference<Long> checkedUserId)
    {
        return (ISysUserService) Proxy.newProxyInstance(ISysUserService.class.getClassLoader(),
                new Class<?>[] { ISysUserService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    if ("checkUserDataScope".equals(method.getName()))
                    {
                        checkedUserId.set((Long) args[0]);
                        return null;
                    }
                    throw new AssertionError("Unexpected user service call: " + method.getName());
                });
    }

    private static ISysUserShopService userShopService(SysUserShopScopeVo scope)
    {
        return (ISysUserShopService) Proxy.newProxyInstance(ISysUserShopService.class.getClassLoader(),
                new Class<?>[] { ISysUserShopService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class)
                    {
                        return method.invoke(new Object(), args);
                    }
                    if ("selectUserShopScope".equals(method.getName()))
                    {
                        assertThat(args[0]).isEqualTo(20L);
                        assertThat(args[1]).isEqualTo(9L);
                        assertThat(args[2]).isEqualTo(false);
                        return scope;
                    }
                    throw new AssertionError("Unexpected user shop service call: " + method.getName());
                });
    }

    private static ISysUserShopService conflictUserShopService()
    {
        return (ISysUserShopService) Proxy.newProxyInstance(ISysUserShopService.class.getClassLoader(),
                new Class<?>[] { ISysUserShopService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return method.invoke(new Object(), args);
                    if ("savePreviewedUserShops".equals(method.getName()))
                    {
                        assertThat(args[0]).isEqualTo(20L);
                        assertThat(args[1]).isEqualTo(Collections.singletonList(202L));
                        assertThat(args[2]).isEqualTo("a".repeat(64));
                        assertThat(args[3]).isEqualTo("shop-operator");
                        assertThat(args[4]).isEqualTo(9L);
                        assertThat(args[5]).isEqualTo(false);
                        throw new SysUserShopScopeConflictException();
                    }
                    throw new AssertionError("Unexpected user shop service call: " + method.getName());
                });
    }

    private static ISysUserShopService previewUserShopService(SysUserShopScopePreviewVo preview)
    {
        return (ISysUserShopService) Proxy.newProxyInstance(ISysUserShopService.class.getClassLoader(),
                new Class<?>[] { ISysUserShopService.class }, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return method.invoke(new Object(), args);
                    if ("previewUserShops".equals(method.getName()))
                    {
                        assertThat(args[0]).isEqualTo(20L);
                        assertThat(args[1]).isEqualTo(Arrays.asList(202L, 202L));
                        assertThat(args[2]).isEqualTo(9L);
                        assertThat(args[3]).isEqualTo(false);
                        return preview;
                    }
                    throw new AssertionError("Unexpected user shop service call: " + method.getName());
                });
    }

    private static void assertPermissions(Method method)
    {
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        Set<String> permissions = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        assertThat(permissions).containsExactlyInAnyOrder("system:userShop:query", "system:userShop:edit");
    }
}
