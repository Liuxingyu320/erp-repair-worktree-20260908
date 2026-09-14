package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.oa.service.impl.OaSignPlanPublishPreviewStore.Ticket;
import com.fasterxml.jackson.databind.ObjectMapper;

class OaSignPlanPublishPreviewStoreTest
{
    private static final long NOW = 1800000000000L;

    @Test
    void roundTripsOpaqueActorBoundTicketWithoutExposingContentHashInToken()
    {
        Fixture f = fixture();
        var issued = f.store.issue(7L, 100L, "private-content-hash", List.of(502L), 501L, true);
        assertThat(issued.token()).matches("[a-f0-9-]{36}");
        assertThat(issued.expiresAt()).isEqualTo(NOW + TimeUnit.MINUTES.toMillis(5));
        Ticket ticket = f.store.require(issued.token(), 7L);
        assertThat(ticket).isEqualTo(new Ticket(7L, 100L, "private-content-hash", List.of(502L), 501L, true, issued.expiresAt()));
        assertThat(f.cache).hasSize(1);
        assertThat(f.cache.keySet()).allMatch(key -> key.startsWith("oa:sign-plan:publish-preview:"));
    }

    @Test
    void anotherActorCannotReuseThePreview()
    {
        Fixture f = fixture();
        var issued = f.store.issue(7L, 100L, "hash", List.of(), null, false);
        assertThatThrownBy(() -> f.store.require(issued.token(), 8L)).hasMessageContaining("失效");
        assertThat(f.store.require(issued.token(), 7L).planId()).isEqualTo(100L);
    }

    @Test
    void deadlineIsCheckedEvenWhenRedisHasNotExpiredItsEntry()
    {
        Fixture f = fixture();
        var issued = f.store.issue(7L, 100L, "hash", List.of(), null, false);
        ReflectionTestUtils.setField(f.store, "clock", Clock.fixed(Instant.ofEpochMilli(issued.expiresAt()), ZoneOffset.UTC));
        assertThatThrownBy(() -> f.store.require(issued.token(), 7L)).hasMessageContaining("失效");
    }

    @Test
    void malformedAndMissingTokensNeverBecomeNewPublicationCommands()
    {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.store.require("not-a-preview", 7L)).hasMessageContaining("先预览");
        assertThatThrownBy(() -> f.store.require("00000000-0000-0000-0000-000000000001", 7L)).hasMessageContaining("失效");
        assertThatThrownBy(() -> f.store.issue(null, 100L, "hash", List.of(), null, false)).hasMessageContaining("操作者");
    }

    @Test
    void unavailableCacheDoesNotIssueUnverifiablePreview()
    {
        Fixture f = fixture();
        doThrow(new IllegalStateException("unavailable")).when(f.redis).setCacheObject(anyString(), any(), any(Long.class), any(TimeUnit.class));
        assertThatThrownBy(() -> f.store.issue(7L, 100L, "hash", List.of(), null, false)).hasMessageContaining("暂时不可用");
    }

    @Test
    void corruptedCacheIsExplicitlyUnavailableRatherThanNotFound()
    {
        Fixture f = fixture();
        var issued = f.store.issue(7L, 100L, "hash", List.of(), null, false);
        f.cache.replaceAll((key, value) -> "invalid json");
        assertThatThrownBy(() -> f.store.require(issued.token(), 7L)).hasMessageContaining("暂时不可用");
    }

    private Fixture fixture()
    {
        RedisService redis = mock(RedisService.class);
        Map<String, String> cache = new HashMap<>();
        doAnswer(call -> {
            assertThat(call.getArgument(2, Long.class)).isEqualTo(5L);
            assertThat(call.getArgument(3, TimeUnit.class)).isEqualTo(TimeUnit.MINUTES);
            cache.put(call.getArgument(0), call.getArgument(1)); return null;
        }).when(redis).setCacheObject(anyString(), any(), any(Long.class), any(TimeUnit.class));
        when(redis.getCacheObject(anyString())).thenAnswer(call -> cache.get(call.getArgument(0)));
        OaSignPlanPublishPreviewStore store = new OaSignPlanPublishPreviewStore(redis, new ObjectMapper());
        ReflectionTestUtils.setField(store, "clock", Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
        return new Fixture(store, redis, cache);
    }
    private record Fixture(OaSignPlanPublishPreviewStore store, RedisService redis, Map<String, String> cache) {}
}
