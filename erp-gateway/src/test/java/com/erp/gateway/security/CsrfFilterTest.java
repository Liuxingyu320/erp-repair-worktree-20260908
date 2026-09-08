package com.erp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.gateway.config.properties.AuthSessionProperties;
import com.erp.gateway.filter.CsrfFilter;
import reactor.core.publisher.Mono;

class CsrfFilterTest
{
    private static String originalJwtSecret;

    @BeforeAll
    static void configureJwtSecret()
    {
        originalJwtSecret = System.getProperty("erp.jwt.secret");
        System.setProperty("erp.jwt.secret", "test-csrf-filter-session-secret-20260728");
    }

    @AfterAll
    static void restoreJwtSecret()
    {
        if (originalJwtSecret == null)
        {
            System.clearProperty("erp.jwt.secret");
        }
        else
        {
            System.setProperty("erp.jwt.secret", originalJwtSecret);
        }
    }

    @Test
    void cookieMutationWithoutCsrfIsRejected()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = MockServerWebExchange.from(basePost(jwt));

        filter("csrf-value").filter(exchange,
                next -> Mono.error(new AssertionError("request should be rejected")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void cookieMutationWithRedisBoundDoubleSubmitTokenPasses()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = MockServerWebExchange.from(basePost(jwt)
                .header(SecurityConstants.CSRF_HEADER, "csrf-value")
                .cookie(new HttpCookie("XSRF-TOKEN", "csrf-value")));
        AtomicBoolean passed = new AtomicBoolean();

        filter("csrf-value").filter(exchange, next -> {
            passed.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(passed).isTrue();
    }

    @Test
    void bearerMutationDoesNotRequireCsrf()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/system/user")
                .header(SecurityConstants.AUTH_SOURCE_HEADER, SecurityConstants.AUTH_SOURCE_BEARER)
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + jwt));
        AtomicBoolean passed = new AtomicBoolean();

        filter("csrf-value").filter(exchange, next -> {
            passed.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(passed).isTrue();
    }

    @Test
    void wrongCsrfTokenIsRejected()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = MockServerWebExchange.from(basePost(jwt)
                .header(SecurityConstants.CSRF_HEADER, "attacker-value")
                .cookie(new HttpCookie("XSRF-TOKEN", "attacker-value")));

        filter("csrf-value").filter(exchange,
                next -> Mono.error(new AssertionError("request should be rejected")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(exchange.getResponse().getHeaders().getFirst(SecurityConstants.CSRF_REQUIRED_HEADER))
                .isEqualTo("true");
    }

    @Test
    void untrustedOriginIsRejectedBeforeTokenValidation()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/system/user")
                .header(SecurityConstants.AUTH_SOURCE_HEADER, SecurityConstants.AUTH_SOURCE_COOKIE)
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + jwt)
                .header(SecurityConstants.CSRF_HEADER, "csrf-value")
                .header("Origin", "https://evil.example")
                .cookie(new HttpCookie("XSRF-TOKEN", "csrf-value")));

        filter("csrf-value").filter(exchange,
                next -> Mono.error(new AssertionError("request should be rejected")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    private MockServerHttpRequest.BaseBuilder<?> basePost(String jwt)
    {
        return MockServerHttpRequest.post("/system/user")
                .header(SecurityConstants.AUTH_SOURCE_HEADER, SecurityConstants.AUTH_SOURCE_COOKIE)
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + jwt)
                .header("Origin", "http://localhost:1025");
    }

    private CsrfFilter filter(String expected)
    {
        CsrfFilter filter = new CsrfFilter();
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setMode(AuthSessionProperties.Mode.DUAL);
        properties.setAllowedOrigins(List.of("http://localhost:1025"));
        properties.setRequireOrigin(true);
        RedisService redis = mock(RedisService.class);
        when(redis.getCacheObject(CacheConstants.CSRF_TOKEN_KEY + "session-a")).thenReturn(expected);
        ReflectionTestUtils.setField(filter, "sessionProperties", properties);
        ReflectionTestUtils.setField(filter, "redisService", redis);
        return filter;
    }

    private String token(String userKey)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.USER_KEY, userKey);
        claims.put(SecurityConstants.DETAILS_USER_ID, 7L);
        claims.put(SecurityConstants.DETAILS_USERNAME, "tester");
        return JwtUtils.createToken(claims);
    }
}
