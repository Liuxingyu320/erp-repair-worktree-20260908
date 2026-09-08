package com.erp.auth.controller;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.erp.auth.config.BrowserSessionProperties;
import com.erp.auth.form.LoginBody;
import com.erp.auth.form.RegisterBody;
import com.erp.auth.form.UnLockBody;
import com.erp.auth.service.SysLoginService;
import com.erp.auth.service.AuthSessionCookieService;
import com.erp.common.core.domain.R;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.security.BrowserSessionSecurity;
import com.erp.common.core.utils.JwtUtils;
import com.erp.common.core.utils.StringUtils;
import com.erp.common.security.annotation.RequiresLogin;
import com.erp.common.security.auth.AuthUtil;
import com.erp.common.security.service.TokenService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.model.LoginUser;

/**
 * token 控制
 * 
 * @author erp
 */
@RestController
public class TokenController
{
    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysLoginService sysLoginService;

    @Autowired
    private AuthSessionCookieService authSessionCookieService;

    @Autowired
    private BrowserSessionProperties browserSessionProperties;

    @PostMapping("login")
    public R<?> login(@RequestBody LoginBody form, HttpServletRequest request, HttpServletResponse response)
    {
        // 用户登录
        LoginUser userInfo = sysLoginService.login(form.getUsername(), form.getPassword());
        // 获取登录token
        Map<String, Object> tokenPayload = tokenService.createToken(userInfo);
        boolean cookiePreferred = SecurityConstants.SESSION_PREFERENCE_COOKIE.equalsIgnoreCase(
                request.getHeader(SecurityConstants.SESSION_PREFERENCE_HEADER));
        authSessionCookieService.issueLoginCookies(tokenPayload, response, cookiePreferred);
        return R.ok(tokenPayload);
    }

    /**
     * Creates the ERP-NEW_2 browser session without exposing its access token
     * to JavaScript.
     */
    @PostMapping("browser/login")
    public R<Map<String, Object>> browserLogin(@RequestBody LoginBody form,
            HttpServletResponse response)
    {
        String csrfSecret = browserSessionProperties.requireCsrfSecret();
        LoginUser userInfo = sysLoginService.login(form.getUsername(), form.getPassword());
        Map<String, Object> tokenResult = tokenService.createToken(userInfo);
        String accessToken = requireAccessToken(tokenResult);
        long expiresInMinutes = requireExpiresInMinutes(tokenResult);

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(accessToken,
                Duration.ofMinutes(expiresInMinutes)).toString());
        preventCaching(response);

        Map<String, Object> browserSession = new LinkedHashMap<>();
        browserSession.put("csrfToken", BrowserSessionSecurity.csrfToken(accessToken, csrfSecret));
        browserSession.put("expiresInMinutes", expiresInMinutes);
        return R.ok(browserSession);
    }

    /** Returns a fresh CSRF proof for an already authenticated browser session. */
    @RequiresLogin
    @GetMapping("browser/session")
    public R<Map<String, Object>> browserSession(HttpServletRequest request,
            HttpServletResponse response)
    {
        String accessToken = requireRequestToken(request);
        String csrfSecret = browserSessionProperties.requireCsrfSecret();
        preventCaching(response);
        return R.ok(Map.of(
                "csrfToken", BrowserSessionSecurity.csrfToken(accessToken, csrfSecret)));
    }

    @RequiresLogin
    @DeleteMapping("logout")
    public R<?> logout(HttpServletRequest request, HttpServletResponse response)
    {
        String token = SecurityUtils.getToken(request);
        try
        {
            if (StringUtils.isNotEmpty(token))
            {
                String username = JwtUtils.getUserName(token);
                // 删除用户缓存记录
                AuthUtil.logoutByToken(token);
                // 记录用户退出日志
                sysLoginService.logout(username);
            }
        }
        finally
        {
            authSessionCookieService.clearCookies(token, response);
        }
        return R.ok();
    }

    /** Invalidates and clears only the ERP-NEW_2 browser session cookie. */
    @RequiresLogin
    @DeleteMapping("browser/session")
    public R<?> browserLogout(HttpServletRequest request, HttpServletResponse response)
    {
        browserSessionProperties.requireCsrfSecret();
        String token = requireRequestToken(request);
        String username = JwtUtils.getUserName(token);
        tokenService.delLoginUser(token);
        sysLoginService.logout(username);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(StringUtils.EMPTY, Duration.ZERO).toString());
        preventCaching(response);
        return R.ok();
    }

    @RequiresLogin
    @PostMapping("refresh")
    public R<?> refresh(HttpServletRequest request, HttpServletResponse response)
    {
        String token = SecurityUtils.getToken(request);
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser))
        {
            // 刷新令牌有效期
            tokenService.refreshToken(loginUser);
            authSessionCookieService.refreshCookies(token, response);
            return R.ok();
        }
        return R.ok();
    }

    /**
     * 受认证的 CSRF 恢复/轮换端点。响应只刷新非 HttpOnly CSRF Cookie，
     * 不向 JavaScript 返回会话凭证。
     */
    @RequiresLogin
    @GetMapping("csrf")
    public R<?> csrf(HttpServletRequest request, HttpServletResponse response)
    {
        authSessionCookieService.rotateCsrf(SecurityUtils.getToken(request), response);
        return R.ok();
    }

    @PostMapping("register")
    public R<?> register(@RequestBody RegisterBody registerBody)
    {
        // 用户注册
        sysLoginService.register(registerBody.getUsername(), registerBody.getPassword());
        return R.ok();
    }

    @GetMapping("passwordPolicy")
    public R<String> passwordPolicy()
    {
        return R.ok(sysLoginService.getPasswordPolicy());
    }

    /**
     * 解锁屏幕
     */
    @RequiresLogin
    @PostMapping("/unlockscreen")
    public R<?> unlockScreen(@RequestBody UnLockBody unLockBody)
    {
        sysLoginService.unlock(unLockBody.getPassword());
        return R.ok();
    }

    private String requireAccessToken(Map<String, Object> tokenResult)
    {
        Object value = tokenResult == null ? null : tokenResult.get("access_token");
        if (!(value instanceof String accessToken) || StringUtils.isEmpty(accessToken.trim()))
        {
            throw new IllegalStateException("认证服务没有生成浏览器会话令牌");
        }
        return accessToken;
    }

    private long requireExpiresInMinutes(Map<String, Object> tokenResult)
    {
        Object value = tokenResult == null ? null : tokenResult.get("expires_in");
        long minutes;
        if (value instanceof Number number)
        {
            minutes = number.longValue();
        }
        else
        {
            try
            {
                minutes = Long.parseLong(String.valueOf(value));
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalStateException("认证服务没有生成有效会话期限", ex);
            }
        }
        if (minutes <= 0)
        {
            throw new IllegalStateException("认证服务没有生成有效会话期限");
        }
        return minutes;
    }

    private String requireRequestToken(HttpServletRequest request)
    {
        String token = SecurityUtils.getToken(request);
        if (StringUtils.isEmpty(token))
        {
            throw new IllegalStateException("浏览器会话令牌不可用");
        }
        return token;
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge)
    {
        return ResponseCookie.from(BrowserSessionSecurity.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(browserSessionProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private void preventCaching(HttpServletResponse response)
    {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
