package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysLoginLockStateVo;
import com.erp.system.domain.vo.SysLoginUnlockResultVo;
import com.erp.system.service.ISysUserService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class SysLogininforControllerLockStateTest
{
    @Test
    void shouldReturnRealTimeLockStateAfterTargetAndScopeValidation()
    {
        RedisService redisService = mock(RedisService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysLogininforController controller = controller(redisService, userService);
        SysUser user = user(42L, "alice");
        when(userService.selectUserByUserName("alice")).thenReturn(user);
        when(redisService.getCacheObject(CacheConstants.PWD_ERR_CNT_KEY + "alice")).thenReturn(5);
        when(redisService.getExpire(CacheConstants.PWD_ERR_CNT_KEY + "alice")).thenReturn(321L);

        AjaxResult response = controller.lockState(" alice ");

        SysLoginLockStateVo state = (SysLoginLockStateVo) response.get(AjaxResult.DATA_TAG);
        assertThat(state.isLocked()).isTrue();
        assertThat(state.isCanUnlock()).isTrue();
        assertThat(state.getRetryCount()).isEqualTo(5);
        assertThat(state.getRemainingSeconds()).isEqualTo(321L);
        verify(userService).checkUserAllowed(user);
        verify(userService).checkUserDataScope(42L);
    }

    @Test
    void shouldUnlockOnlyWhenAccountIsActuallyLockedAndReturnBeforeAfterState()
    {
        RedisService redisService = mock(RedisService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysLogininforController controller = controller(redisService, userService);
        when(userService.selectUserByUserName("alice")).thenReturn(user(42L, "alice"));
        when(redisService.getCacheObject(anyString())).thenReturn(5).thenReturn(null);
        when(redisService.getExpire(anyString())).thenReturn(120L, -2L);
        when(redisService.deleteObject(CacheConstants.PWD_ERR_CNT_KEY + "alice")).thenReturn(true);

        AjaxResult response = controller.unlock("alice");

        SysLoginUnlockResultVo result = (SysLoginUnlockResultVo) response.get(AjaxResult.DATA_TAG);
        assertThat(result.isUnlocked()).isTrue();
        assertThat(result.getBefore().isLocked()).isTrue();
        assertThat(result.getAfter().isLocked()).isFalse();
        verify(redisService).deleteObject(CacheConstants.PWD_ERR_CNT_KEY + "alice");
    }

    @Test
    void shouldNotClearRetryCounterWhenAccountIsNotLocked()
    {
        RedisService redisService = mock(RedisService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysLogininforController controller = controller(redisService, userService);
        when(userService.selectUserByUserName("alice")).thenReturn(user(42L, "alice"));
        when(redisService.getCacheObject(anyString())).thenReturn(2);
        when(redisService.getExpire(anyString())).thenReturn(60L);

        AjaxResult response = controller.unlock("alice");

        SysLoginUnlockResultVo result = (SysLoginUnlockResultVo) response.get(AjaxResult.DATA_TAG);
        assertThat(result.isUnlocked()).isFalse();
        assertThat(result.getBefore().getRetryCount()).isEqualTo(2);
        verify(redisService, never()).deleteObject(anyString());
    }

    @Test
    void shouldFailExplicitlyWhenRedisLockStateIsUnavailable()
    {
        RedisService redisService = mock(RedisService.class);
        ISysUserService userService = mock(ISysUserService.class);
        SysLogininforController controller = controller(redisService, userService);
        when(userService.selectUserByUserName("alice")).thenReturn(user(42L, "alice"));
        when(redisService.getCacheObject(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> controller.lockState("alice"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不可用");
    }

    @Test
    void changingLockStateMustUsePostWhileLegacyGetIsClearlySeparated() throws Exception
    {
        Method unlock = SysLogininforController.class.getMethod("unlock", String.class);
        Method legacy = SysLogininforController.class.getMethod("unlockLegacy", String.class);

        assertThat(unlock.getAnnotation(PostMapping.class).value()).containsExactly("/{userName}/unlock");
        assertThat(unlock.getAnnotation(IdempotentSubmit.class)).isNotNull();
        assertThat(unlock.getAnnotation(GetMapping.class)).isNull();
        assertThat(legacy.getAnnotation(GetMapping.class).value()).containsExactly("/unlock/{userName}");
        assertThat(legacy.getAnnotation(Deprecated.class)).isNotNull();
    }

    private SysLogininforController controller(RedisService redisService, ISysUserService userService)
    {
        SysLogininforController controller = new SysLogininforController();
        ReflectionTestUtils.setField(controller, "redisService", redisService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        return controller;
    }

    private SysUser user(Long id, String userName)
    {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUserName(userName);
        return user;
    }
}
