package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.dto.SysSelfPasswordUpdateRequest;
import com.erp.system.domain.dto.SysUserManageUpdateRequest;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.domain.vo.SysUserCreateResultVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;
import java.util.Date;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

class SysPasswordPolicyControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void profilePasswordUpdateShouldRejectNewPasswordShorterThanPolicy()
    {
        SysProfileController controller = new SysProfileController();
        ISysUserService userService = mock(ISysUserService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "tokenService", mock(TokenService.class));
        when(userService.activateUserPassword(any(Long.class), any(String.class), anyString())).thenReturn(1);
        setLoginUser("oldpass1");

        AjaxResult result = controller.updatePwd(passwordRequest("oldpass1", "abc1234"));

        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("密码长度必须在8到20个字符之间");
    }

    @Test
    void profilePasswordUpdateShouldRejectNewPasswordThatViolatesConfiguredCharacterPolicy()
    {
        SysProfileController controller = new SysProfileController();
        ISysUserService userService = mock(ISysUserService.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "tokenService", mock(TokenService.class));
        ReflectionTestUtils.setField(controller, "configService", configService);
        when(configService.selectConfigByKey("sys.account.chrtype")).thenReturn("4");
        when(userService.activateUserPassword(any(Long.class), any(String.class), anyString())).thenReturn(1);
        setLoginUser("oldpass1");

        AjaxResult result = controller.updatePwd(passwordRequest("oldpass1", "abc12345"));

        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("密码必须同时包含字母、数字和特殊字符（~!@#$%^&*()-=_+）");
    }

    @Test
    void adminPasswordResetShouldIgnoreSubmittedPasswordAndReturnOneTimeCredential()
    {
        SysUserController controller = new SysUserController();
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        setLoginUser("oldpass1");
        SysUser request = new SysUser();
        request.setUserId(202L);
        request.setPassword("attacker-selected-password");
        SysUser stored = new SysUser();
        stored.setUserId(202L);
        stored.setUserName("target-user");
        SysTemporaryCredentialVo credential = new SysTemporaryCredentialVo(
                202L, "target-user", "one-time-password", new Date(System.currentTimeMillis() + 60_000L));
        when(userService.selectUserById(202L)).thenReturn(stored);
        when(userService.resetTemporaryCredential(eq(stored), anyString())).thenReturn(credential);
        HttpServletResponse response = mock(HttpServletResponse.class);

        AjaxResult result = controller.resetPwd(request, response);

        SysUserCreateResultVo data = (SysUserCreateResultVo) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getTemporaryCredential()).isSameAs(credential);
        assertThat(stored.getPassword()).isNull();
        // Invalidation is persisted in the same service transaction and dispatched after commit.
        verifyNoInteractions(tokenService);
        verify(response).setHeader("Cache-Control", "no-store, max-age=0");
    }

    @Test
    void adminUserAddShouldNotAcceptClientSelectedPassword()
    {
        SysUserController controller = new SysUserController();
        ISysUserService userService = mock(ISysUserService.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "deptService", mock(ISysDeptService.class));
        ReflectionTestUtils.setField(controller, "roleService", mock(ISysRoleService.class));
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
        when(userService.checkPhoneUnique(any(SysUser.class))).thenReturn(true);
        when(userService.checkEmailUnique(any(SysUser.class))).thenReturn(true);
        SysTemporaryCredentialVo credential = new SysTemporaryCredentialVo(
                303L, "new-user", "generated-password", new Date(System.currentTimeMillis() + 60_000L));
        when(userService.insertUserWithTemporaryCredential(any(SysUser.class), anyString())).thenReturn(credential);
        setLoginUser("oldpass1");
        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"userName\":\"new-user\",\"password\":\"client-selected-password\"}",
                SysUserManageUpdateRequest.class))
                .hasMessageContaining("password");

        SysUserManageUpdateRequest target = new SysUserManageUpdateRequest();
        target.setUserName("new-user");
        target.setDeptId(10L);
        target.setRoleIds(new Long[] { 2L });
        HttpServletResponse response = mock(HttpServletResponse.class);

        AjaxResult result = controller.add(target, response);

        SysUserCreateResultVo data = (SysUserCreateResultVo) result.get(AjaxResult.DATA_TAG);
        assertThat(data.getTemporaryCredential()).isSameAs(credential);
        verify(response).setHeader("Cache-Control", "no-store, max-age=0");
    }

    private void setLoginUser(String rawPassword)
    {
        SecurityContextHolder.setUserId("101");
        SecurityContextHolder.setUserName("admin");
        SysUser user = new SysUser();
        user.setUserId(101L);
        user.setUserName("admin");
        user.setPassword(SecurityUtils.encryptPassword(rawPassword));

        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(101L);
        loginUser.setUsername("admin");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }

    private static SysSelfPasswordUpdateRequest passwordRequest(String oldPassword, String newPassword)
    {
        SysSelfPasswordUpdateRequest request = new SysSelfPasswordUpdateRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
