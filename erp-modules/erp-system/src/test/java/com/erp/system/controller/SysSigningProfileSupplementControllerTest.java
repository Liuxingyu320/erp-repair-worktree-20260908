package com.erp.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import com.erp.common.core.domain.R;
import com.erp.common.security.annotation.InnerAuth;
import com.erp.system.api.domain.ReviewedSignProfileSupplement;
import com.erp.system.api.domain.ReviewedSignProfileSupplementResult;
import com.erp.system.service.ISysSigningProfileSupplementService;

class SysSigningProfileSupplementControllerTest
{
    @Test
    void endpointIsInnerOnlyAndDelegatesTypedWhitelist() throws Exception
    {
        Method endpoint = SysSigningProfileSupplementController.class.getMethod("supplement",
                ReviewedSignProfileSupplement.class);
        assertThat(endpoint.getAnnotation(InnerAuth.class)).isNotNull();

        ISysSigningProfileSupplementService service = org.mockito.Mockito.mock(
                ISysSigningProfileSupplementService.class);
        ReviewedSignProfileSupplement request = new ReviewedSignProfileSupplement();
        request.setRequestId("oa-review-1");
        request.setEmployeeId(7L);
        request.setCurrentAddress("现住址");
        ReviewedSignProfileSupplementResult supplementResult =
                new ReviewedSignProfileSupplementResult(true, false, "before", "after", "currentAddress");
        when(service.supplement(request)).thenReturn(supplementResult);

        R<ReviewedSignProfileSupplementResult> result =
                new SysSigningProfileSupplementController(service).supplement(request);

        assertThat(result.getData()).isSameAs(supplementResult);
        verify(service).supplement(request);
    }
}
