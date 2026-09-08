package com.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.enums.UserStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.exception.CredentialRestrictionException;
import com.erp.system.api.RemoteConfigService;
import com.erp.system.api.RemoteUserService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;

class SysLoginServiceTest
{
    @Test
    void loginShouldAllowLegacyShortPasswordToReachCredentialValidation()
    {
        String username = "legacy-user";
        String legacyPassword = "abc1234";
        SysLoginService service = new SysLoginService();
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        SysPasswordService passwordService = mock(SysPasswordService.class);
        SysRecordLogService recordLogService = mock(SysRecordLogService.class);
        RedisService redisService = mock(RedisService.class);
        LoginUser loginUser = loginUser(username);

        doReturn(null).when(redisService).getCacheObject(anyString());
        when(remoteUserService.getUserInfo(eq(username), eq(SecurityConstants.INNER))).thenReturn(R.ok(loginUser));
        when(remoteUserService.recordUserLogin(any(SysUser.class), eq(SecurityConstants.INNER))).thenReturn(R.ok(Boolean.TRUE));
        doNothing().when(passwordService).validate(any(SysUser.class), eq(legacyPassword));
        ReflectionTestUtils.setField(service, "remoteUserService", remoteUserService);
        ReflectionTestUtils.setField(service, "passwordService", passwordService);
        ReflectionTestUtils.setField(service, "recordLogService", recordLogService);
        ReflectionTestUtils.setField(service, "redisService", redisService);

        service.login(username, legacyPassword);

        verify(remoteUserService).getUserInfo(username, SecurityConstants.INNER);
        verify(passwordService).validate(loginUser.getSysUser(), legacyPassword);
    }

    @Test
    void registerShouldRejectPasswordThatViolatesConfiguredCharacterPolicyBeforeEncrypting()
    {
        SysLoginService service = new SysLoginService();
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        RemoteConfigService remoteConfigService = mock(RemoteConfigService.class);
        when(remoteConfigService.getConfigKey(eq("sys.account.chrtype"), eq(SecurityConstants.INNER))).thenReturn(R.ok("4"));
        ReflectionTestUtils.setField(service, "remoteUserService", remoteUserService);
        ReflectionTestUtils.setField(service, "remoteConfigService", remoteConfigService);

        assertThatThrownBy(() -> service.register("new-user", "abc12345"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("密码必须同时包含字母、数字和特殊字符（~!@#$%^&*()-=_+）");

        verify(remoteUserService, never()).registerUserInfo(any(SysUser.class), eq(SecurityConstants.INNER));
    }

    @Test
    void loginShouldRejectExpiredTemporaryCredentialAfterPasswordValidation()
    {
        String username = "temporary-user";
        SysLoginService service = new SysLoginService();
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        SysPasswordService passwordService = mock(SysPasswordService.class);
        SysRecordLogService recordLogService = mock(SysRecordLogService.class);
        RedisService redisService = mock(RedisService.class);
        LoginUser loginUser = loginUser(username);
        loginUser.getSysUser().setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        loginUser.getSysUser().setTemporaryPasswordExpiresAt(new Date(System.currentTimeMillis() - 1_000L));

        when(remoteUserService.getUserInfo(eq(username), eq(SecurityConstants.INNER))).thenReturn(R.ok(loginUser));
        doNothing().when(passwordService).validate(any(SysUser.class), eq("valid-password"));
        ReflectionTestUtils.setField(service, "remoteUserService", remoteUserService);
        ReflectionTestUtils.setField(service, "passwordService", passwordService);
        ReflectionTestUtils.setField(service, "recordLogService", recordLogService);
        ReflectionTestUtils.setField(service, "redisService", redisService);

        assertThatThrownBy(() -> service.login(username, "valid-password"))
                .isInstanceOf(CredentialRestrictionException.class)
                .extracting("businessCode")
                .isEqualTo(CredentialRestrictionException.TEMPORARY_EXPIRED);

        verify(passwordService).validate(loginUser.getSysUser(), "valid-password");
        verify(remoteUserService, never()).recordUserLogin(any(SysUser.class), eq(SecurityConstants.INNER));
    }

    @Test
    void loginShouldCarryChangeRequiredStateIntoTokenModel()
    {
        String username = "legacy-user";
        SysLoginService service = new SysLoginService();
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        SysPasswordService passwordService = mock(SysPasswordService.class);
        SysRecordLogService recordLogService = mock(SysRecordLogService.class);
        RedisService redisService = mock(RedisService.class);
        LoginUser loginUser = loginUser(username);
        loginUser.getSysUser().setCredentialState(SysUser.CREDENTIAL_STATE_CHANGE_REQUIRED);

        when(remoteUserService.getUserInfo(eq(username), eq(SecurityConstants.INNER))).thenReturn(R.ok(loginUser));
        when(remoteUserService.recordUserLogin(any(SysUser.class), eq(SecurityConstants.INNER))).thenReturn(R.ok(Boolean.TRUE));
        doNothing().when(passwordService).validate(any(SysUser.class), eq("valid-password"));
        ReflectionTestUtils.setField(service, "remoteUserService", remoteUserService);
        ReflectionTestUtils.setField(service, "passwordService", passwordService);
        ReflectionTestUtils.setField(service, "recordLogService", recordLogService);
        ReflectionTestUtils.setField(service, "redisService", redisService);

        LoginUser result = service.login(username, "valid-password");

        assertThat(result.getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_CHANGE_REQUIRED);
    }

    @Test
    void loginShouldCarryUnexpiredTemporaryCredentialStateAndExpiresAtIntoTokenModel()
    {
        String username = "temporary-user";
        Date expiresAt = new Date(System.currentTimeMillis() + 3_600_000L);
        SysLoginService service = new SysLoginService();
        RemoteUserService remoteUserService = mock(RemoteUserService.class);
        SysPasswordService passwordService = mock(SysPasswordService.class);
        SysRecordLogService recordLogService = mock(SysRecordLogService.class);
        RedisService redisService = mock(RedisService.class);
        LoginUser loginUser = loginUser(username);
        loginUser.getSysUser().setCredentialState(SysUser.CREDENTIAL_STATE_TEMPORARY);
        loginUser.getSysUser().setTemporaryPasswordExpiresAt(expiresAt);

        when(remoteUserService.getUserInfo(eq(username), eq(SecurityConstants.INNER))).thenReturn(R.ok(loginUser));
        when(remoteUserService.recordUserLogin(any(SysUser.class), eq(SecurityConstants.INNER))).thenReturn(R.ok(Boolean.TRUE));
        doNothing().when(passwordService).validate(any(SysUser.class), eq("valid-password"));
        ReflectionTestUtils.setField(service, "remoteUserService", remoteUserService);
        ReflectionTestUtils.setField(service, "passwordService", passwordService);
        ReflectionTestUtils.setField(service, "recordLogService", recordLogService);
        ReflectionTestUtils.setField(service, "redisService", redisService);

        LoginUser result = service.login(username, "valid-password");

        assertThat(result.getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_TEMPORARY);
        assertThat(result.getTemporaryPasswordExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSysUser().getCredentialState()).isEqualTo(SysUser.CREDENTIAL_STATE_TEMPORARY);
        assertThat(result.getSysUser().getTemporaryPasswordExpiresAt()).isEqualTo(expiresAt);
        verify(passwordService).validate(loginUser.getSysUser(), "valid-password");
        verify(remoteUserService).recordUserLogin(any(SysUser.class), eq(SecurityConstants.INNER));
    }

    @Test
    void passwordPolicyShouldFallBackToDefaultWhenConfigServiceReturnsNoResult()
    {
        SysLoginService service = new SysLoginService();
        RemoteConfigService remoteConfigService = mock(RemoteConfigService.class);
        when(remoteConfigService.getConfigKey(eq("sys.account.chrtype"), eq(SecurityConstants.INNER))).thenReturn(null);
        ReflectionTestUtils.setField(service, "remoteConfigService", remoteConfigService);

        assertThat(service.getPasswordPolicy()).isEqualTo("0");
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserId(101L);
        user.setUserName(username);
        user.setStatus(UserStatus.OK.getCode());
        user.setDelFlag(UserStatus.OK.getCode());

        LoginUser loginUser = new LoginUser();
        loginUser.setSysUser(user);
        return loginUser;
    }
}
