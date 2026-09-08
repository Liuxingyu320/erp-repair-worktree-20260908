package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("V2调拨差异裁决异步完成独立写开关")
class
        InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGateTest
{
    @Test
    @DisplayName("任一条件关闭时失败且只有双条件开启才通过")
    void shouldRequireBothIndependentConditions()
    {
        assertThatThrownBy(() -> gate(false, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        assertThatThrownBy(() -> gate(true, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未就绪");
        assertThatCode(() -> gate(true, true).requireEnabled())
                .doesNotThrowAnyException();
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                    gate(boolean enabled, boolean ready)
    {
        return new
                InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate(
                        enabled, ready);
    }
}
