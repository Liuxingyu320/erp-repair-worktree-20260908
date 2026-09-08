package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("V2差异退回专属收货独立写开关")
class InvTransferReceiptDiscrepancyReturnReceiptWriteGateTest
{
    @Test
    @DisplayName("默认关闭且必须同时确认生命周期边界")
    void shouldFailClosedUntilBothFlagsAreReady()
    {
        assertThatThrownBy(() ->
                new InvTransferReceiptDiscrepancyReturnReceiptWriteGate(
                        false, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        assertThatThrownBy(() ->
                new InvTransferReceiptDiscrepancyReturnReceiptWriteGate(
                        true, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("生命周期边界尚未就绪");
        assertThatCode(() ->
                new InvTransferReceiptDiscrepancyReturnReceiptWriteGate(
                        true, true).requireEnabled()).doesNotThrowAnyException();
    }
}
