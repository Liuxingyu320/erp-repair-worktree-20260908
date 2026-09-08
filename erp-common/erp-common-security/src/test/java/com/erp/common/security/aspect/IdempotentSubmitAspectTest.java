package com.erp.common.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.SignScopeHeaderUtils;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.security.annotation.IdempotentSubmit;
import com.erp.common.security.service.IdempotentSubmitService;

class IdempotentSubmitAspectTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.remove();
    }

    @Test
    void shouldRejectDuplicateRequestWithSameUserShopMethodUriAndBody() throws Throwable
    {
        SecurityContextHolder.setUserId("7");
        bindRequest("POST", "/stock/adjust", "201", null);
        FakeIdempotentSubmitService service = new FakeIdempotentSubmitService();
        IdempotentSubmitAspect aspect = newAspect(service);
        AtomicInteger proceedCount = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint(new Object[] { new SubmitBody(100L, "3") }, () -> {
            proceedCount.incrementAndGet();
            return "ok";
        });

        Object result = aspect.around(joinPoint, annotation(30, true, "请勿重复提交"));

        assertThat(result).isEqualTo("ok");
        assertThatThrownBy(() -> aspect.around(joinPoint, annotation(30, true, "请勿重复提交")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请勿重复提交");
        assertThat(proceedCount).hasValue(1);
        assertThat(service.keys).hasSize(2);
        assertThat(service.keys.get(1)).isEqualTo(service.keys.get(0));
        assertThat(service.keys.get(0))
                .contains("user:7", "dept:201", "POST", "/stock/adjust", "body:");
    }

    @Test
    void shouldReleaseIdempotentKeyWhenBusinessThrows() throws Throwable
    {
        SecurityContextHolder.setUserId("8");
        bindRequest("POST", "/transfer/deliver/300", "202", "client-key-1");
        FakeIdempotentSubmitService service = new FakeIdempotentSubmitService();
        IdempotentSubmitAspect aspect = newAspect(service);
        ProceedingJoinPoint failingJoinPoint = joinPoint(new Object[] { new SubmitBody(200L, "5") }, () -> {
            throw new IllegalStateException("business failed");
        });

        assertThatThrownBy(() -> aspect.around(failingJoinPoint, annotation(30, true, "请勿重复提交")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business failed");

        assertThat(service.releasedKeys).containsExactly(service.keys.get(0));
        assertThat(service.keys.get(0)).contains("client:client-key-1");
    }

    @Test
    void shouldReleaseInFlightGuardAfterDurablyIdempotentSuccess()
            throws Throwable
    {
        SecurityContextHolder.setUserId("9");
        bindRequest("POST", "/inventory/customer/service-card", "203",
                null);
        FakeIdempotentSubmitService service = new FakeIdempotentSubmitService();
        IdempotentSubmitAspect aspect = newAspect(service);
        AtomicInteger proceedCount = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint(
                new Object[] { new SubmitBody(300L, "customer-key") },
                () -> {
                    proceedCount.incrementAndGet();
                    return "replayable";
                });

        IdempotentSubmit annotation = annotation(30, true, true,
                "请勿重复提交");
        assertThat(aspect.around(joinPoint, annotation))
                .isEqualTo("replayable");
        assertThat(aspect.around(joinPoint, annotation))
                .isEqualTo("replayable");

        assertThat(proceedCount).hasValue(2);
        assertThat(service.releasedKeys).hasSize(2);
    }

    @Test
    void shouldUseDedicatedSignScopeInIdempotencyKey()
    {
        SecurityContextHolder.setUserId("940");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/signPackage/plan/7/publish");
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, "1185");
        request.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1157");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String key = newAspect(new FakeIdempotentSubmitService())
                .buildKey(joinPoint(new Object[0], () -> "ok"));

        assertThat(key).contains("user:940", "dept:1157", "/signPackage/plan/7/publish")
                .doesNotContain("dept:1185");
    }

    @Test
    void shouldIgnoreSigningHeaderForInventoryIdempotencyKey()
    {
        SecurityContextHolder.setUserId("940");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inventory/transfer");
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, "1185");
        request.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1157");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String key = newAspect(new FakeIdempotentSubmitService())
                .buildKey(joinPoint(new Object[0], () -> "ok"));

        assertThat(key).contains("dept:1185").doesNotContain("dept:1157");
    }

    @Test
    void shouldNotTreatSimilarNonSigningPathAsSigningEndpoint()
    {
        SecurityContextHolder.setUserId("940");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/signPackageArchive/export");
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, "1185");
        request.addHeader(SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1157");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String key = newAspect(new FakeIdempotentSubmitService())
                .buildKey(joinPoint(new Object[0], () -> "ok"));

        assertThat(key).contains("dept:1185").doesNotContain("dept:1157");
    }

    private static IdempotentSubmitAspect newAspect(FakeIdempotentSubmitService service)
    {
        IdempotentSubmitAspect aspect = new IdempotentSubmitAspect();
        ReflectionTestUtils.setField(aspect, "idempotentSubmitService", service);
        return aspect;
    }

    private static void bindRequest(String method, String uri, String shopDeptId, String idempotencyKey)
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader(ShopHeaderUtils.SHOP_HEADER, shopDeptId);
        if (idempotencyKey != null)
        {
            request.addHeader("Idempotency-Key", idempotencyKey);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static IdempotentSubmit annotation(long timeout, boolean releaseOnFailure, String message)
    {
        return annotation(timeout, releaseOnFailure, false, message);
    }

    private static IdempotentSubmit annotation(long timeout,
            boolean releaseOnFailure, boolean releaseOnSuccess,
            String message)
    {
        return new IdempotentSubmit()
        {
            @Override
            public long timeout()
            {
                return timeout;
            }

            @Override
            public boolean releaseOnFailure()
            {
                return releaseOnFailure;
            }

            @Override
            public boolean releaseOnSuccess()
            {
                return releaseOnSuccess;
            }

            @Override
            public String message()
            {
                return message;
            }

            @Override
            public Class<? extends Annotation> annotationType()
            {
                return IdempotentSubmit.class;
            }
        };
    }

    private static ProceedingJoinPoint joinPoint(Object[] pointArgs, ThrowingSupplier supplier)
    {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[] { ProceedingJoinPoint.class }, (proxy, method, args) -> {
                    if ("proceed".equals(method.getName()))
                    {
                        return supplier.get();
                    }
                    if ("getArgs".equals(method.getName()))
                    {
                        return pointArgs;
                    }
                    if ("toString".equals(method.getName()))
                    {
                        return "testJoinPoint";
                    }
                    if (method.getReturnType().isPrimitive())
                    {
                        return primitiveDefault(method.getReturnType());
                    }
                    return null;
                });
    }

    private static Object primitiveDefault(Class<?> type)
    {
        if (boolean.class.equals(type))
        {
            return false;
        }
        if (char.class.equals(type))
        {
            return '\0';
        }
        return 0;
    }

    private interface ThrowingSupplier
    {
        Object get() throws Throwable;
    }

    private record SubmitBody(Long id, String quantity)
    {
    }

    private static class FakeIdempotentSubmitService extends IdempotentSubmitService
    {
        private final Set<String> lockedKeys = new HashSet<>();
        private final List<String> keys = new ArrayList<>();
        private final List<String> releasedKeys = new ArrayList<>();

        @Override
        public boolean tryAcquire(String key, long timeoutSeconds)
        {
            keys.add(key);
            return lockedKeys.add(key);
        }

        @Override
        public void release(String key)
        {
            releasedKeys.add(key);
            lockedKeys.remove(key);
        }
    }
}
