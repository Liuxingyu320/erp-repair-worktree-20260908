package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("V2差异退回发货独立写开关")
class InvTransferReceiptDiscrepancyReturnShipmentWriteGateTest
{
    @Test
    @DisplayName("默认关闭和收货未就绪均失败关闭")
    void shouldFailClosedUntilBothFlagsAreReady()
    {
        assertThatThrownBy(() ->
                new InvTransferReceiptDiscrepancyReturnShipmentWriteGate(
                        false, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        assertThatThrownBy(() ->
                new InvTransferReceiptDiscrepancyReturnShipmentWriteGate(
                        true, false).requireEnabled())
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货边界尚未就绪");
        assertThatCode(() ->
                new InvTransferReceiptDiscrepancyReturnShipmentWriteGate(
                        true, true).requireEnabled()).doesNotThrowAnyException();
    }
}
