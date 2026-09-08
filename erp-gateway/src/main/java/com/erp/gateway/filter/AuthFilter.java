package com.erp.gateway.filter;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.erp.common.core.constant.CacheConstants;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.constant.TokenConstants;
import com.erp.common.core.security.BrowserSessionSecurity;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.core.utils.ServletUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.redis.service.RedisService;
import com.erp.gateway.config.properties.BrowserSessionProperties;
import com.erp.gateway.config.properties.IgnoreWhiteProperties;
import com.erp.gateway.config.properties.AuthSessionProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * 网关鉴权
 *
 * @author erp
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered
{
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final Set<HttpMethod> SAFE_METHODS = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private static final Set<String> BROWSER_SESSION_PATHS = Set.of(
            "/auth/browser/login", "/auth/browser/session");

    // 排除过滤的 uri 地址，nacos自行添加
    @Autowired
    private IgnoreWhiteProperties ignoreWhite;

    @Autowired
    private RedisService redisService;

    @Autowired
    private AuthSessionProperties sessionProperties;

    @Autowired(required = false)
    private BrowserSessionProperties browserSessionProperties;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpRequest.Builder mutate = request.mutate();
        stripUntrustedInternalHeaders(mutate);

        String url = request.getURI().getPath();
        if (BROWSER_SESSION_PATHS.contains(url))
        {
            exchange.getResponse().getHeaders().set(
                    HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
            exchange.getResponse().getHeaders().set(HttpHeaders.PRAGMA, "no-cache");
        }
        if (ignoreWhite.isWhitelisted(url))
        {
            return chain.filter(exchange.mutate().request(mutate.build()).build());
        }
        CredentialSelection credentials = selectCredentials(request);
        if (credentials.error != null)
        {
            incrementAuthMetric(credentials.metricSource, credentials.error);
            return unauthorizedResponse(exchange, credentials.error);
        }
        String token = credentials.token;
        if (StringUtils.isEmpty(token))
        {
            incrementAuthMetric("none", "missing");
            return unauthorizedResponse(exchange, "令牌不能为空");
        }
        Claims claims;
        try
        {
            claims = JwtUtils.parseToken(token);
        }
        catch (JwtException e)
        {
            incrementAuthMetric(credentials.source, "invalid_jwt");
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }
        if (claims == null)
        {
            incrementAuthMetric(credentials.source, "invalid_jwt");
            return unauthorizedResponse(exchange, "令牌已过期或验证不正确！");
        }

        String userkey = JwtUtils.getUserKey(claims);
        boolean islogin = redisService.hasKey(getTokenKey(userkey));
        if (!islogin)
        {
            incrementAuthMetric(credentials.source, "expired");
            return unauthorizedResponse(exchange, "登录状态已过期");
        }
        String userid = JwtUtils.getUserId(claims);
        String username = JwtUtils.getUserName(claims);
        if (StringUtils.isEmpty(userid) || StringUtils.isEmpty(username))
        {
            incrementAuthMetric(credentials.source, "invalid_claims");
            return unauthorizedResponse(exchange, "令牌验证失败");
        }

        if (credentials.browserSession && !SAFE_METHODS.contains(request.getMethod()))
        {
            String csrfSecret;
            try
            {
                csrfSecret = browserSessionProperties.requireCsrfSecret();
            }
            catch (RuntimeException ex)
            {
                log.error("ERP-NEW_2 浏览器会话安全配置不可用");
                return forbiddenResponse(exchange, "浏览器会话安全配置不可用");
            }
            String csrfToken = request.getHeaders().getFirst(BrowserSessionSecurity.CSRF_HEADER_NAME);
            if (!BrowserSessionSecurity.matches(token, csrfToken, csrfSecret))
            {
                return forbiddenResponse(exchange, "CSRF 令牌缺失或不匹配");
            }
        }

        // 设置用户信息到请求
        setHeader(mutate, SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + token);
        setHeader(mutate, SecurityConstants.AUTH_SOURCE_HEADER, credentials.source);
        addHeader(mutate, SecurityConstants.USER_KEY, userkey);
        addHeader(mutate, SecurityConstants.DETAILS_USER_ID, userid);
        addHeader(mutate, SecurityConstants.DETAILS_USERNAME, username);
        incrementAuthMetric(credentials.source, "success");
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

    private void stripUntrustedInternalHeaders(ServerHttpRequest.Builder mutate)
    {
        removeHeader(mutate, SecurityConstants.AUTH_SOURCE_HEADER);
        removeHeader(mutate, SecurityConstants.USER_KEY);
        removeHeader(mutate, SecurityConstants.DETAILS_USER_ID);
        removeHeader(mutate, SecurityConstants.DETAILS_USERNAME);
        removeHeader(mutate, SecurityConstants.FROM_SOURCE);
    }

    private CredentialSelection selectCredentials(ServerHttpRequest request)
    {
        boolean headerSupplied = request.getHeaders().containsHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (request.getHeaders().getOrEmpty(SecurityConstants.AUTHORIZATION_HEADER).size() > 1)
        {
            return CredentialSelection.error("header", "Authorization 头格式不正确");
        }
        String headerToken = getHeaderToken(request);

        // 客户端只要显式提供 Authorization，就必须完整验证，不能用好 Cookie
        // 为缺失、截断或错误的 Header 兜底。
        if (headerSupplied && StringUtils.isEmpty(headerToken))
        {
            return CredentialSelection.error("header", "Authorization 头格式不正确");
        }

        if (sessionProperties == null)
        {
            if (StringUtils.isNotEmpty(headerToken))
            {
                return CredentialSelection.success(headerToken, SecurityConstants.AUTH_SOURCE_BEARER,
                        "header", false);
            }
            CredentialSelection browserCredentials = dedicatedBrowserCredentials(request);
            return browserCredentials == null
                    ? CredentialSelection.success(null, SecurityConstants.AUTH_SOURCE_BEARER, "header", false)
                    : browserCredentials;
        }

        String cookieToken = getCookieToken(request);
        if (sessionProperties.getMode() == AuthSessionProperties.Mode.BEARER)
        {
            if (StringUtils.isNotEmpty(headerToken))
            {
                return CredentialSelection.success(headerToken, SecurityConstants.AUTH_SOURCE_BEARER,
                        "header", false);
            }
            CredentialSelection browserCredentials = dedicatedBrowserCredentials(request);
            return browserCredentials == null
                    ? CredentialSelection.success(null, SecurityConstants.AUTH_SOURCE_BEARER, "header", false)
                    : browserCredentials;
        }

        if (sessionProperties.getMode() == AuthSessionProperties.Mode.DUAL)
        {
            if (StringUtils.isNotEmpty(headerToken) && StringUtils.isNotEmpty(cookieToken)
                    && !StringUtils.equals(headerToken, cookieToken))
            {
                return CredentialSelection.error("both", "双凭证不一致");
            }
            if (StringUtils.isNotEmpty(headerToken))
            {
                String metricSource = StringUtils.isNotEmpty(cookieToken) ? "both" : "header";
                return CredentialSelection.success(headerToken, SecurityConstants.AUTH_SOURCE_BEARER,
                        metricSource, false);
            }
            if (StringUtils.isNotEmpty(cookieToken))
            {
                return CredentialSelection.success(cookieToken, SecurityConstants.AUTH_SOURCE_COOKIE,
                        "cookie", false);
            }
            CredentialSelection browserCredentials = dedicatedBrowserCredentials(request);
            return browserCredentials == null
                    ? CredentialSelection.success(null, SecurityConstants.AUTH_SOURCE_COOKIE, "cookie", false)
                    : browserCredentials;
        }

        if (StringUtils.isNotEmpty(headerToken) && StringUtils.isNotEmpty(cookieToken)
                && !StringUtils.equals(headerToken, cookieToken))
        {
            return CredentialSelection.error("both", "双凭证不一致");
        }
        if (StringUtils.isNotEmpty(cookieToken))
        {
            return CredentialSelection.success(cookieToken, SecurityConstants.AUTH_SOURCE_COOKIE,
                    "cookie", false);
        }
        CredentialSelection browserCredentials = dedicatedBrowserCredentials(request);
        return browserCredentials == null
                ? CredentialSelection.success(null, SecurityConstants.AUTH_SOURCE_COOKIE, "cookie", false)
                : browserCredentials;
    }

    private CredentialSelection dedicatedBrowserCredentials(ServerHttpRequest request)
    {
        if (browserSessionProperties == null || !browserSessionProperties.isEnabled())
        {
            return null;
        }
        try
        {
            browserSessionProperties.requireCsrfSecret();
        }
        catch (RuntimeException ex)
        {
            log.error("ERP-NEW_2 浏览器会话安全配置不可用");
            return CredentialSelection.error("browser_cookie", "浏览器会话安全配置不可用");
        }
        HttpCookie cookie = request.getCookies().getFirst(BrowserSessionSecurity.COOKIE_NAME);
        if (cookie == null || StringUtils.isEmpty(cookie.getValue()))
        {
            return null;
        }
        // 专用浏览器 Cookie 已在本过滤器内完成 CSRF 验证，下游按 Bearer 处理。
        return CredentialSelection.success(cookie.getValue(), SecurityConstants.AUTH_SOURCE_BEARER,
                "browser_cookie", true);
    }

    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value)
    {
        if (value == null)
        {
            return;
        }
        String valueStr = value.toString();
        String valueEncode = ServletUtils.urlEncode(valueStr);
        mutate.headers(headers -> headers.set(name, valueEncode));
    }

    private void setHeader(ServerHttpRequest.Builder mutate, String name, String value)
    {
        mutate.headers(headers -> {
            headers.remove(name);
            headers.set(name, value);
        });
    }

    private void removeHeader(ServerHttpRequest.Builder mutate, String name)
    {
        mutate.headers(httpHeaders -> httpHeaders.remove(name)).build();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String msg)
    {
        log.error("[鉴权异常处理]请求路径:{},错误信息:{}", exchange.getRequest().getPath(), msg);
        if (sessionProperties != null && sessionProperties.acceptsCookie()
                && exchange.getRequest().getCookies().containsKey(sessionProperties.getCookieName()))
        {
            clearSessionCookies(exchange);
        }
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(),
                org.springframework.http.HttpStatus.UNAUTHORIZED, msg, HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String msg)
    {
        log.warn("[浏览器会话请求拒绝]请求路径:{},错误信息:{}", exchange.getRequest().getPath(), msg);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        return ServletUtils.webFluxResponseWriter(exchange.getResponse(),
                org.springframework.http.HttpStatus.FORBIDDEN, msg, HttpStatus.FORBIDDEN);
    }

    private void clearSessionCookies(ServerWebExchange exchange)
    {
        exchange.getResponse().addCookie(expiredCookie(sessionProperties.getCookieName(), true));
        exchange.getResponse().addCookie(expiredCookie(sessionProperties.getCsrfCookieName(), false));
    }

    private ResponseCookie expiredCookie(String name, boolean httpOnly)
    {
        return ResponseCookie.from(name, "")
                .httpOnly(httpOnly)
                .secure(sessionProperties.isSecure())
                .path(sessionProperties.getCookiePath())
                .sameSite(sessionProperties.getSameSite())
                .maxAge(0)
                .build();
    }

    private String getTokenKey(String token)
    {
        return CacheConstants.LOGIN_TOKEN_KEY + token;
    }

    /**
     * 获取请求token
     */
    private String getHeaderToken(ServerHttpRequest request)
    {
        String token = request.getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER);
        if (StringUtils.isNotEmpty(token))
        {
            if (token.startsWith(TokenConstants.PREFIX))
            {
                token = token.substring(TokenConstants.PREFIX.length());
            }
        }
        return token;
    }

    private String getCookieToken(ServerHttpRequest request)
    {
        if (!sessionProperties.acceptsCookie()
                || request.getCookies().getFirst(sessionProperties.getCookieName()) == null)
        {
            return null;
        }
        return request.getCookies().getFirst(sessionProperties.getCookieName()).getValue();
    }

    private void incrementAuthMetric(String source, String result)
    {
        if (meterRegistry != null)
        {
            meterRegistry.counter("erp.gateway.auth.requests", "source",
                    StringUtils.isEmpty(source) ? "unknown" : source, "result", result).increment();
        }
    }

    private static final class CredentialSelection
    {
        private final String token;

        private final String source;

        private final String metricSource;

        private final String error;

        private final boolean browserSession;

        private CredentialSelection(String token, String source, String metricSource, String error,
                boolean browserSession)
        {
            this.token = token;
            this.source = source;
            this.metricSource = metricSource;
            this.error = error;
            this.browserSession = browserSession;
        }

        private static CredentialSelection success(String token, String source, String metricSource,
                boolean browserSession)
        {
            return new CredentialSelection(token, source, metricSource, null, browserSession);
        }

        private static CredentialSelection error(String source, String error)
        {
            return new CredentialSelection(null, null, source, error, false);
        }
    }

    @Override
    public int getOrder()
    {
        return -200;
    }
}
