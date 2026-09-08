package com.erp.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录后员工资料补全门禁配置。
 *
 * <p>默认开启；受控的本地验收环境可通过配置显式关闭。</p>
 */
@Component
@ConfigurationProperties(prefix = "system.profile-completion")
public class ProfileCompletionProperties
{
    private boolean gateEnabled = true;

    public boolean isGateEnabled()
    {
        return gateEnabled;
    }

    public void setGateEnabled(boolean gateEnabled)
    {
        this.gateEnabled = gateEnabled;
    }
}
