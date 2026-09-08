package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.system.api.RemoteFileService;
import com.erp.system.domain.vo.HrHealthCertificateCapabilityVo;
import com.erp.system.service.IHrHealthCertificateService;
import com.erp.system.service.impl.HrHealthCertificateFeatureService;

class HrHealthCertificateControllerTest
{
    @Test
    void capabilityDoesNotTouchCertificateOrFileServices()
    {
        IHrHealthCertificateService certificateService=mock(IHrHealthCertificateService.class);
        RemoteFileService fileService=mock(RemoteFileService.class);
        HrHealthCertificateFeatureService featureService=mock(HrHealthCertificateFeatureService.class);
        HrHealthCertificateCapabilityVo capability=new HrHealthCertificateCapabilityVo();
        capability.setReason("维护中");
        when(featureService.capability()).thenReturn(capability);
        HrHealthCertificateController controller=new HrHealthCertificateController(
                certificateService,fileService,featureService);

        var response=controller.capability();

        assertThat(response.get("data")).isSameAs(capability);
        verifyNoInteractions(certificateService,fileService);
    }
}
