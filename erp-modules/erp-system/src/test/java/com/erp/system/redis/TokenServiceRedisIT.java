package com.erp.system.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.service.TokenService;
import com.erp.system.testsupport.NativeRedisIntegrationTestSupport;

@DisplayName("TokenService 原生 Redis 会话撤销")
class TokenServiceRedisIT
{
    private static final long TARGET_USER_ID = 4242L;

    private static NativeRedisIntegrationTestSupport REDIS;

    private RedisService redisService;

    private TokenService tokenService;

    @BeforeAll
    static void openRedis()
    {
        REDIS = NativeRedisIntegrationTestSupport.create("token_service");
    }

    @BeforeEach
    void cleanBeforeTest()
    {
        redisService = REDIS.getRedisService();
        tokenService = REDIS.getTokenService();
        REDIS.cleanupGeneratedKeys();
    }

    @AfterAll
    static void closeRedis()
    {
        if (REDIS != null)
        {
            REDIS.close();
        }
    }

    @Test
    @DisplayName("100、1000、10000 会话只撤销目标用户且不刷新其他键 TTL")
    void revokesOnlyTargetSessionsAcrossRequiredDataSets()
    {
        benchmarkDataSet(100, 1);
        benchmarkDataSet(1_000, 5);
        benchmarkDataSet(10_000, 20);
    }

    @Test
    @DisplayName("撤销期间新增目标会话不影响其他用户并可由受控重试完全收敛")
    void concurrentSessionCreationConvergesWithoutTouchingOtherUsers() throws Exception
    {
        int initialSize = 1_000;
        int initialTargetCount = 20;
        seedDataSet(initialSize, initialTargetCount);
        List<String> initialTargets = targetSessionIds(initialTargetCount);
        List<String> concurrentTargets = new ArrayList<>();
        CountDownLatch writerStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<Integer> writer = executor.submit(() -> {
                for (int i = 0; i < 30; i++)
                {
                    String sessionId = REDIS.sessionId("concurrent_target", i);
                    REDIS.putLoginUser(sessionId, TARGET_USER_ID, "target-user", 1_800);
                    concurrentTargets.add(sessionId);
                    if (i == 0)
                    {
                        writerStarted.countDown();
                    }
                }
                return concurrentTargets.size();
            });
            Future<Integer> revocation = executor.submit(() -> {
                assertThat(writerStarted.await(10, TimeUnit.SECONDS)).isTrue();
                return tokenService.deleteLoginUsersByUserId(TARGET_USER_ID);
            });

            assertThat(writer.get(30, TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(revocation.get(30, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(initialTargetCount);
        }
        finally
        {
            executor.shutdownNow();
        }

        for (String sessionId : initialTargets)
        {
            assertThat(redisService.hasKey(REDIS.sessionKey(sessionId))).isFalse();
        }

        int retryDeleted = tokenService.deleteLoginUsersByUserId(TARGET_USER_ID);
        assertThat(retryDeleted).isBetween(0, concurrentTargets.size());
        assertThat(countTargetSessions()).isZero();
        assertThat(redisService.scanKeys("login_tokens:" + REDIS.getPrefix() + "*", 500,
                Integer.MAX_VALUE).getKeys()).hasSize(initialSize - initialTargetCount);
    }

    private void benchmarkDataSet(int totalSessions, int targetSessionCount)
    {
        REDIS.cleanupGeneratedKeys();
        seedDataSet(totalSessions, targetSessionCount);
        String sentinelSession = REDIS.sessionId("other", 0);
        String malformedSession = REDIS.sessionId("malformed", 0);
        String businessKey = REDIS.businessKey("ttl_probe_" + totalSessions);
        redisService.setCacheObject(businessKey, "unrelated-business-value", 1_800L,
                TimeUnit.SECONDS);

        List<Long> samples = new ArrayList<>();
        int warmups = 2;
        int iterations = 20;
        for (int iteration = -warmups; iteration < iterations; iteration++)
        {
            restoreTargetSessions(targetSessionCount);
            long sentinelTtlBefore = redisService.getExpire(REDIS.sessionKey(sentinelSession));
            long businessTtlBefore = redisService.getExpire(businessKey);

            long started = System.nanoTime();
            int deleted = tokenService.deleteLoginUsersByUserId(TARGET_USER_ID);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (iteration >= 0)
            {
                samples.add(durationMillis);
            }

            assertThat(deleted).isEqualTo(targetSessionCount);
            assertThat(countTargetSessions()).isZero();
            assertThat(redisService.hasKey(REDIS.sessionKey(sentinelSession))).isTrue();
            assertThat(redisService.hasKey(REDIS.sessionKey(malformedSession))).isTrue();
            Object businessValue = redisService.getCacheObject(businessKey);
            assertThat(businessValue).isEqualTo("unrelated-business-value");
            assertTtlNotRefreshed(sentinelTtlBefore,
                    redisService.getExpire(REDIS.sessionKey(sentinelSession)));
            assertTtlNotRefreshed(businessTtlBefore, redisService.getExpire(businessKey));
            assertThat(redisService.scanKeys("login_tokens:" + REDIS.getPrefix() + "*", 500,
                    Integer.MAX_VALUE).getKeys()).hasSize(totalSessions - targetSessionCount);
        }

        Collections.sort(samples);
        long p50 = percentile(samples, 0.50);
        long p95 = percentile(samples, 0.95);
        long max = samples.get(samples.size() - 1);
        long threshold = thresholdFor(totalSessions);
        System.out.printf("[redis-benchmark] operation=revoke dataset=%d samples=%s "
                        + "p50_ms=%d p95_ms=%d max_ms=%d threshold_ms=%d%n",
                totalSessions, samples, p50, p95, max, threshold);
        assertThat(p95).as("%d 会话撤销 p95", totalSessions).isLessThanOrEqualTo(threshold);
    }

    private void seedDataSet(int totalSessions, int targetSessionCount)
    {
        restoreTargetSessions(targetSessionCount);
        REDIS.putLoginUser(REDIS.sessionId("admin", 0), 1L, "admin", 1_200);
        REDIS.putMalformedSession(REDIS.sessionId("malformed", 0), 900);
        int otherCount = totalSessions - targetSessionCount - 2;
        for (int i = 0; i < otherCount; i++)
        {
            long ttl = 600L + (i % 3) * 600L;
            REDIS.putLoginUser(REDIS.sessionId("other", i), 10_000L + (i % 2_000),
                    "other-" + (i % 2_000), ttl);
        }
        String expired = REDIS.sessionId("expired", totalSessions);
        REDIS.putLoginUser(expired, 77_777L, "expired-user", 60);
        redisService.expire(REDIS.sessionKey(expired), -1, TimeUnit.SECONDS);
        assertThat(redisService.hasKey(REDIS.sessionKey(expired))).isFalse();
        assertThat(redisService.scanKeys("login_tokens:" + REDIS.getPrefix() + "*", 500,
                Integer.MAX_VALUE).getKeys()).hasSize(totalSessions);
    }

    private void restoreTargetSessions(int targetSessionCount)
    {
        for (String sessionId : targetSessionIds(targetSessionCount))
        {
            REDIS.putLoginUser(sessionId, TARGET_USER_ID, "target-user", 1_800);
        }
    }

    private List<String> targetSessionIds(int count)
    {
        List<String> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            sessions.add(REDIS.sessionId("target", i));
        }
        return sessions;
    }

    private int countTargetSessions()
    {
        int count = 0;
        for (String key : redisService.scanKeys("login_tokens:" + REDIS.getPrefix() + "*", 500,
                Integer.MAX_VALUE).getKeys())
        {
            var loginUser = tokenService.getLoginUserByCacheKey(key);
            if (loginUser != null && Long.valueOf(TARGET_USER_ID).equals(loginUser.getUserid()))
            {
                count++;
            }
        }
        return count;
    }

    private void assertTtlNotRefreshed(long before, long after)
    {
        assertThat(after).isPositive().isLessThanOrEqualTo(before);
    }

    private long percentile(List<Long> sorted, double percentile)
    {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private long thresholdFor(int totalSessions)
    {
        String environmentName = "ERP_IT_REDIS_REVOKE_P95_MAX_MS_" + totalSessions;
        String configured = System.getenv(environmentName);
        if (configured != null && !configured.isBlank())
        {
            return Long.parseLong(configured);
        }
        if (totalSessions <= 100)
        {
            return 1_000L;
        }
        if (totalSessions <= 1_000)
        {
            return 3_000L;
        }
        return 15_000L;
    }
}
