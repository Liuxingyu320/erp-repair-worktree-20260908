package com.erp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.security.BrowserSessionSecurity;

/** ERP-NEW_2 browser-session settings. Disabled unless explicitly configured. */
@Configuration
@ConfigurationProperties(prefix = "security.browser-session")
public class BrowserSessionProperties
{
    private boolean enabled;

    private boolean cookieSecure = true;

    private String csrfSecret;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isCookieSecure()
    {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure)
    {
        this.cookieSecure = cookieSecure;
    }

    public String getCsrfSecret()
    {
        return csrfSecret;
    }

    public void setCsrfSecret(String csrfSecret)
    {
        this.csrfSecret = csrfSecret;
    }

    public String requireCsrfSecret()
    {
        if (!enabled)
        {
            throw new ServiceException("ERP_NEW_2_BROWSER_SESSION_DISABLED");
        }
        BrowserSessionSecurity.validateSecret(csrfSecret);
        return csrfSecret;
    }
}
