package com.erp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.gateway.config.properties.AuthSessionProperties;
import com.erp.gateway.config.properties.IgnoreWhiteProperties;
import com.erp.gateway.filter.AuthFilter;
import reactor.core.publisher.Mono;

class AuthFilterSessionModeTest
{
    private static String originalJwtSecret;

    @BeforeAll
    static void configureJwtSecret()
    {
        originalJwtSecret = System.getProperty("erp.jwt.secret");
        System.setProperty("erp.jwt.secret", "test-auth-filter-session-secret-20260728");
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
    void headerOnlyRemainsBearerAuthenticated()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + jwt));
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> downstream =
                runSuccess(exchange);

        assertThat(downstream.get().getHeaders().getFirst(SecurityConstants.AUTH_SOURCE_HEADER))
                .isEqualTo(SecurityConstants.AUTH_SOURCE_BEARER);
    }

    @Test
    void cookieOnlyIsAuthenticatedAndAuthorizationIsSupplemented()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .cookie(new HttpCookie("ERP_SESSION", jwt)));
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> downstream =
                runSuccess(exchange);

        assertThat(downstream.get().getHeaders().getFirst(SecurityConstants.AUTH_SOURCE_HEADER))
                .isEqualTo(SecurityConstants.AUTH_SOURCE_COOKIE);
        assertThat(downstream.get().getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER))
                .isEqualTo(TokenConstants.PREFIX + jwt);
    }

    @Test
    void sameHeaderAndCookieAreAccepted()
    {
        String jwt = token("session-a");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + jwt)
                .cookie(new HttpCookie("ERP_SESSION", jwt)));

        assertThat(runSuccess(exchange).get()).isNotNull();
    }

    @Test
    void mismatchedHeaderAndCookieAreRejectedAndCookiesExpired()
    {
        String header = token("session-a");
        String cookie = token("session-b");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + header)
                .cookie(new HttpCookie("ERP_SESSION", cookie)));

        runRejected(exchange);

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getCookies().getFirst("ERP_SESSION").getMaxAge().isZero()).isTrue();
    }

    @Test
    void badHeaderNeverFallsBackToValidCookie()
    {
        String cookie = token("session-a");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + "bad-token")
                .cookie(new HttpCookie("ERP_SESSION", cookie)));

        runRejected(exchange);

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void emptyBearerHeaderNeverFallsBackToValidCookie()
    {
        String cookie = token("session-a");
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/system/user/getInfo")
                .header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX)
                .cookie(new HttpCookie("ERP_SESSION", cookie)));

        runRejected(exchange);

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginIgnoresStaleCookieAndStripsForgedInternalAuthSource()
    {
        AuthFilter filter = filter();
        IgnoreWhiteProperties ignore = new IgnoreWhiteProperties();
        ignore.setWhites(List.of("/auth/login"));
        ReflectionTestUtils.setField(filter, "ignoreWhite", ignore);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/auth/login")
                .header(SecurityConstants.AUTH_SOURCE_HEADER, SecurityConstants.AUTH_SOURCE_COOKIE)
                .cookie(new HttpCookie("ERP_SESSION", "stale-token")));
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> downstream =
                new AtomicReference<>();

        filter.filter(exchange, next -> {
            downstream.set(next.getRequest());
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(downstream.get().getHeaders().getFirst(SecurityConstants.AUTH_SOURCE_HEADER)).isNull();
    }

    private AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> runSuccess(
            MockServerWebExchange exchange)
    {
        AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> downstream =
                new AtomicReference<>();
        filter().filter(exchange, next -> {
            downstream.set(next.getRequest());
            return Mono.empty();
        }).block(Duration.ofSeconds(1));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        return downstream;
    }

    private void runRejected(MockServerWebExchange exchange)
    {
        filter().filter(exchange, next -> Mono.error(new AssertionError("request should be rejected")))
                .block(Duration.ofSeconds(1));
    }

    private AuthFilter filter()
    {
        AuthFilter filter = new AuthFilter();
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setMode(AuthSessionProperties.Mode.DUAL);
        properties.setSecure(false);
        RedisService redis = mock(RedisService.class);
        when(redis.hasKey(anyString())).thenReturn(true);
        ReflectionTestUtils.setField(filter, "ignoreWhite", new IgnoreWhiteProperties());
        ReflectionTestUtils.setField(filter, "sessionProperties", properties);
        ReflectionTestUtils.setField(filter, "redisService", redis);
        return filter;
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request)
    {
        return MockServerWebExchange.from(request);
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
