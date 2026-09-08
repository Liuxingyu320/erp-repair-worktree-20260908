package com.erp.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.security.BrowserSessionSecurity;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.gateway.config.properties.BrowserSessionProperties;
import com.erp.gateway.config.properties.IgnoreWhiteProperties;
import com.erp.gateway.filter.AuthFilter;
import reactor.core.publisher.Mono;

class AuthFilterBrowserSessionTest
{
    private static final String CSRF_SECRET = "erp-new-2-browser-csrf-test-secret-0001";

    private String originalProfile;

    private AuthFilter filter;

    private RedisService redisService;

    private BrowserSessionProperties properties;

    private String accessToken;

    @BeforeEach
    void setUp()
    {
        originalProfile = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "local");
        accessToken = JwtUtils.createToken(Map.of(
                SecurityConstants.USER_KEY, "browser-session-id",
                SecurityConstants.DETAILS_USER_ID, "42",
                SecurityConstants.DETAILS_USERNAME, "erp-new-2-user"));

        filter = new AuthFilter();
        redisService = mock(RedisService.class);
        properties = new BrowserSessionProperties();
        properties.setEnabled(true);
        properties.setCsrfSecret(CSRF_SECRET);
        ReflectionTestUtils.setField(filter, "ignoreWhite", new IgnoreWhiteProperties());
        ReflectionTestUtils.setField(filter, "redisService", redisService);
        ReflectionTestUtils.setField(filter, "browserSessionProperties", properties);
        when(redisService.hasKey(CacheConstants.LOGIN_TOKEN_KEY + "browser-session-id"))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown()
    {
        if (originalProfile == null)
        {
            System.clearProperty("spring.profiles.active");
        }
        else
        {
            System.setProperty("spring.profiles.active", originalProfile);
        }
    }

    @Test
    void safeCookieRequestInjectsBearerOnlyForTrustedDownstreamServices()
    {
        MockServerWebExchange exchange = cookieExchange(
                MockServerHttpRequest.get("/system/user/getInfo")
                        .header(SecurityConstants.DETAILS_USER_ID, "spoofed-user-id"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.AUTHORIZATION_HEADER))
                .isEqualTo(TokenConstants.PREFIX + accessToken);
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(SecurityConstants.DETAILS_USER_ID)).isEqualTo("42");
        assertThat(forwarded.get().getRequest().getHeaders()
                .get(SecurityConstants.DETAILS_USER_ID)).containsExactly("42");
        verify(redisService).hasKey(CacheConstants.LOGIN_TOKEN_KEY + "browser-session-id");
    }

    @Test
    void unsafeCookieRequestRequiresSessionBoundCsrfProof()
    {
        MockServerWebExchange missing = cookieExchange(
                MockServerHttpRequest.post("/inventory/transfer/submit"));

        filter.filter(missing, next -> Mono.error(new AssertionError("request must stop")))
                .block(Duration.ofSeconds(1));

        assertThat(missing.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(missing.getResponse().getBodyAsString().block())
                .contains("\"code\":403")
                .contains("CSRF");

        String csrf = BrowserSessionSecurity.csrfToken(accessToken, CSRF_SECRET);
        MockServerWebExchange accepted = cookieExchange(
                MockServerHttpRequest.post("/inventory/transfer/submit")
                        .header(BrowserSessionSecurity.CSRF_HEADER_NAME, csrf));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(accepted, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void tamperedCsrfProofIsRejected()
    {
        MockServerWebExchange exchange = cookieExchange(
                MockServerHttpRequest.delete("/auth/browser/session")
                        .header(BrowserSessionSecurity.CSRF_HEADER_NAME, "tampered"));

        filter.filter(exchange, next -> Mono.error(new AssertionError("request must stop")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void bearerCommandsRemainCompatibleWithoutBrowserCsrfHeader()
    {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/inventory/transfer/submit")
                        .header(SecurityConstants.AUTHORIZATION_HEADER,
                                TokenConstants.PREFIX + accessToken));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void browserSessionEndpointsAreNonCacheableEvenWhenWhitelisted()
    {
        IgnoreWhiteProperties ignoreWhite = new IgnoreWhiteProperties();
        ignoreWhite.setWhites(List.of("/auth/browser/login"));
        ReflectionTestUtils.setField(filter, "ignoreWhite", ignoreWhite);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/browser/login"));

        filter.filter(exchange, next -> Mono.empty()).block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control"))
                .isEqualTo("no-store, max-age=0");
        assertThat(exchange.getResponse().getHeaders().getFirst("Pragma"))
                .isEqualTo("no-cache");
    }

    @Test
    void disabledBrowserSessionDoesNotAcceptEvenTheDedicatedCookie()
    {
        properties.setEnabled(false);
        MockServerWebExchange exchange = cookieExchange(
                MockServerHttpRequest.get("/system/user/getInfo"));

        filter.filter(exchange, next -> Mono.error(new AssertionError("request must stop")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void misconfiguredBrowserSessionFailsClosedBeforeCookieAuthentication()
    {
        properties.setCsrfSecret("too-short");
        MockServerWebExchange exchange = cookieExchange(
                MockServerHttpRequest.get("/system/user/getInfo"));

        filter.filter(exchange, next -> Mono.error(new AssertionError("request must stop")))
                .block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    private MockServerWebExchange cookieExchange(MockServerHttpRequest.BaseBuilder<?> builder)
    {
        return MockServerWebExchange.from(builder.cookie(
                new HttpCookie(BrowserSessionSecurity.COOKIE_NAME, accessToken)));
    }
}
