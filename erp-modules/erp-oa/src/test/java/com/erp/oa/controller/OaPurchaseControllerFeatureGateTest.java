package com.erp.oa.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.oa.service.IOaPurchaseService;

class OaPurchaseControllerFeatureGateTest
{
    private IOaPurchaseService purchaseService;
    private BusinessFeatureGate featureGate;
    private OaPurchaseController controller;

    @BeforeEach
    void setUp()
    {
        purchaseService = mock(IOaPurchaseService.class);
        featureGate = mock(BusinessFeatureGate.class);
        controller = new OaPurchaseController(purchaseService, featureGate);
        doThrow(new ServiceException("FEATURE_DISABLED: " + BusinessFeatureGate.OA_PURCHASE))
                .when(featureGate).requireEnabled(BusinessFeatureGate.OA_PURCHASE);
    }

    @Test
    void allPurchaseWriteEntrypointsFailClosedBeforeBusinessService()
    {
        assertDisabled(() -> controller.save(new OaPurchase(), null));
        assertDisabled(() -> controller.submit(new OaPurchase(), null));
        assertDisabled(() -> controller.close(1L, null));

        verifyNoInteractions(purchaseService);
    }

    private void assertDisabled(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable)
    {
        assertThatThrownBy(callable)
                .isInstanceOf(ServiceException.class)
                .hasMessage("FEATURE_DISABLED: " + BusinessFeatureGate.OA_PURCHASE);
    }
}
