package com.erp.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import com.erp.common.core.security.BrowserSessionSecurity;

/** ERP-NEW_2 browser-session settings. Disabled unless explicitly configured. */
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "security.browser-session")
public class BrowserSessionProperties
{
    private boolean enabled;

    private String csrfSecret;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
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
            throw new IllegalStateException("ERP-NEW_2 浏览器会话未启用");
        }
        BrowserSessionSecurity.validateSecret(csrfSecret);
        return csrfSecret;
    }
}
