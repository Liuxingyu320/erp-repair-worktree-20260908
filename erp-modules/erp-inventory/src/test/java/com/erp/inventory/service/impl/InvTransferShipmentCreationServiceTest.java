package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.dto.InvTransferShipmentAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentCreateRequest;
import com.erp.inventory.domain.transfer.InvItemFulfillmentPolicy;
import com.erp.inventory.domain.transfer.InvLockedShipmentBalance;
import com.erp.inventory.domain.transfer.InvShipmentAllocationCandidate;
import com.erp.inventory.domain.transfer.InvStockLedgerDetailRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationRecord;
import com.erp.inventory.domain.transfer.InvTransferShipmentPlanComposer;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.domain.vo.InvTransferRevisionHistoryVo;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.domain.vo.InvTransferShipmentCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;
import com.erp.inventory.mapper.InvTransferShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.IInvTransferApprovalService;

@DisplayName("V2调拨发货写事务")
class InvTransferShipmentCreationServiceTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("默认关闭 Gate 在获取任何业务行锁前拒绝写入")
    void shouldRejectBeforeAnyBusinessLockWhenGateIsClosed()
    {
        InvTransferOrderMapper orderMapper =
                mock(InvTransferOrderMapper.class);
        InvTransferShipmentCreationService service = service(
                new InvTransferShipmentWriteGate(false, false), orderMapper);

        assertThatThrownBy(() -> service.create(
                "shipment-command-0001", 900L,
                new InvTransferShipmentCreateRequest(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");

        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("即使写开关开启也必须等待V2收货边界")
    void shouldRequireReceiptBoundaryBeforeAnyBusinessLock()
    {
        InvTransferOrderMapper orderMapper =
                mock(InvTransferOrderMapper.class);
        InvTransferShipmentCreationService service = service(
                new InvTransferShipmentWriteGate(true, false), orderMapper);

        assertThatThrownBy(() -> service.create(
                "shipment-command-0001", 900L,
                new InvTransferShipmentCreateRequest(), 301L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("收货事务尚未就绪");

        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("锁定规划完全一致时一次提交写入发货批次库存与审计闭环")
    void shouldPersistAuthoritativeShipmentTransaction()
    {
        SecurityContextHolder.setUserId("100");
        SecurityContextHolder.setUserName("warehouse-user");
        InvTransferOrderMapper orderMapper = mock(InvTransferOrderMapper.class);
        InvTransferRevisionMapper revisionMapper =
                mock(InvTransferRevisionMapper.class);
        InvTransferDetailMapper detailMapper =
                mock(InvTransferDetailMapper.class);
        InvTransferShipmentCreationMapper creationMapper =
                mock(InvTransferShipmentCreationMapper.class);
        InvTransferReservationService reservationService =
                mock(InvTransferReservationService.class);
        InvTransferRevisionService revisionService =
                mock(InvTransferRevisionService.class);
        IInvTransferApprovalService approvalService =
                mock(IInvTransferApprovalService.class);
        InvTransferShipmentMapper shipmentMapper =
                mock(InvTransferShipmentMapper.class);
        InvTransferShipmentDetailMapper shipmentDetailMapper =
                mock(InvTransferShipmentDetailMapper.class);
        InvStockLogMapper stockLogMapper = mock(InvStockLogMapper.class);
        InvTransferStatusLogMapper statusLogMapper =
                mock(InvTransferStatusLogMapper.class);
        InvDeptScopeMapper deptScopeMapper = mock(InvDeptScopeMapper.class);
        ShopScopeService shopScopeService = mock(ShopScopeService.class);
        InvTransferShipmentCreationService service =
                new InvTransferShipmentCreationService(
                        new InvTransferShipmentWriteGate(true, true),
                        orderMapper, revisionMapper, detailMapper,
                        creationMapper, reservationService, revisionService,
                        approvalService, shipmentMapper, shipmentDetailMapper,
                        stockLogMapper, statusLogMapper,
                        deptScopeMapper, shopScopeService);

        InvTransferOrder order = order();
        InvTransferDetail detail = detail();
        InvTransferRevision lockedRevision = lockedRevision();
        InvTransferRevisionVo revision = revision();
        InvWarehouseStockMode mode = mode();
        InvItemFulfillmentPolicy policy = policy();
        InvShipmentAllocationCandidate candidate = candidate();
        InvTransferShipmentPlanComposer.Composition composition =
                InvTransferShipmentPlanComposer.compose(order, revision,
                        mode, List.of(detail), List.of(policy),
                        List.of(candidate));
        InvTransferShipmentCreateRequest request = request(composition);
        InvLockedShipmentBalance balance = balance();
        InvTransferRevisionHistoryVo history = new InvTransferRevisionHistoryVo();
        history.setTransferId(900L);
        history.setCurrentRevisionNo(2);
        history.setCurrentRevisionStatus(InvTransferRevisionStatuses.APPROVED);
        history.setRevisions(List.of(revision));

        when(shopScopeService.resolveRequiredShopDept(301L)).thenReturn(301L);
        when(shopScopeService.hasUserShopScope(100L, 301L)).thenReturn(true);
        when(deptScopeMapper.countDeptInScope(301L, 301L)).thenReturn(1);
        when(deptScopeMapper.selectDeptTypeById(301L))
                .thenReturn("WAREHOUSE");
        when(orderMapper.selectInvTransferOrderByIdForUpdate(900L))
                .thenReturn(order);
        when(revisionMapper.selectLatestByTransferIdForUpdate(900L))
                .thenReturn(lockedRevision);
        when(revisionService.getHistory(order)).thenReturn(history);
        when(creationMapper.selectTransferDetailIdsForUpdate(900L))
                .thenReturn(List.of(11L));
        when(detailMapper.selectByTransferId(900L))
                .thenReturn(List.of(detail));
        when(creationMapper.selectWarehouseModeForUpdate(301L))
                .thenReturn(mode);
        when(creationMapper.selectPoliciesForUpdate(any()))
                .thenReturn(List.of(policy));
        InvTransferReservationService.LockedShipmentReservations locked =
                new InvTransferReservationService.LockedShipmentReservations(
                        List.of(), Map.of(), Map.of());
        when(reservationService.lockForShipment(any(), any(), any()))
                .thenReturn(locked);
        when(creationMapper.selectBalancesForUpdate(anyLong(), any()))
                .thenReturn(List.of(balance));
        when(creationMapper.selectLotsForUpdate(List.of(600L)))
                .thenReturn(List.of(600L));
        when(creationMapper.selectLocationsForUpdate(List.of(700L)))
                .thenReturn(List.of(700L));
        when(creationMapper.selectEligibleCandidateFacts(List.of(500L)))
                .thenReturn(List.of(candidate));
        when(creationMapper.selectSerialsForUpdate(List.of(500L)))
                .thenReturn(List.of());
        when(creationMapper.deductBalance(500L, 3L,
                new BigDecimal("5"), new BigDecimal("10.000000"),
                "warehouse-user")).thenReturn(1);
        when(reservationService.consumeLockedShipment(locked,
                Map.of(11L, new BigDecimal("10.000000")),
                "warehouse-user")).thenReturn(
                        new InvTransferReservationService.ShipmentConsumption(
                                Map.of(11L,
                                        new InvTransferReservationService.StockConsumption(
                                                1001L,
                                                new BigDecimal("20"),
                                                new BigDecimal("15"),
                                                new BigDecimal("2"),
                                                new BigDecimal("10")))));
        doAnswer(invocation -> {
            ((InvTransferShipment) invocation.getArgument(0))
                    .setShipmentId(800L);
            return 1;
        }).when(shipmentMapper).insertShipment(any());
        doAnswer(invocation -> {
            ((InvTransferShipmentDetail) invocation.getArgument(0))
                    .setShipmentDetailId(810L);
            return 1;
        }).when(shipmentDetailMapper).insertShipmentDetail(any());
        doAnswer(invocation -> {
            ((InvStockLog) invocation.getArgument(0)).setLogId(820L);
            return 1;
        }).when(stockLogMapper).insertInvStockLog(any());
        doAnswer(invocation -> {
            ((InvTransferShipmentAllocationRecord) invocation.getArgument(0))
                    .setAllocationId(830L);
            return 1;
        }).when(creationMapper).insertAllocation(any());
        doAnswer(invocation -> {
            ((InvStockLedgerDetailRecord) invocation.getArgument(0))
                    .setLedgerId(840L);
            return 1;
        }).when(creationMapper).insertStockLedger(any());
        when(detailMapper.updateDeliveredQuantity(detail)).thenReturn(1);
        when(orderMapper.updateInvTransferOrder(any())).thenReturn(1);
        when(statusLogMapper.insertLog(any())).thenReturn(1);

        InvTransferShipmentCreationVo result = service.create(
                "shipment-command-0001", 900L, request, 301L);

        assertThat(result.shipmentId()).isEqualTo("800");
        assertThat(result.transferId()).isEqualTo("900");
        assertThat(result.status()).isEqualTo(
                InvStatusConstants.PENDING_RECEIVE);
        assertThat(result.planVersion()).isEqualTo(composition.planVersion());
        assertThat(detail.getDeliveredQuantity()).isEqualByComparingTo("5");
        verify(creationMapper).deductBalance(500L, 3L,
                new BigDecimal("5"), new BigDecimal("10.000000"),
                "warehouse-user");
        verify(creationMapper).insertAllocation(any());
        verify(creationMapper).insertStockLedger(any());
    }

    private static InvTransferShipmentCreationService service(
            InvTransferShipmentWriteGate gate,
            InvTransferOrderMapper orderMapper)
    {
        return new InvTransferShipmentCreationService(gate, orderMapper,
                mock(InvTransferRevisionMapper.class),
                mock(InvTransferDetailMapper.class),
                mock(InvTransferShipmentCreationMapper.class),
                mock(InvTransferReservationService.class),
                mock(InvTransferRevisionService.class),
                mock(IInvTransferApprovalService.class),
                mock(InvTransferShipmentMapper.class),
                mock(InvTransferShipmentDetailMapper.class),
                mock(InvStockLogMapper.class),
                mock(InvTransferStatusLogMapper.class),
                mock(InvDeptScopeMapper.class),
                mock(ShopScopeService.class));
    }

    private static InvTransferOrder order()
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(900L);
        value.setOrderNo("TR-900");
        value.setVersion(2L);
        value.setStatus(InvStatusConstants.APPROVED);
        value.setTransferType(InvTransferTypes.WAREHOUSE);
        value.setFromDeptId(201L);
        value.setFromWarehouseId(301L);
        value.setToDeptId(202L);
        value.setToDeptName("目标门店");
        value.setApprovalRound(1);
        return value;
    }

    private static InvTransferDetail detail()
    {
        InvTransferDetail value = new InvTransferDetail();
        value.setDetailId(11L);
        value.setTransferId(900L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setProductId(1001L);
        value.setItemCode("P-1001");
        value.setItemName("测试物料");
        value.setQuantity(new BigDecimal("5"));
        value.setDeliveredQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferRevision lockedRevision()
    {
        InvTransferRevision value = new InvTransferRevision();
        value.setRevisionId(700L);
        value.setTransferId(900L);
        value.setRevisionNo(2);
        value.setStatus(InvTransferRevisionStatuses.APPROVED);
        value.setSnapshotHash("a".repeat(64));
        return value;
    }

    private static InvTransferRevisionVo revision()
    {
        InvTransferRevisionVo value = new InvTransferRevisionVo();
        value.setRevisionId(700L);
        value.setRevisionNo(2);
        value.setStatus(InvTransferRevisionStatuses.APPROVED);
        value.setSnapshotHash("a".repeat(64));
        return value;
    }

    private static InvWarehouseStockMode mode()
    {
        InvWarehouseStockMode value = new InvWarehouseStockMode();
        value.setWarehouseId(301L);
        value.setWriteMode("dual");
        value.setReadMode("detail");
        value.setReconcileStatus("passed");
        value.setLastReconcileBatch("reconcile-01");
        return value;
    }

    private static InvItemFulfillmentPolicy policy()
    {
        InvItemFulfillmentPolicy value = new InvItemFulfillmentPolicy();
        value.setPolicyId(1L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setAllocationPolicy("FIFO");
        value.setTrackingPolicy("lot");
        value.setStatus("0");
        value.setVersion(1L);
        return value;
    }

    private static InvShipmentAllocationCandidate candidate()
    {
        InvShipmentAllocationCandidate value =
                new InvShipmentAllocationCandidate();
        value.setBalanceId(500L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setWarehouseId(301L);
        value.setLotId(600L);
        value.setLotNo("LOT-600");
        value.setReceivedAt(new Date(1_700_000_000_000L));
        value.setLocationId(700L);
        value.setLocationCode("A-01");
        value.setAvailableQuantity(new BigDecimal("5"));
        value.setVersion(3L);
        return value;
    }

    private static InvLockedShipmentBalance balance()
    {
        InvLockedShipmentBalance value = new InvLockedShipmentBalance();
        value.setBalanceId(500L);
        value.setItemType("product");
        value.setItemId(1001L);
        value.setProductId(1001L);
        value.setWarehouseId(301L);
        value.setLotId(600L);
        value.setLocationId(700L);
        value.setCurrentQuantity(new BigDecimal("5"));
        value.setAvailableQuantity(new BigDecimal("5"));
        value.setLockedQuantity(BigDecimal.ZERO);
        value.setCostPrice(new BigDecimal("2"));
        value.setTotalCost(new BigDecimal("10"));
        value.setVersion(3L);
        return value;
    }

    private static InvTransferShipmentCreateRequest request(
            InvTransferShipmentPlanComposer.Composition composition)
    {
        InvTransferShipmentAllocationRequest allocation =
                new InvTransferShipmentAllocationRequest();
        allocation.setTransferDetailId(11L);
        allocation.setBalanceId(500L);
        allocation.setLotId(600L);
        allocation.setLocationId(700L);
        allocation.setQuantity(new BigDecimal("5"));
        allocation.setSerialIds(List.of());
        InvTransferShipmentCreateRequest value =
                new InvTransferShipmentCreateRequest();
        value.setPlanVersion(composition.planVersion());
        value.setSealedRevisionId(700L);
        value.setBasis("server-recommendation");
        value.setAllocations(List.of(allocation));
        return value;
    }
}
