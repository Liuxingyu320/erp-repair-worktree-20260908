package com.erp.oa.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.redis.service.RedisService;

/** Short-lived opaque previews work across OA instances; they confer no business permission. */
@Component
public class OaSignPlanPublishPreviewStore
{
    private static final String PREFIX = "oa:sign-plan:publish-preview:";
    static final long TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private final RedisService redis;
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    public OaSignPlanPublishPreviewStore(RedisService redis, ObjectMapper json)
    {
        this.redis = redis;
        this.json = json;
    }

    public record Ticket(Long actorId, Long planId, String versionHash,
            List<Long> activeVersionIds, Long targetVersionId, boolean restore, long expiresAt) {}
    public record Issued(String token, long expiresAt) {}

    public Issued issue(Long actorId, Long planId, String versionHash,
            List<Long> activeVersionIds, Long targetVersionId, boolean restore)
    {
        if (actorId == null) throw new ServiceException("无法确认方案发布操作者");
        String token = UUID.randomUUID().toString();
        long expiresAt = clock.millis() + TTL_MILLIS;
        Ticket ticket = new Ticket(actorId, planId, versionHash, List.copyOf(activeVersionIds),
                targetVersionId, restore, expiresAt);
        try
        {
            redis.setCacheObject(PREFIX + token, json.writeValueAsString(ticket), 5L, TimeUnit.MINUTES);
            return new Issued(token, expiresAt);
        }
        catch (Exception failure)
        {
            throw new ServiceException("发布预览暂时不可用，请稍后重新预览");
        }
    }

    public Ticket require(String token, Long actorId)
    {
        if (token == null || !token.matches("[a-f0-9-]{36}"))
            throw new ServiceException("请先预览并确认本次方案发布");
        Ticket ticket;
        try
        {
            String value = redis.getCacheObject(PREFIX + token);
            ticket = value == null ? null : json.readValue(value, Ticket.class);
        }
        catch (Exception failure)
        {
            throw new ServiceException("发布预览暂时不可用，请保留当前输入后重试");
        }
        if (actorId == null || ticket == null || ticket.expiresAt() <= clock.millis()
                || !Objects.equals(ticket.actorId(), actorId))
            throw new ServiceException("发布预览已失效，请重新预览后确认");
        return ticket;
    }
}
