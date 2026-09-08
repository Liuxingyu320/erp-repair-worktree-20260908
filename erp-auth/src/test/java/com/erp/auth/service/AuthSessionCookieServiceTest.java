package com.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.auth.config.AuthSessionProperties;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.redis.service.RedisService;

class AuthSessionCookieServiceTest
{
    private static String originalJwtSecret;

    @BeforeAll
    static void configureJwtSecret()
    {
        originalJwtSecret = System.getProperty("erp.jwt.secret");
        System.setProperty("erp.jwt.secret", "test-auth-session-cookie-secret-20260728");
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
    void dualModeIssuesHttpOnlySessionAndBoundCsrfCookie()
    {
        AuthSessionProperties properties = properties(AuthSessionProperties.Mode.DUAL);
        RedisService redis = mock(RedisService.class);
        AuthSessionCookieService service = service(properties, redis);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", token("session-a"));

        service.issueLoginCookies(payload, response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).anySatisfy(value -> {
            assertThat(value).contains("ERP_SESSION=").contains("HttpOnly").contains("SameSite=Lax")
                    .contains("Path=/").contains("Max-Age=43200");
            assertThat(value).doesNotContain("Secure");
        });
        assertThat(cookies).anySatisfy(value -> {
            assertThat(value).contains("XSRF-TOKEN=").contains("SameSite=Lax").contains("Path=/");
            assertThat(value).doesNotContain("HttpOnly");
        });
        assertThat(payload).containsKey("access_token");
        verify(redis).setCacheObject(eq(CacheConstants.CSRF_TOKEN_KEY + "session-a"), startsWith(""),
                eq(720L), eq(TimeUnit.MINUTES));
    }

    @Test
    void bearerModeDoesNotIssueCookies()
    {
        AuthSessionProperties properties = properties(AuthSessionProperties.Mode.BEARER);
        RedisService redis = mock(RedisService.class);
        AuthSessionCookieService service = service(properties, redis);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", token("session-a"));

        service.issueLoginCookies(payload, response);

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(payload).containsKey("access_token");
        verifyNoInteractions(redis);
    }

    @Test
    void cookiePreferredClientInDualModeNeverReceivesJwtInBody()
    {
        AuthSessionProperties properties = properties(AuthSessionProperties.Mode.DUAL);
        RedisService redis = mock(RedisService.class);
        AuthSessionCookieService service = service(properties, redis);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", token("session-a"));

        service.issueLoginCookies(payload, response, true);

        assertThat(payload).doesNotContainKey("access_token");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
    }

    @Test
    void cookieModeHidesBearerAndLogoutExpiresBothCookies()
    {
        AuthSessionProperties properties = properties(AuthSessionProperties.Mode.COOKIE);
        RedisService redis = mock(RedisService.class);
        AuthSessionCookieService service = service(properties, redis);
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        String token = token("session-a");
        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", token);

        service.issueLoginCookies(payload, loginResponse);
        MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
        service.clearCookies(token, logoutResponse);

        assertThat(payload).doesNotContainKey("access_token");
        assertThat(logoutResponse.getHeaders("Set-Cookie")).hasSize(2)
                .allSatisfy(value -> assertThat(value).contains("Max-Age=0").contains("Path=/"));
        verify(redis).deleteObject(CacheConstants.CSRF_TOKEN_KEY + "session-a");
    }

    private AuthSessionProperties properties(AuthSessionProperties.Mode mode)
    {
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setMode(mode);
        properties.setSecure(false);
        return properties;
    }

    private AuthSessionCookieService service(AuthSessionProperties properties, RedisService redis)
    {
        AuthSessionCookieService service = new AuthSessionCookieService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "redisService", redis);
        return service;
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
