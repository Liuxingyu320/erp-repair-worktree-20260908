package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;
import com.erp.inventory.service.IInvTransferApprovalService;
import com.erp.inventory.service.IInvTransferService;

@DisplayName("V2调拨收货命令前置 Gate")
class InvTransferCommandReceiptV2GateTest
{
    @Test
    @DisplayName("默认关闭时不进入命令台账执行器")
    void shouldRejectBeforePersistentCommandExecutor()
    {
        InvTransferCommandExecutor executor =
                mock(InvTransferCommandExecutor.class);
        InvTransferCommandService service = new InvTransferCommandService(
                executor, mock(IInvTransferService.class),
                mock(IInvTransferApprovalService.class),
                mock(InvTransferShipmentCreationService.class),
                new InvTransferShipmentReceiptWriteGate(false, false),
                mock(InvTransferShipmentReceiptCreationService.class));

        assertThatThrownBy(() -> service.createReceiptV2("receipt-1", 91L,
                new InvTransferShipmentReceiptCreateRequest(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");

        verifyNoInteractions(executor);
    }
}
