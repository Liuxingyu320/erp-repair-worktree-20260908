package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.dto.SysSelfPasswordUpdateRequest;
import com.erp.system.domain.dto.SysSelfProfileUpdateRequest;
import com.erp.system.domain.vo.SysProfileCompletionRequest;
import com.erp.system.domain.vo.SysSelfProfileVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.UserSessionInvalidationService;

@DisplayName("个人资料控制器权限边界")
class SysProfileControllerTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("个人资料接口都要求登录")
    void profileEndpointsShouldRequireLogin() throws Exception
    {
        assertRequiresLogin(SysProfileController.class.getMethod("profile"));
        assertRequiresLogin(SysProfileController.class.getMethod("profileCompletion"));
        assertRequiresLogin(SysProfileController.class.getMethod(
                "updateProfileCompletion", SysProfileCompletionRequest.class));
        assertRequiresLogin(SysProfileController.class.getMethod(
                "updateProfile", SysSelfProfileUpdateRequest.class));
        assertRequiresLogin(SysProfileController.class.getMethod(
                "updatePwd", SysSelfPasswordUpdateRequest.class));
        assertRequiresLogin(SysProfileController.class.getMethod("avatar", MultipartFile.class));
    }

    @Test
    @DisplayName("updatePwd 必须允许临时凭证访问")
    void updatePwdShouldAllowTemporaryCredential() throws Exception
    {
        Method method = SysProfileController.class.getMethod("updatePwd", SysSelfPasswordUpdateRequest.class);
        assertThat(method.getAnnotation(AllowsTemporaryCredential.class))
                .as("updatePwd must be annotated with @AllowsTemporaryCredential")
                .isNotNull();
    }

    @Test
    @DisplayName("改密成功后激活正式密码并清空临时过期时间")
    void updatePwdShouldActivateCredentialAndClearTemporaryExpiry()
    {
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        UserSessionInvalidationService sessionInvalidationService = mock(UserSessionInvalidationService.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("sys.account.chrtype")).thenReturn("0");
        when(userService.activateUserPassword(eq(42L), anyString(), eq("temp-user"))).thenReturn(1);

        String oldPlain = "OldTemp~Password01";
        String newPlain = "NewActive~Password02";
        String oldHash = SecurityUtils.encryptPassword(oldPlain);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(42L);
        loginUser.setUsername("temp-user");
        loginUser.setToken("token-42");
        loginUser.setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        loginUser.setTemporaryPasswordExpiresAt(new Date(System.currentTimeMillis() + 3_600_000L));
        SysUser sysUser = new SysUser();
        sysUser.setUserId(42L);
        sysUser.setUserName("temp-user");
        sysUser.setPassword(oldHash);
        sysUser.setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        sysUser.setMustChangePassword("1");
        sysUser.setTemporaryPasswordExpiresAt(loginUser.getTemporaryPasswordExpiresAt());
        loginUser.setSysUser(sysUser);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);

        SysProfileController controller = new SysProfileController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "userSessionInvalidationService", sessionInvalidationService);
        ReflectionTestUtils.setField(controller, "configService", configService);

        SysSelfPasswordUpdateRequest params = new SysSelfPasswordUpdateRequest();
        params.setOldPassword(oldPlain);
        params.setNewPassword(newPlain);

        AjaxResult result = controller.updatePwd(params);

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(200);
        verify(userService).activateUserPassword(eq(42L), anyString(), eq("temp-user"));
        assertThat(loginUser.getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_ACTIVE);
        assertThat(loginUser.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(loginUser.getSysUser().getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_ACTIVE);
        assertThat(loginUser.getSysUser().getMustChangePassword()).isEqualTo("0");
        assertThat(loginUser.getSysUser().getTemporaryPasswordExpiresAt()).isNull();
        assertThat(SecurityUtils.matchesPassword(newPlain, loginUser.getSysUser().getPassword())).isTrue();
        verify(tokenService).setLoginUser(loginUser);
        verify(sessionInvalidationService).record(eq(42L),
                eq(UserSessionInvalidationService.PASSWORD_CHANGED), eq("token-42"));
    }

    @Test
    @DisplayName("本人资料响应包含员工档案中的现住地")
    void selfProfileShouldIncludeCurrentAddress()
    {
        SysUser user = new SysUser(7L);
        SysUserProfile profile = new SysUserProfile();
        profile.setCurrentAddress("上海市浦东新区");
        profile.setIdNumber("310101199001011234");
        profile.setBankAccount("6222021234567890123");
        profile.setEmployeeNo("00007");
        user.setProfile(profile);

        SysSelfProfileVo result = SysSelfProfileVo.from(user);
        assertThat(result.getCurrentAddress()).isEqualTo("上海市浦东新区");
        assertThat(result.getProfile())
                .containsEntry("idNumberMasked", "3101**********1234")
                .containsEntry("bankAccountMasked", "6222***********0123")
                .containsEntry("employeeNo", "00007");
    }

    @Test
    @DisplayName("本人资料更新只生成允许维护的员工档案字段")
    void selfProfileRequestShouldBuildWhitelistedProfileValues()
    {
        SysSelfProfileUpdateRequest request = new SysSelfProfileUpdateRequest();
        request.setCurrentAddress(" 上海市浦东新区 ");
        request.setEmergencyContact("李四");
        request.setFirstEducation("本科");
        request.setBankName("中国银行");
        request.setBankAccount("");

        assertThat(request.profileValues())
                .containsEntry("currentAddress", "上海市浦东新区")
                .containsEntry("emergencyContact", "李四")
                .containsEntry("firstEducation", "本科")
                .containsEntry("bankName", "中国银行")
                .doesNotContainKey("bankAccount")
                .doesNotContainKey("employeeNo")
                .doesNotContainKey("contractType");
    }

    private static void assertRequiresLogin(Method method)
    {
        assertThat(method.getAnnotation(RequiresLogin.class))
                .as(method.getName() + " should require login")
                .isNotNull();
    }
}
