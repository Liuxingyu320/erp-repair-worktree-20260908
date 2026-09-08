package com.erp.common.security.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.redis.service.RedisService;
import com.erp.system.api.model.LoginUser;

class TokenServiceUserIndexTest
{
    @Test
    void refreshMaintainsPerUserTokenIndexWithFullTokenTtl()
    {
        RedisService redis = mock(RedisService.class);
        TokenService service = service(redis);
        LoginUser user = new LoginUser();
        user.setUserid(42L);
        user.setToken("token-a");

        service.refreshToken(user);

        verify(redis).setCacheObject(eq(CacheConstants.LOGIN_TOKEN_KEY + "token-a"), eq(user),
                eq(CacheConstants.EXPIRATION), eq(TimeUnit.MINUTES));
        verify(redis).addCacheSetValue(CacheConstants.USER_LOGIN_TOKEN_KEY + "42", "token-a");
        verify(redis).expire(CacheConstants.USER_LOGIN_TOKEN_KEY + "42",
                CacheConstants.EXPIRATION, TimeUnit.MINUTES);
    }

    @Test
    void invalidationUsesUserIndexAndCanRetainCurrentToken()
    {
        RedisService redis = mock(RedisService.class);
        when(redis.<String>getCacheSet(CacheConstants.USER_LOGIN_TOKEN_KEY + "42"))
                .thenReturn(Set.of("token-a", "token-b"));
        TokenService service = service(redis);

        service.invalidateUserSessions(42L, "token-a");

        verify(redis, never()).deleteObject(CacheConstants.LOGIN_TOKEN_KEY + "token-a");
        verify(redis).deleteObject(CacheConstants.LOGIN_TOKEN_KEY + "token-b");
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
