package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.BusinessFeatureGate;

@DisplayName("调拨审批发起关联窗口回调")
class InventoryUnifiedApprovalTransferStartLinkTest
{
    @Test
    @DisplayName("同轮次实例尚未关联时最终回调应重试而不使实例失效")
    void shouldRetryCallbackWhileStartInstanceIsBeingAttached()
    {
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferOrder transfer = transfer(null);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(transfer);
        InventoryUnifiedApprovalService service = service(orderMapper,
                mock(InvTransferStatusLogMapper.class));

        ApprovalBusinessCallbackResponse result = service.applyCallback(
                callback());

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getInvalidated()).isFalse();
        assertThat(result.getCode()).isEqualTo(
                "APPROVAL_START_LINK_PENDING");
        verify(orderMapper, never()).updateInvTransferOrder(any());
    }

    @Test
    @DisplayName("本地关联完成后重投回调正常应用审批结果")
    void shouldApplyRetriedCallbackAfterInstanceWasAttached()
    {
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferStatusLogMapper statusMapper = mock(
                InvTransferStatusLogMapper.class);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(transfer(9001L));
        when(orderMapper.updateInvTransferOrder(any())).thenReturn(1);

        ApprovalBusinessCallbackResponse result = service(orderMapper,
                statusMapper).applyCallback(callback());

        assertThat(result.getAccepted()).isTrue();
        assertThat(result.getInvalidated()).isFalse();
        ArgumentCaptor<InvTransferOrder> update = ArgumentCaptor
                .forClass(InvTransferOrder.class);
        verify(orderMapper).updateInvTransferOrder(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.APPROVED);
        assertThat(update.getValue().getLastApprovalEventKey())
                .isEqualTo("9001:APPROVE");
        verify(statusMapper).insertLog(any());
    }

    @Test
    @DisplayName("原生审批拒绝先释放当前轮库存预留再更新业务状态")
    void shouldReleaseCurrentReservationRoundWhenApprovalRejected()
    {
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferStatusLogMapper statusMapper = mock(
                InvTransferStatusLogMapper.class);
        InvTransferReservationService reservationService = mock(
                InvTransferReservationService.class);
        InvTransferRevisionService revisionService = mock(
                InvTransferRevisionService.class);
        InvTransferOrder transfer = transfer(9001L);
        when(orderMapper.selectInvTransferOrderByIdForUpdate(88L))
                .thenReturn(transfer);
        when(orderMapper.updateInvTransferOrder(any())).thenReturn(1);

        ApprovalBusinessCallbackResponse result = service(orderMapper,
                statusMapper, reservationService, revisionService).applyCallback(
                        callback("REJECT", "REJECTED"));

        assertThat(result.getAccepted()).isTrue();
        verify(reservationService).releaseAllRemaining(transfer, "审批人");
        verify(revisionService).recordApprovalOutcome(transfer,
                com.erp.inventory.constant.InvTransferRevisionStatuses.REJECTED,
                InvStatusConstants.REJECTED, "REJECT", null, 7L,
                "审批人", 9001L);
        ArgumentCaptor<InvTransferOrder> update = ArgumentCaptor
                .forClass(InvTransferOrder.class);
        verify(orderMapper).updateInvTransferOrder(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.REJECTED);
    }

    private static InventoryUnifiedApprovalService service(
            InvTransferOrderMapper orderMapper,
            InvTransferStatusLogMapper statusMapper)
    {
        return service(orderMapper, statusMapper,
                mock(InvTransferReservationService.class));
    }

    private static InventoryUnifiedApprovalService service(
            InvTransferOrderMapper orderMapper,
            InvTransferStatusLogMapper statusMapper,
            InvTransferReservationService reservationService)
    {
        return service(orderMapper, statusMapper, reservationService,
                mock(InvTransferRevisionService.class));
    }

    private static InventoryUnifiedApprovalService service(
            InvTransferOrderMapper orderMapper,
            InvTransferStatusLogMapper statusMapper,
            InvTransferReservationService reservationService,
            InvTransferRevisionService revisionService)
    {
        return new InventoryUnifiedApprovalService(
                mock(RemoteApprovalService.class),
                mock(BusinessFeatureGate.class),
                mock(InvStockCheckMapper.class),
                mock(InvStockCheckDetailMapper.class),
                mock(InvStockCheckAdjustmentService.class), orderMapper,
                statusMapper,
                reservationService,
                revisionService);
    }

    private static InvTransferOrder transfer(Long instanceId)
    {
        InvTransferOrder transfer = new InvTransferOrder();
        transfer.setTransferId(88L);
        transfer.setStatus(InvStatusConstants.SUBMITTED);
        transfer.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        transfer.setApprovalRound(1);
        transfer.setApprovalInstanceId(instanceId);
        return transfer;
    }

    private static ApprovalBusinessCallbackRequest callback()
    {
        return callback("APPROVE", "APPROVED");
    }

    private static ApprovalBusinessCallbackRequest callback(String action,
            String targetStatus)
    {
        ApprovalBusinessCallbackRequest request =
                new ApprovalBusinessCallbackRequest();
        request.setEventKey("9001:" + action);
        request.setInstanceId(9001L);
        request.setBusinessCode(InventoryUnifiedApprovalService.TRANSFER);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setAction(action);
        request.setPayload("{\"targetStatus\":\"" + targetStatus + "\","
                + "\"operatorId\":7,\"operatorName\":\"审批人\"}");
        return request;
    }
}
