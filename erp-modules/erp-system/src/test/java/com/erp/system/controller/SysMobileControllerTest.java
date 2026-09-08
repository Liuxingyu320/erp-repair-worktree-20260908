package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.vo.MobileProfile;

@DisplayName("手机端当前用户入口")
class SysMobileControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("手机端 profile 只要求登录不要求后台管理权限")
    void profileShouldRequireLoginOnly() throws Exception
    {
        Method method = SysMobileController.class.getMethod("profile");

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
    }

    @Test
    @DisplayName("手机端仓库选项要求登录并依赖授权树过滤")
    void warehouseOptionsShouldRequireLogin() throws Exception
    {
        Method method = SysMobileController.class.getMethod("warehouseOptions", String.class, Integer.class);

        assertThat(method.getAnnotation(RequiresLogin.class)).isNotNull();
    }

    @Test
    @DisplayName("当前用户移动 profile 不下发后台管理入口")
    void profileShouldNotExposeMobileAdminEntriesForCurrentUser()
    {
        setLoginUser(8L, Set.of("common"), Set.of("system:user:list", "system:dict:list"));
        SysMobileController controller = new SysMobileController();

        AjaxResult result = controller.profile();
        MobileProfile profile = (MobileProfile) result.get(AjaxResult.DATA_TAG);

        assertThat(profile.isAdmin()).isFalse();
        assertThat(profile.getPermissions()).containsExactlyInAnyOrder("system:user:list", "system:dict:list");
        assertThat(profile.getAdminEntries()).isEmpty();
    }

    @Test
    @DisplayName("管理员移动 profile 也不下发后台管理入口")
    void profileShouldNotExposeMobileAdminEntriesForAdmin()
    {
        setLoginUser(1L, Set.of("admin"), Set.of("system:user:list"));
        SysMobileController controller = new SysMobileController();

        AjaxResult result = controller.profile();
        MobileProfile profile = (MobileProfile) result.get(AjaxResult.DATA_TAG);

        assertThat(profile.isAdmin()).isTrue();
        assertThat(profile.getAdminEntries()).isEmpty();
    }

    private static void setLoginUser(Long userId, Set<String> roles, Set<String> permissions)
    {
        SecurityContextHolder.setUserId(String.valueOf(userId));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setUsername("mobile-user");
        loginUser.setRoles(roles);
        loginUser.setPermissions(permissions);
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("mobile-user");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
