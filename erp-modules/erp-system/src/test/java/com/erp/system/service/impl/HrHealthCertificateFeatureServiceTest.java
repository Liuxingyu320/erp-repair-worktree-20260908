package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.service.BusinessFeatureGate;
import com.erp.system.service.ISysConfigService;

class HrHealthCertificateFeatureServiceTest
{
    @Test
    void missingAndInvalidValuesFailClosed()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        HrHealthCertificateFeatureService service=new HrHealthCertificateFeatureService(new BusinessFeatureGate(config));

        assertThat(service.capability().isIntakeEnabled()).isFalse();
        assertThat(service.capability().isSchemaRequired()).isTrue();
        when(config.selectConfigByKey(HrHealthCertificateFeatureService.FEATURE_KEY)).thenReturn("maybe");
        assertThat(service.capability().isIntakeEnabled()).isFalse();
        assertThat(service.capability().getReason()).contains("无效");
        assertThatThrownBy(service::requireIntakeEnabled).isInstanceOf(ServiceException.class)
                .hasMessageContaining(BusinessFeatureGate.FEATURE_DISABLED)
                .hasMessageContaining(HrHealthCertificateFeatureService.FEATURE_KEY);
    }

    @Test
    void acceptedTrueAndFalseValuesAreParsedStrictly()
    {
        ISysConfigService config=mock(ISysConfigService.class);
        HrHealthCertificateFeatureService service=new HrHealthCertificateFeatureService(new BusinessFeatureGate(config));
        when(config.selectConfigByKey(HrHealthCertificateFeatureService.FEATURE_KEY)).thenReturn(" YES ");
        assertThat(service.capability().isIntakeEnabled()).isTrue();
        when(config.selectConfigByKey(HrHealthCertificateFeatureService.FEATURE_KEY)).thenReturn("off");
        assertThat(service.capability().isIntakeEnabled()).isFalse();
    }
}
