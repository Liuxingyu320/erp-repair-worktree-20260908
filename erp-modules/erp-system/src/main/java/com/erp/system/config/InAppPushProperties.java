package com.erp.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Only newly created in-app notifications are queued while this switch is enabled. */
@Component
@ConfigurationProperties(prefix = "erp.push.in-app")
public class InAppPushProperties
{
    private boolean enabled;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
