package com.erp.oa.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("签约场景规范")
class OaSignScenarioCodesTest
{
    @Test
    @DisplayName("任务和签约包场景统一兼容历史续签别名")
    void shouldNormalizeTaskAndPackageScenarios()
    {
        assertThat(OaSignScenarioCodes.normalizeTaskScenario(" renew ")).isEqualTo("RENEWAL");
        assertThat(OaSignScenarioCodes.normalizeTaskScenario("Renewal")).isEqualTo("RENEWAL");
        assertThat(OaSignScenarioCodes.normalizePackageScenario(" RENEW ")).isEqualTo("renewal");
        assertThat(OaSignScenarioCodes.normalizePackageScenario(" OnBoard ")).isEqualTo("onboard");
        assertThat(OaSignScenarioCodes.normalizeTaskScenario("   ")).isNull();
    }

    @Test
    @DisplayName("只允许五个系统场景")
    void shouldRecognizeOnlySupportedScenarios()
    {
        assertThat(new String[] { "ONBOARD", "RENEWAL", "TRANSFER", "REGULARIZE", "OFFBOARD" })
                .allSatisfy(value -> assertThat(OaSignScenarioCodes.isSupported(value)).isTrue());
        assertThat(OaSignScenarioCodes.isSupported("change")).isFalse();
        assertThat(OaSignScenarioCodes.isSupported("free-form")).isFalse();
        assertThat(OaSignScenarioCodes.isSupported(null)).isFalse();
    }
}
