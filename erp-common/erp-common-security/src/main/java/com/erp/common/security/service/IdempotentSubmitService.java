package com.erp.common.security.service;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.redis.service.RedisService;

@Component
public class IdempotentSubmitService
{
    private static final String CACHE_VALUE = "1";

    @Autowired
    private RedisService redisService;

    public boolean tryAcquire(String key, long timeoutSeconds)
    {
        Boolean locked = redisService.setCacheObjectIfAbsent(key, CACHE_VALUE, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    public void release(String key)
    {
        redisService.deleteObject(key);
    }
}
