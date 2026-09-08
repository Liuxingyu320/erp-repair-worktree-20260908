package com.erp.system.service.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.service.TokenService;
import com.erp.system.api.domain.SysUser;
import com.erp.system.service.ISysUserService;

@DisplayName("管理员密码重置事务编排")
class UserCredentialResetServiceTest
{
    @Test
    @DisplayName("密码更新成功后必须撤销全部旧会话")
    void resetsPasswordBeforeRevokingSessions()
    {
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        UserCredentialResetService service = new UserCredentialResetService(userService, tokenService);
        SysUser user = user(99L);
        when(userService.resetPwd(user)).thenReturn(1);
        when(tokenService.deleteLoginUsersByUserId(99L)).thenReturn(3);

        assertThat(service.resetPasswordAndRevokeSessions(user)).isOne();

        InOrder order = inOrder(userService, tokenService);
        order.verify(userService).resetPwd(user);
        order.verify(tokenService).deleteLoginUsersByUserId(99L);
    }

    @Test
    @DisplayName("数据库未更新时不能误删现有会话")
    void doesNotRevokeWhenPasswordWasNotUpdated()
    {
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        UserCredentialResetService service = new UserCredentialResetService(userService, tokenService);
        SysUser user = user(99L);
        when(userService.resetPwd(user)).thenReturn(0);

        assertThat(service.resetPasswordAndRevokeSessions(user)).isZero();
        verify(tokenService, never()).deleteLoginUsersByUserId(99L);
    }

    @Test
    @DisplayName("Redis 故障必须显式失败并触发数据库事务回滚")
    void redisFailureIsVisibleAndMethodIsTransactional() throws Exception
    {
        ISysUserService userService = mock(ISysUserService.class);
        TokenService tokenService = mock(TokenService.class);
        UserCredentialResetService service = new UserCredentialResetService(userService, tokenService);
        SysUser user = user(99L);
        when(userService.resetPwd(user)).thenReturn(1);
        when(tokenService.deleteLoginUsersByUserId(99L))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> service.resetPasswordAndRevokeSessions(user))
                .isInstanceOf(ServiceException.class)
                .hasMessage("密码重置未完成：会话撤销失败，请稍后重试");

        Method method = UserCredentialResetService.class.getMethod(
                "resetPasswordAndRevokeSessions", SysUser.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private SysUser user(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setPassword("encoded-temporary-password");
        user.setMustChangePassword("1");
        return user;
    }
}
