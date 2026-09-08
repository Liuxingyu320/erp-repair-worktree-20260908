package com.erp.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Web 会话迁移配置。生产默认 bearer，只有显式配置后才签发 Cookie。
 */
@Configuration
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

    private Duration maxAge = Duration.ofMinutes(720);

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

    public Duration getMaxAge()
    {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge)
    {
        this.maxAge = maxAge == null ? Duration.ofMinutes(720) : maxAge;
    }

    public boolean issuesCookies()
    {
        return mode != Mode.BEARER;
    }

    public boolean exposesBearerInBody()
    {
        return mode != Mode.COOKIE;
    }
}
