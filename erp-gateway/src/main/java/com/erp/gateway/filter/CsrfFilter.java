package com.erp.gateway.filter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.gateway.config.properties.AuthSessionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * 仅对 Cookie 认证的非安全方法执行 CSRF 校验；纯 Bearer 客户端（含 Native）不受影响。
 */
@Component
public class CsrfFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered
{
    @Autowired
    private AuthSessionProperties sessionProperties;

    @Autowired
    private RedisService redisService;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain)
    {
        ServerHttpRequest request = exchange.getRequest();
        if (!sessionProperties.isCsrfEnabled()
                || !SecurityConstants.AUTH_SOURCE_COOKIE.equals(
                        request.getHeaders().getFirst(SecurityConstants.AUTH_SOURCE_HEADER))
                || isSafeMethod(request.getMethod()))
        {
            return chain.filter(exchange);
        }

        if (!isAllowedOrigin(request))
        {
            return reject(exchange, "CSRF 来源不受信任", "origin");
        }

        String headerToken = request.getHeaders().getFirst(SecurityConstants.CSRF_HEADER);
        String cookieToken = request.getCookies().getFirst(sessionProperties.getCsrfCookieName()) == null
                ? null
                : request.getCookies().getFirst(sessionProperties.getCsrfCookieName()).getValue();
        String jwt = bearerToken(request.getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER));
        String userKey = StringUtils.isEmpty(jwt) ? null : JwtUtils.getUserKey(jwt);
        Object cached = StringUtils.isEmpty(userKey) ? null
                : redisService.getCacheObject(CacheConstants.CSRF_TOKEN_KEY + userKey);
        String expected = cached == null ? null : cached.toString();

        if (!constantTimeEquals(expected, headerToken) || !constantTimeEquals(expected, cookieToken))
        {
            return reject(exchange, "CSRF 校验失败", "token");
        }
        incrementMetric("success");
        return chain.filter(exchange);
    }

    private boolean isSafeMethod(HttpMethod method)
    {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private boolean isAllowedOrigin(ServerHttpRequest request)
    {
        String origin = request.getHeaders().getOrigin();
        if (StringUtils.isEmpty(origin))
        {
            String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
            if (StringUtils.isNotEmpty(referer))
            {
                try
                {
                    URI uri = URI.create(referer);
                    origin = uri.getScheme() + "://" + uri.getAuthority();
                }
                catch (IllegalArgumentException ignored)
                {
                    return false;
                }
            }
        }
        if (StringUtils.isEmpty(origin))
        {
            return !sessionProperties.isRequireOrigin();
        }
        for (String allowed : sessionProperties.getAllowedOrigins())
        {
            if (origin.equalsIgnoreCase(allowed))
            {
                return true;
            }
        }
        return false;
    }

    private String bearerToken(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        return value.toLowerCase(Locale.ROOT).startsWith("bearer ") ? value.substring(7) : value;
    }

    private boolean constantTimeEquals(String expected, String actual)
    {
        if (expected == null || actual == null)
        {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message, String reason)
    {
        incrementMetric("reject_" + reason);
        exchange.getResponse().getHeaders().set(SecurityConstants.CSRF_REQUIRED_HEADER, "true");
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(),
                org.springframework.http.HttpStatus.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    private void incrementMetric(String result)
    {
        if (meterRegistry != null)
        {
            meterRegistry.counter("erp.gateway.csrf.requests", "result", result).increment();
        }
    }

    @Override
    public int getOrder()
    {
        return -190;
    }
}
