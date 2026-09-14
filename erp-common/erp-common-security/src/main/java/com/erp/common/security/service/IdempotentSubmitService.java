package com.erp.common.security.service;

import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.redis.service.RedisService;

@Component
public class IdempotentSubmitService
{
    @Autowired
    private RedisService redisService;

    public String tryAcquire(String key, long timeoutSeconds)
    {
        String owner = UUID.randomUUID().toString();
        Boolean locked = redisService.setCacheObjectIfAbsent(key, owner, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked) ? owner : null;
    }

    public void release(String key, String owner)
    {
        redisService.deleteCacheObjectIfValueMatches(key, owner);
    }
}
