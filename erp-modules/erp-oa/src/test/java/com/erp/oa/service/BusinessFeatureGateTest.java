package com.erp.oa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteConfigService;

class BusinessFeatureGateTest
{
    @Test
    void disabledAndUnavailableRemoteConfigurationFailClosed()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        when(config.getConfigKey(BusinessFeatureGate.REIMBURSEMENT,
                SecurityConstants.INNER)).thenReturn(R.ok("false"));
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.isEnabled(BusinessFeatureGate.REIMBURSEMENT)).isFalse();
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.REIMBURSEMENT))
                .isInstanceOf(ServiceException.class)
                .hasMessage("FEATURE_DISABLED: " + BusinessFeatureGate.REIMBURSEMENT);

        when(config.getConfigKey(BusinessFeatureGate.REIMBURSEMENT,
                SecurityConstants.INNER)).thenReturn(R.fail("system unavailable"));
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.REIMBURSEMENT))
                .hasMessage("FEATURE_CONFIG_UNAVAILABLE: " + BusinessFeatureGate.REIMBURSEMENT);
    }

    @Test
    void explicitTrueEnablesNewEntry()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        when(config.getConfigKey(BusinessFeatureGate.REIMBURSEMENT,
                SecurityConstants.INNER)).thenReturn(R.ok(" on "));
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.isEnabled(BusinessFeatureGate.REIMBURSEMENT)).isTrue();
        gate.requireEnabled(BusinessFeatureGate.REIMBURSEMENT);
    }
}
