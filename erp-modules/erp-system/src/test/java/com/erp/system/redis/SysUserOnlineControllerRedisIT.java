package com.erp.system.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.AjaxResult;
import com.erp.common.redis.service.RedisService;
import com.erp.common.security.service.TokenService;
import com.erp.system.controller.SysUserOnlineController;
import com.erp.system.domain.SysUserOnline;
import com.erp.system.domain.SysUserOnlineTableDataInfo;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.impl.SysUserOnlineServiceImpl;
import com.erp.system.testsupport.NativeRedisIntegrationTestSupport;

@DisplayName("在线用户控制器原生 Redis")
class SysUserOnlineControllerRedisIT
{
    private static NativeRedisIntegrationTestSupport REDIS;

    private RedisService redisService;

    private TokenService tokenService;

    private ISysUserService userService;

    private SysUserOnlineController controller;

    @BeforeAll
    static void openRedis()
    {
        REDIS = NativeRedisIntegrationTestSupport.create("online_controller");
    }

    @BeforeEach
    void setUp()
    {
        REDIS.cleanupGeneratedKeys();
        redisService = REDIS.getRedisService();
        tokenService = REDIS.getTokenService();
        userService = mock(ISysUserService.class);
        when(userService.selectVisibleUserIds(anyCollection()))
                .thenAnswer(invocation -> new LinkedHashSet<>(invocation.getArgument(0)));
        controller = new SysUserOnlineController();
        ReflectionTestUtils.setField(controller, "redisService", redisService);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "userOnlineService", new SysUserOnlineServiceImpl());
        SecurityContextHolder.remove();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
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
    @DisplayName("真实序列化数据支持筛选、分页、损坏值容错和当前会话标记")
    void listsRealSessionsWithFilteringPaginationAndMalformedTolerance()
    {
        String currentSession = REDIS.sessionId("current", 0);
        REDIS.putLoginUser(currentSession, 21L, "current-user", 1_800);
        REDIS.putLoginUser(REDIS.sessionId("target", 0), 42L, "target-user", 1_200);
        REDIS.putMalformedSession(REDIS.sessionId("malformed", 0), 900);
        for (int i = 0; i < 97; i++)
        {
            REDIS.putLoginUser(REDIS.sessionId("other", i), 10_000L + i,
                    "other-" + i, 600L + (i % 3) * 600L);
        }
        bindRequest(1, 20);
        SecurityContextHolder.setUserKey(currentSession);

        SysUserOnlineTableDataInfo response =
                (SysUserOnlineTableDataInfo) controller.list(null, "target-user");

        assertThat(response.isTruncated()).isFalse();
        assertThat(response.getScannedCount()).isEqualTo(100);
        assertThat(response.getInvalidSessionCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getRows()).hasSize(1);
        SysUserOnline row = (SysUserOnline) response.getRows().get(0);
        assertThat(row.getUserName()).isEqualTo("target-user");
        assertThat(row.isCurrentSession()).isFalse();
    }

    @Test
    @DisplayName("真实 Redis 强退执行数据范围校验、自退保护、管理员保护和幂等删除")
    void forceLogoutHonorsIdentityAndIdempotencyBoundaries()
    {
        String current = REDIS.sessionId("current", 0);
        String target = REDIS.sessionId("target", 0);
        String admin = REDIS.sessionId("admin", 0);
        REDIS.putLoginUser(current, 21L, "operator", 1_800);
        REDIS.putLoginUser(target, 42L, "target-user", 1_800);
        REDIS.putLoginUser(admin, 1L, "admin", 1_800);
        SecurityContextHolder.setUserId("21");
        SecurityContextHolder.setUserKey(current);

        assertThatThrownBy(() -> controller.forceLogout(current))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不能强退当前会话，请使用退出登录");

        assertThatThrownBy(() -> controller.forceLogout(admin))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不允许强退管理员会话");
        assertThat(redisService.hasKey(REDIS.sessionKey(admin))).isTrue();
        verify(userService, never()).checkUserDataScope(1L);

        AjaxResult first = controller.forceLogout(target);
        assertThat(first.get(AjaxResult.MSG_TAG)).isEqualTo("强退成功");
        assertThat(redisService.hasKey(REDIS.sessionKey(target))).isFalse();
        verify(userService).checkUserDataScope(42L);

        AjaxResult second = controller.forceLogout(target);
        assertThat(second.get(AjaxResult.MSG_TAG)).isEqualTo("会话已离线");
    }

    @Test
    @DisplayName("10000 会话在线列表有界截断并记录 p50/p95")
    void boundsAndBenchmarksTenThousandSessionListing()
    {
        for (int i = 0; i < 10_000; i++)
        {
            REDIS.putLoginUser(REDIS.sessionId("bulk", i), 20_000L + (i % 3_000),
                    "bulk-" + i, 600L + (i % 3) * 600L);
        }
        bindRequest(1, 100);

        List<Long> samples = new ArrayList<>();
        SysUserOnlineTableDataInfo response = null;
        int warmups = 2;
        int iterations = 20;
        for (int i = -warmups; i < iterations; i++)
        {
            long started = System.nanoTime();
            response = (SysUserOnlineTableDataInfo) controller.list(null, null);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (i >= 0)
            {
                samples.add(durationMillis);
            }
            assertThat(response.isTruncated()).isTrue();
            assertThat(response.getScanLimit()).isEqualTo(5_000);
            assertThat(response.getScannedCount()).isEqualTo(5_001);
            assertThat(response.getTotal()).isEqualTo(5_000);
            assertThat(response.getRows()).hasSize(100);
        }

        Collections.sort(samples);
        long p50 = percentile(samples, 0.50);
        long p95 = percentile(samples, 0.95);
        long max = samples.get(samples.size() - 1);
        long threshold = threshold();
        System.out.printf("[redis-benchmark] operation=online-list dataset=10000 samples=%s "
                        + "p50_ms=%d p95_ms=%d max_ms=%d threshold_ms=%d%n",
                samples, p50, p95, max, threshold);
        assertThat(p95).as("10000 会话在线列表 p95").isLessThanOrEqualTo(threshold);
    }

    private void bindRequest(int pageNum, int pageSize)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pageNum", String.valueOf(pageNum));
        request.setParameter("pageSize", String.valueOf(pageSize));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private long percentile(List<Long> sorted, double percentile)
    {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private long threshold()
    {
        String configured = System.getenv("ERP_IT_REDIS_ONLINE_P95_MAX_MS_10000");
        return configured == null || configured.isBlank() ? 15_000L : Long.parseLong(configured);
    }
}
