package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.api.domain.ApprovalBusinessCallbackResponse;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockCheckDetailMapper;
import com.erp.inventory.mapper.InvStockCheckMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.BusinessFeatureGate;

@DisplayName("盘点审批发起关联窗口回调")
class InventoryUnifiedApprovalStockCheckStartLinkTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("相同盘点轮次构建稳定幂等请求快照")
    void shouldBuildStableIdempotentStartSnapshot()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("counter");
        InvStockCheckMapper mapper = mock(InvStockCheckMapper.class);
        InventoryUnifiedApprovalService service = service(mapper);
        InvDeptScopeMapper deptMapper = mock(InvDeptScopeMapper.class);
        when(deptMapper.selectDeptNameById(20L)).thenReturn("中心仓");
        service.deptScopeMapper = deptMapper;
        InvStockCheck check = check(7001L);
        check.setShopDeptId(20L);
        check.setWarehouseId(20L);
        check.setCheckNo("SC-88");
        InvStockCheckDetail detail = new InvStockCheckDetail();

        ApprovalStartRequest first = service.buildStockCheckStartRequest(
                check, List.of(detail), 2);
        ApprovalStartRequest replay = service.buildStockCheckStartRequest(
                check, List.of(detail), 2);

        assertThat(first.getIdempotencyKey())
                .isEqualTo("INV_STOCK_CHECK:88:2")
                .isEqualTo(replay.getIdempotencyKey());
        assertThat(first.getBusinessCode())
                .isEqualTo(InventoryUnifiedApprovalService.STOCK_CHECK);
        assertThat(first.getBusinessId()).isEqualTo("88");
        assertThat(first.getBusinessRound()).isEqualTo(2);
        assertThat(first.getPreviousInstanceId()).isEqualTo(7001L);
        assertThat(first.getRouteSnapshot())
                .containsEntry("checkId", "88")
                .containsEntry("desktopPath", "/inventory/stock-check/88")
                .containsEntry("mobilePath", "/inventory/stock-check/88");
    }

    @Test
    @DisplayName("同轮次实例尚未关联时最终回调要求重试")
    void shouldRetryCallbackWhileStartInstanceIsBeingAttached()
    {
        InvStockCheckMapper mapper = mock(InvStockCheckMapper.class);
        when(mapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(check(null));

        ApprovalBusinessCallbackResponse result = service(mapper)
                .applyCallback(callback());

        assertThat(result.getAccepted()).isFalse();
        assertThat(result.getInvalidated()).isFalse();
        assertThat(result.getCode())
                .isEqualTo("APPROVAL_START_LINK_PENDING");
        verify(mapper, never()).updateInvStockCheck(any());
    }

    @Test
    @DisplayName("实例关联完成后重投回调正常应用")
    void shouldApplyRetriedCallbackAfterInstanceWasAttached()
    {
        InvStockCheckMapper mapper = mock(InvStockCheckMapper.class);
        when(mapper.selectInvStockCheckByIdForUpdate(88L))
                .thenReturn(check(9001L));
        when(mapper.updateInvStockCheck(any())).thenReturn(1);

        ApprovalBusinessCallbackResponse result = service(mapper)
                .applyCallback(callback());

        assertThat(result.getAccepted()).isTrue();
        ArgumentCaptor<InvStockCheck> update = ArgumentCaptor
                .forClass(InvStockCheck.class);
        verify(mapper).updateInvStockCheck(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(InvStatusConstants.REJECTED);
        assertThat(update.getValue().getLastApprovalEventKey())
                .isEqualTo("9001:REJECT");
    }

    private static InventoryUnifiedApprovalService service(
            InvStockCheckMapper mapper)
    {
        return new InventoryUnifiedApprovalService(
                mock(RemoteApprovalService.class),
                mock(BusinessFeatureGate.class), mapper,
                mock(InvStockCheckDetailMapper.class),
                mock(InvStockCheckAdjustmentService.class),
                mock(InvTransferOrderMapper.class),
                mock(InvTransferStatusLogMapper.class),
                mock(InvTransferReservationService.class),
                mock(InvTransferRevisionService.class));
    }

    private static InvStockCheck check(Long instanceId)
    {
        InvStockCheck check = new InvStockCheck();
        check.setCheckId(88L);
        check.setStatus(InvStatusConstants.PENDING_APPROVAL);
        check.setApprovalEngine(
                InventoryUnifiedApprovalService.ENGINE_NATIVE);
        check.setApprovalRound(1);
        check.setApprovalInstanceId(instanceId);
        return check;
    }

    private static ApprovalBusinessCallbackRequest callback()
    {
        ApprovalBusinessCallbackRequest request =
                new ApprovalBusinessCallbackRequest();
        request.setEventKey("9001:REJECT");
        request.setInstanceId(9001L);
        request.setBusinessCode(
                InventoryUnifiedApprovalService.STOCK_CHECK);
        request.setBusinessId("88");
        request.setBusinessRound(1);
        request.setAction("REJECT");
        request.setPayload("{\"targetStatus\":\"REJECTED\","
                + "\"operatorId\":7,\"operatorName\":\"审批人\"}");
        return request;
    }
}
