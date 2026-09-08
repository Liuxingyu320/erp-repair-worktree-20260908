package com.erp.common.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.redis.service.RedisService;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.model.LoginUser;

class TokenServiceTest
{
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void deletesOnlySessionsOwnedByResetUser()
    {
        RedisService redisService = mock(RedisService.class);
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "redisService", redisService);
        String first = CacheConstants.LOGIN_TOKEN_KEY + "first";
        String second = CacheConstants.LOGIN_TOKEN_KEY + "second";
        String other = CacheConstants.LOGIN_TOKEN_KEY + "other";
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 10000))
                .thenReturn(new KeyScanResult(List.of(first, second, other), false, 3, 10000));
        when(redisService.getMultiCacheObject(List.of(first, second, other)))
                .thenReturn(List.of(loginUser(22L), loginUser(22L), loginUser(33L)));
        when(redisService.deleteObject(any(Collection.class))).thenReturn(true);

        int deleted = tokenService.deleteLoginUsersByUserId(22L);

        assertThat(deleted).isEqualTo(2);
        ArgumentCaptor<Collection> keys = ArgumentCaptor.forClass(Collection.class);
        verify(redisService).deleteObject(keys.capture());
        verify(redisService, never()).keys(any(String.class));
        assertThat(keys.getValue()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void refusesPartialSessionRevocationWhenScanIsTruncated()
    {
        RedisService redisService = mock(RedisService.class);
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "redisService", redisService);
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 10000))
                .thenReturn(new KeyScanResult(List.of(CacheConstants.LOGIN_TOKEN_KEY + "first"), true, 10001,
                        10000));

        assertThatThrownBy(() -> tokenService.deleteLoginUsersByUserId(22L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝执行不完整的会话撤销");

        verify(redisService, never()).getMultiCacheObject(any(Collection.class));
        verify(redisService, never()).deleteObject(any(Collection.class));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void refusesRevocationWhenBatchReadIsIncomplete()
    {
        RedisService redisService = mock(RedisService.class);
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "redisService", redisService);
        String first = CacheConstants.LOGIN_TOKEN_KEY + "first";
        String second = CacheConstants.LOGIN_TOKEN_KEY + "second";
        when(redisService.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 10000))
                .thenReturn(new KeyScanResult(List.of(first, second), false, 2, 10000));
        when(redisService.getMultiCacheObject(List.of(first, second)))
                .thenReturn(List.of(loginUser(22L)));

        assertThatThrownBy(() -> tokenService.deleteLoginUsersByUserId(22L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批量读取登录会话结果不完整");

        verify(redisService, never()).deleteObject(any(Collection.class));
    }

    private LoginUser loginUser(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setSysUser(user);
        return loginUser;
    }
}
