package com.erp.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class BusinessFeatureGateTest
{
    @Test
    void missingInvalidAndReadFailureAllFailClosedWithStableCodes()
    {
        ISysConfigService config = mock(ISysConfigService.class);
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.NOTICE_WORKFLOW))
                .isInstanceOf(ServiceException.class)
                .hasMessage("FEATURE_DISABLED: " + BusinessFeatureGate.NOTICE_WORKFLOW);

        when(config.selectConfigByKey(BusinessFeatureGate.NOTICE_WORKFLOW)).thenReturn("maybe");
        assertThat(gate.inspect(BusinessFeatureGate.NOTICE_WORKFLOW).isInvalid()).isTrue();
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.NOTICE_WORKFLOW))
                .hasMessage("FEATURE_DISABLED: " + BusinessFeatureGate.NOTICE_WORKFLOW);

        when(config.selectConfigByKey(BusinessFeatureGate.NOTICE_WORKFLOW))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.NOTICE_WORKFLOW))
                .hasMessage("FEATURE_CONFIG_UNAVAILABLE: " + BusinessFeatureGate.NOTICE_WORKFLOW);
    }

    @Test
    void capabilitiesUseStrictBooleansAndExposeEveryActiveKey()
    {
        ISysConfigService config = mock(ISysConfigService.class);
        when(config.selectConfigByKey(BusinessFeatureGate.HEALTH_CERTIFICATE)).thenReturn(" YES ");
        when(config.selectConfigByKey(BusinessFeatureGate.ATTENDANCE_V2)).thenReturn("true");
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.capabilities()).hasSize(7)
                .containsEntry("healthCertificate", true)
                .containsEntry("attendanceV2", true)
                .doesNotContainKey("team")
                .containsEntry("systemManagementUxV2", false)
                .containsEntry("noticeWorkflow", false)
                .containsEntry("customerServiceCard", false);
    }

    @Test
    void attendanceCapabilityDefaultsAndInvalidValuesFailClosed()
    {
        ISysConfigService config = mock(ISysConfigService.class);
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.capabilities()).containsEntry("attendanceV2", false);

        when(config.selectConfigByKey(BusinessFeatureGate.ATTENDANCE_V2))
                .thenReturn("enabled");
        assertThat(gate.capabilities()).containsEntry("attendanceV2", false);

        when(config.selectConfigByKey(BusinessFeatureGate.ATTENDANCE_V2))
                .thenThrow(new IllegalStateException("config unavailable"));
        assertThat(gate.capabilities()).containsEntry("attendanceV2", false);
    }
}
