package com.erp.common.security.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.List;
import java.util.Collection;
import com.erp.common.redis.service.RedisService.CacheSnapshot;
import com.erp.common.redis.service.RedisService.KeyScanResult;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.redis.service.RedisService;
import com.erp.system.api.model.LoginUser;

class TokenServiceUserIndexTest
{
    @Test
    void refreshUsesOriginalSnapshotWithoutRebuildingUserIndex()
    {
        RedisService redis = mock(RedisService.class);
        TokenService service = service(redis);
        LoginUser original = new LoginUser();
        original.setUserid(42L);
        original.setToken("token-a");
        byte[] bytes = new byte[] {1, 2, 3};
        when(redis.getCacheSnapshot(CacheConstants.LOGIN_TOKEN_KEY + "token-a"))
                .thenReturn(new CacheSnapshot(bytes, original));
        when(redis.deserializeCacheValue(any(byte[].class))).thenReturn(original);
        when(redis.compareAndSetCacheObject(any(), any(), any(), eq(CacheConstants.EXPIRATION),
                eq(TimeUnit.MINUTES))).thenReturn(new byte[] {4, 5, 6});

        service.refreshToken(service.getLoginUserByUserKey("token-a"));

        verify(redis).compareAndSetCacheObject(eq(CacheConstants.LOGIN_TOKEN_KEY + "token-a"),
                any(byte[].class), eq(original), eq(CacheConstants.EXPIRATION), eq(TimeUnit.MINUTES));
        verify(redis, never()).setCacheObject(any(), any(), any(), any());
        verify(redis, never()).addCacheSetValue(any(), any());
    }

    @Test
    void invalidationUsesUserIndexAndCanRetainCurrentToken()
    {
        RedisService redis = mock(RedisService.class);
        when(redis.<String>getCacheSet(CacheConstants.USER_LOGIN_TOKEN_KEY + "42"))
                .thenReturn(Set.of("token-a", "token-b"));
        LoginUser first = new LoginUser(); first.setUserid(42L);
        LoginUser second = new LoginUser(); second.setUserid(42L);
        String keyA = CacheConstants.LOGIN_TOKEN_KEY + "token-a";
        String keyB = CacheConstants.LOGIN_TOKEN_KEY + "token-b";
        when(redis.scanKeys(CacheConstants.LOGIN_TOKEN_KEY + "*", 500, 10000))
                .thenReturn(new KeyScanResult(List.of(keyA, keyB), false, 2, 10000));
        when(redis.getMultiCacheObject(List.of(keyA, keyB))).thenReturn(List.of(first, second));
        when(redis.deleteObject(any(Collection.class))).thenReturn(true);
        TokenService service = service(redis);

        service.invalidateUserSessions(42L, "token-a");

        verify(redis, never()).deleteObject(CacheConstants.LOGIN_TOKEN_KEY + "token-a");
        verify(redis).deleteObject(List.of(CacheConstants.LOGIN_TOKEN_KEY + "token-b"));
        verify(redis).removeCacheSetValue(CacheConstants.USER_LOGIN_TOKEN_KEY + "42", "token-b");
        verify(redis).expire(CacheConstants.USER_LOGIN_TOKEN_KEY + "42",
                CacheConstants.EXPIRATION, TimeUnit.MINUTES);
        verify(redis, never()).keys(any());
    }

    private TokenService service(RedisService redis)
    {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "redisService", redis);
        return service;
    }
}
