package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.log.annotation.Log;
import com.erp.common.security.annotation.AllowPasswordChangeRequired;
import com.erp.common.security.annotation.AllowsTemporaryCredential;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import com.erp.system.domain.dto.SysSelfPasswordUpdateRequest;
import com.erp.system.domain.dto.SysSelfProfileUpdateRequest;
import com.erp.system.domain.dto.SysUserManageUpdateRequest;
import com.erp.system.domain.vo.SysTemporaryCredentialVo;
import com.erp.system.domain.vo.SysUserCreateResultVo;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysDeptService;
import com.erp.system.service.ISysRoleService;
import com.erp.system.service.ISysUserService;
import java.util.Date;

class SysUserCredentialControllerTest
{
    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void addGeneratesOneTimePasswordMarksUserAndPreventsCaching()
    {
        ISysUserService userService = mock(ISysUserService.class);
        SysUserController controller = controller(userService, mock(TokenService.class));
        when(userService.checkUserNameUnique(any())).thenReturn(true);
        when(userService.checkPhoneUnique(any())).thenReturn(true);
        when(userService.checkEmailUnique(any())).thenReturn(true);
        SysTemporaryCredentialVo credential = new SysTemporaryCredentialVo(
                88L, "new-user", "generated-password", new Date(System.currentTimeMillis() + 60_000L));
        when(userService.insertUserWithTemporaryCredential(any(SysUser.class), any())).thenReturn(credential);
        bindAdministrator();
        SysUserManageUpdateRequest user = new SysUserManageUpdateRequest();
        user.setUserName("new-user");
        user.setNickName("新用户");
        user.setDeptId(10L);
        user.setRoleIds(new Long[] { 2L });
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult ajax = controller.add(user, response);

        SysUserCreateResultVo result = (SysUserCreateResultVo) ajax.get(AjaxResult.DATA_TAG);
        assertThat(result.getTemporaryCredential()).isSameAs(credential);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void resetReturnsNewCredentialAndPreventsCaching()
    {
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        SysUserController controller = controller(userService, tokenService);
        SysUser stored = new SysUser();
        stored.setUserId(99L);
        stored.setUserName("target-user");
        SysTemporaryCredentialVo credential = new SysTemporaryCredentialVo(
                99L, "target-user", "generated-password", new Date(System.currentTimeMillis() + 60_000L));
        when(userService.selectUserById(99L)).thenReturn(stored);
        when(userService.resetTemporaryCredential(stored, "4")).thenReturn(credential);
        bindAdministrator();
        SysUser user = new SysUser();
        user.setUserId(99L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AjaxResult ajax = controller.resetPwd(user, response);

        SysUserCreateResultVo result = (SysUserCreateResultVo) ajax.get(AjaxResult.DATA_TAG);
        assertThat(result.getTemporaryCredential()).isSameAs(credential);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void passwordEndpointsDoNotPersistRequestOrResponseBodies() throws Exception
    {
        Method add = SysUserController.class.getMethod("add", SysUserManageUpdateRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        Method reset = SysUserController.class.getMethod("resetPwd", SysUser.class,
                jakarta.servlet.http.HttpServletResponse.class);
        Method selfChange = SysProfileController.class.getMethod("updatePwd", SysSelfPasswordUpdateRequest.class);
        Method profileEdit = SysProfileController.class.getMethod("updateProfile", SysSelfProfileUpdateRequest.class);

        assertLogBodiesDisabled(add);
        assertLogBodiesDisabled(reset);
        assertLogBodiesDisabled(selfChange);
        assertThat(selfChange.getAnnotation(AllowPasswordChangeRequired.class)).isNotNull();
        assertThat(profileEdit.getAnnotation(AllowPasswordChangeRequired.class)).isNull();
    }

    @Test
    void currentUserInfoAllowsCredentialDiscoveryButBusinessMenusRemainRestricted() throws Exception
    {
        Method getInfo = SysUserController.class.getMethod("getInfo");
        Method getRouters = SysMenuController.class.getMethod("getRouters");

        assertThat(getInfo.getAnnotation(AllowPasswordChangeRequired.class)).isNotNull();
        assertThat(getInfo.getAnnotation(AllowsTemporaryCredential.class)).isNotNull();
        assertThat(getRouters.getAnnotation(AllowPasswordChangeRequired.class)).isNull();
        assertThat(getRouters.getAnnotation(AllowsTemporaryCredential.class)).isNull();
    }

    private void assertLogBodiesDisabled(Method method)
    {
        Log log = method.getAnnotation(Log.class);
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private SysUserController controller(ISysUserService userService, TokenService tokenService)
    {
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("sys.account.chrtype")).thenReturn("4");
        SysUserController controller = new SysUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "deptService", mock(ISysDeptService.class));
        ReflectionTestUtils.setField(controller, "roleService", mock(ISysRoleService.class));
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "configService", configService);
        return controller;
    }

    private void bindAdministrator()
    {
        SecurityContextHolder.setUserId("1");
        SecurityContextHolder.setUserName("admin");
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName("admin");
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(1L);
        loginUser.setUsername("admin");
        loginUser.setSysUser(user);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
    }
}
