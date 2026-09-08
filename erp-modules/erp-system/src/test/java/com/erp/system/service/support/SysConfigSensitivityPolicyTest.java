package com.erp.system.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.erp.system.domain.SysConfig;

class SysConfigSensitivityPolicyTest
{
    private final SysConfigSensitivityPolicy policy = new SysConfigSensitivityPolicy();

    @Test
    void listDetailAndExportNeverExposeSensitiveValue()
    {
        SysConfig config = new SysConfig();
        config.setConfigId(1L);
        config.setConfigKey("integration.private-key");
        config.setConfigValue("must-not-leak");

        assertThat(policy.toListVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        assertThat(policy.toDetailVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        assertThat(policy.toExportVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        assertThat(policy.toDetailVo(config).isSensitive()).isTrue();
        assertThat(policy.toDetailVo(config).isValueConfigured()).isTrue();
    }

    @Test
    void ordinaryNonSecretConfigurationRemainsReadableToAuthorizedManagers()
    {
        SysConfig config = new SysConfig();
        config.setConfigKey("feature.notice.enabled");
        config.setConfigValue("true");

        assertThat(policy.toDetailVo(config).getConfigValue()).isEqualTo("true");
        assertThat(policy.toDetailVo(config).isSensitive()).isFalse();
    }
}
