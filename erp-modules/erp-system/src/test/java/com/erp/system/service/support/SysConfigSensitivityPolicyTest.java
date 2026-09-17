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
    @Test
    void detailPreservesVersionAndTrustedMetadataWithoutMutatingSource() throws Exception
    {
        SysConfig config = new SysConfig();
        config.setConfigId(3L);
        config.setConfigName("密码最小长度");
        config.setConfigKey("sys.user.password.minLength");
        config.setConfigType("Y");
        config.setConfigValue("12");
        config.setVersion(7);
        config.setCreateTime(new java.util.Date(0));
        var detail = policy.toDetailVo(config);
        assertThat(detail.getVersion()).isEqualTo(7);
        assertThat(detail.getValueType()).isEqualTo("integer");
        assertThat(detail.getValidationRule()).isEqualTo("min=8;max=128");
        assertThat(detail.isSensitive()).isFalse();
        assertThat(detail.getConfigValue()).isEqualTo("12");
        assertThat(config.getValueType()).isNull();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SysConfig decoded = mapper.readValue(mapper.writeValueAsBytes(detail), SysConfig.class);
        assertThat(decoded.getVersion()).isEqualTo(7);
        assertThat(decoded.getCreateTime()).isEqualTo(config.getCreateTime());
    }

    @Test
    void explicitSensitiveMetadataAndUnknownBuiltInsCannotLeakValues()
    {
        SysConfig config = new SysConfig();
        config.setConfigKey("custom.integration");
        config.setConfigValue("never-expose");
        config.setConfigType("N");
        config.setSensitiveFlag("Y");
        assertThat(policy.toDetailVo(config).isSensitive()).isTrue();
        assertThat(policy.toListVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        assertThat(policy.toExportVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        config.setSensitiveFlag("N");
        config.setConfigType("Y");
        assertThat(policy.toDetailVo(config).isSensitive()).isTrue();
        assertThat(policy.toDetailVo(config).getConfigValue()).isEqualTo(SysConfigSensitivityPolicy.MASK);
        assertThat(config.getConfigValue()).isEqualTo("never-expose");
    }
}
