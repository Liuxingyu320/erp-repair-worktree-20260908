package com.erp.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import com.erp.auth.config.AuthSessionProperties;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;

/**
 * 签发和撤销 HttpOnly Web 会话 Cookie，并维护与 session UUID 绑定的 CSRF token。
 */
@Service
public class AuthSessionCookieService
{
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    private AuthSessionProperties properties;

    @Autowired
    private RedisService redisService;

    public void issueLoginCookies(Map<String, Object> tokenPayload, HttpServletResponse response)
    {
        issueLoginCookies(tokenPayload, response, false);
    }

    public void issueLoginCookies(Map<String, Object> tokenPayload, HttpServletResponse response,
            boolean cookiePreferred)
    {
        Object accessToken = tokenPayload.get("access_token");
        if (!properties.issuesCookies() || accessToken == null)
        {
            return;
        }
        issueCookies(accessToken.toString(), response);
        if (!properties.exposesBearerInBody() || cookiePreferred)
        {
            tokenPayload.remove("access_token");
        }
    }

    public void refreshCookies(String token, HttpServletResponse response)
    {
        if (properties.issuesCookies() && StringUtils.isNotEmpty(token))
        {
            issueCookies(token, response);
        }
    }

    public void rotateCsrf(String token, HttpServletResponse response)
    {
        if (!properties.issuesCookies() || !properties.isCsrfEnabled() || StringUtils.isEmpty(token))
        {
            return;
        }
        issueCsrfCookie(token, response);
    }

    public void clearCookies(String token, HttpServletResponse response)
    {
        deleteCsrfBinding(token);
        addCookie(response, buildCookie(properties.getCookieName(), "", true, Duration.ZERO));
        addCookie(response, buildCookie(properties.getCsrfCookieName(), "", false, Duration.ZERO));
    }

    private void issueCookies(String token, HttpServletResponse response)
    {
        addCookie(response, buildCookie(properties.getCookieName(), token, true, properties.getMaxAge()));
        if (properties.isCsrfEnabled())
        {
            issueCsrfCookie(token, response);
        }
    }

    private void issueCsrfCookie(String token, HttpServletResponse response)
    {
        String userKey = JwtUtils.getUserKey(token);
        if (StringUtils.isEmpty(userKey))
        {
            throw new IllegalArgumentException("Cannot bind CSRF token to an invalid session");
        }
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String csrfToken = TOKEN_ENCODER.encodeToString(randomBytes);
        redisService.setCacheObject(CacheConstants.CSRF_TOKEN_KEY + userKey, csrfToken,
                properties.getMaxAge().toMinutes(), TimeUnit.MINUTES);
        addCookie(response, buildCookie(properties.getCsrfCookieName(), csrfToken, false, properties.getMaxAge()));
    }

    private void deleteCsrfBinding(String token)
    {
        if (StringUtils.isEmpty(token))
        {
            return;
        }
        try
        {
            String userKey = JwtUtils.getUserKey(token);
            if (StringUtils.isNotEmpty(userKey))
            {
                redisService.deleteObject(CacheConstants.CSRF_TOKEN_KEY + userKey);
            }
        }
        catch (RuntimeException ignored)
        {
            // 注销仍须清客户端 Cookie；无效 JWT 没有可信的 Redis key 可删除。
        }
    }

    private ResponseCookie buildCookie(String name, String value, boolean httpOnly, Duration maxAge)
    {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.isSecure())
                .path(properties.getCookiePath())
                .sameSite(properties.getSameSite())
                .maxAge(maxAge)
                .build();
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie)
    {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
