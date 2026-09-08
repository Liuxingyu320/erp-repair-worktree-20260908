package com.erp.gateway.config.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 网关会话兼容配置。默认 bearer，防止未配置环境意外切换认证协议。
 */
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "auth.session")
public class AuthSessionProperties
{
    public enum Mode
    {
        BEARER,
        DUAL,
        COOKIE
    }

    private Mode mode = Mode.BEARER;

    private String cookieName = "ERP_SESSION";

    private String csrfCookieName = "XSRF-TOKEN";

    private String cookiePath = "/";

    private String sameSite = "Lax";

    private boolean secure = true;

    private boolean csrfEnabled = true;

    private boolean requireOrigin = false;

    private Duration maxAge = Duration.ofMinutes(720);

    private List<String> allowedOrigins = new ArrayList<>();

    public Mode getMode()
    {
        return mode;
    }

    public void setMode(Mode mode)
    {
        this.mode = mode == null ? Mode.BEARER : mode;
    }

    public String getCookieName()
    {
        return cookieName;
    }

    public void setCookieName(String cookieName)
    {
        this.cookieName = cookieName;
    }

    public String getCsrfCookieName()
    {
        return csrfCookieName;
    }

    public void setCsrfCookieName(String csrfCookieName)
    {
        this.csrfCookieName = csrfCookieName;
    }

    public String getCookiePath()
    {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath)
    {
        this.cookiePath = cookiePath;
    }

    public String getSameSite()
    {
        return sameSite;
    }

    public void setSameSite(String sameSite)
    {
        this.sameSite = sameSite;
    }

    public boolean isSecure()
    {
        return secure;
    }

    public void setSecure(boolean secure)
    {
        this.secure = secure;
    }

    public boolean isCsrfEnabled()
    {
        return csrfEnabled;
    }

    public void setCsrfEnabled(boolean csrfEnabled)
    {
        this.csrfEnabled = csrfEnabled;
    }

    public boolean isRequireOrigin()
    {
        return requireOrigin;
    }

    public void setRequireOrigin(boolean requireOrigin)
    {
        this.requireOrigin = requireOrigin;
    }

    public Duration getMaxAge()
    {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge)
    {
        this.maxAge = maxAge == null ? Duration.ofMinutes(720) : maxAge;
    }

    public List<String> getAllowedOrigins()
    {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins)
    {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
    }

    public boolean acceptsBearer()
    {
        return mode != Mode.COOKIE;
    }

    public boolean acceptsCookie()
    {
        return mode != Mode.BEARER;
    }
}
