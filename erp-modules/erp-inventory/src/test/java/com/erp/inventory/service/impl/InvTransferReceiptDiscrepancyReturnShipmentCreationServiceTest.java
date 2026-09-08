package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.InvTransferShipmentDetail;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnShipmentCreateRequest;
import com.erp.inventory.domain.transfer.InvStockLedgerDetailRecord;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecycleFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnShipmentPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationRecord;
import com.erp.inventory.domain.transfer.InvWarehouseStockMode;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferRevisionMapper;
import com.erp.inventory.mapper.InvTransferShipmentCreationMapper;
import com.erp.inventory.mapper.InvTransferShipmentDetailMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;

@DisplayName("V2差异退回固定隔离来源发货原子创建")
class InvTransferReceiptDiscrepancyReturnShipmentCreationServiceTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("默认关闭时在任何业务行锁前拒绝创建")
    void shouldRejectBeforeBusinessLocksWhenGateIsClosed()
    {
        Fixture fixture = fixture(false, false);
        assertThatThrownBy(() -> fixture.service().create(
                "return-shipment-0001", 2101L, request(fixture.plan()),
                202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未启用");
        verifyNoInteractions(fixture.orderMapper());
        verifyNoInteractions(fixture.lifecycleService());
        verifyNoInteractions(fixture.shipmentMapper());
    }

    @Test
    @DisplayName("固定锁序后写入V2发货消费审计和条件进度闭环")
    void shouldPersistAtomicReturnShipmentInFixedOrder()
    {
        Fixture fixture = fixture(true, true);
        stubSuccess(fixture);
        ArgumentCaptor<InvTransferShipment> shipment =
                ArgumentCaptor.forClass(InvTransferShipment.class);
        ArgumentCaptor<InvTransferShipmentAllocationRecord> allocation =
                ArgumentCaptor.forClass(
                        InvTransferShipmentAllocationRecord.class);
        ArgumentCaptor<InvStockLedgerDetailRecord> ledger =
                ArgumentCaptor.forClass(InvStockLedgerDetailRecord.class);

        var result = fixture.service().create("return-shipment-0001",
                2101L, request(fixture.plan()), 202L);

        assertThat(result.shipmentId()).isEqualTo("3001");
        assertThat(result.shipmentDetailId()).isEqualTo("3002");
        assertThat(result.transferId()).isEqualTo("2101");
        assertThat(result.transferStatus()).isEqualTo("delivered");
        assertThat(result.quantity()).isEqualTo("5");
        assertThat(result.amount()).isEqualTo("30");
        assertThat(result.planVersion()).isEqualTo(
                fixture.plan().planVersion());
        assertThat(result.reservationId()).isEqualTo("1001");
        assertThat(result.consumptionId()).isEqualTo("5001");
        assertThat(result.reservationStatus()).isEqualTo("CONSUMED");
        assertThat(result.reservationVersion()).isEqualTo("3");
        assertThat(result.sealedRevisionId()).isEqualTo("2601");
        assertThat(result.createdAt()).isEqualTo(NOW.toString());

        verify(fixture.shipmentMapper()).insertShipment(shipment.capture());
        assertThat(shipment.getValue().getInventoryWriteVersion())
                .isEqualTo(InvTransferShipmentWriteVersions.V2_DETAIL);
        assertThat(shipment.getValue().getCommandRequestId())
                .isEqualTo("return-shipment-0001");
        assertThat(shipment.getValue().getPlanVersion())
                .isEqualTo(fixture.plan().planVersion());
        assertThat(shipment.getValue().getSealedRevisionId())
                .isEqualTo(2601L);
        assertThat(shipment.getValue().getReconcileBatch())
                .isEqualTo("return-reconcile-01");

        verify(fixture.v2Mapper()).insertAllocation(allocation.capture());
        assertThat(allocation.getValue().getAllocationPolicy())
                .isEqualTo("FIXED_RETURN");
        assertThat(allocation.getValue().getBalanceId()).isEqualTo(2302L);
        assertThat(allocation.getValue().getBalanceVersionBefore())
                .isEqualTo(8L);
        assertThat(allocation.getValue().getBalanceVersionAfter())
                .isEqualTo(9L);
        verify(fixture.v2Mapper()).insertStockLedger(ledger.capture());
        assertThat(ledger.getValue().getBusinessType())
                .isEqualTo("transfer_discrepancy_return_shipment_v2");
        assertThat(ledger.getValue().getChangeQuantity())
                .isEqualByComparingTo("-5.0000");

        InOrder order = inOrder(fixture.orderMapper(),
                fixture.revisionMapper(), fixture.detailMapper(),
                fixture.v2Mapper(), fixture.lifecycleService(),
                fixture.shipmentMapper(), fixture.shipmentDetailMapper(),
                fixture.stockLogMapper(), fixture.returnMapper(),
                fixture.statusLogMapper());
        order.verify(fixture.orderMapper())
                .selectInvTransferOrderByIdForUpdate(2101L);
        order.verify(fixture.revisionMapper())
                .selectLatestByTransferIdForUpdate(2101L);
        order.verify(fixture.detailMapper())
                .selectByTransferIdForUpdate(2101L);
        order.verify(fixture.v2Mapper())
                .selectWarehouseModeForUpdate(202L);
        order.verify(fixture.lifecycleService()).lockForConsumption(2101L,
                fixture.plan().planVersion(), NOW);
        order.verify(fixture.lifecycleService()).planLockedConsumption(
                new BigDecimal("5.0000"), fixture.locked());
        order.verify(fixture.shipmentMapper()).insertShipment(any());
        order.verify(fixture.shipmentDetailMapper())
                .insertShipmentDetail(any());
        order.verify(fixture.lifecycleService()).consumeLocked(
                anyString(), anyLong(), anyLong(), any(), anyString(),
                any(), any());
        order.verify(fixture.stockLogMapper()).insertInvStockLog(any());
        order.verify(fixture.v2Mapper()).insertAllocation(any());
        order.verify(fixture.v2Mapper()).insertStockLedger(any());
        order.verify(fixture.returnMapper()).advanceChildDetail(any());
        order.verify(fixture.returnMapper()).advanceChildOrder(any());
        order.verify(fixture.statusLogMapper()).insertLog(any());
    }

    @Test
    @DisplayName("计划漂移在首个DML前停止且条件推进冲突拒绝后续状态写入")
    void shouldFailClosedOnPlanOrConditionalProgressConflict()
    {
        Fixture stale = fixture(true, true);
        stubLocks(stale);
        when(stale.lifecycleService().lockForConsumption(2101L,
                stale.plan().planVersion(), NOW))
                .thenThrow(new ServiceException("退回发货规划已变化"));
        assertThatThrownBy(() -> stale.service().create(
                "return-shipment-0001", 2101L, request(stale.plan()),
                202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划已变化");
        verifyNoInteractions(stale.shipmentMapper());
        verifyNoInteractions(stale.shipmentDetailMapper());
        verify(stale.lifecycleService(), never()).consumeLocked(anyString(),
                anyLong(), anyLong(), any(), anyString(), any(), any());

        Fixture conflict = fixture(true, true);
        stubSuccess(conflict);
        when(conflict.returnMapper().advanceChildDetail(any()))
                .thenReturn(0);
        assertThatThrownBy(() -> conflict.service().create(
                "return-shipment-0001", 2101L,
                request(conflict.plan()), 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("累计发货数量推进冲突")
                .hasMessageContaining("已回滚");
        verify(conflict.returnMapper(), never()).advanceChildOrder(any());
        verifyNoInteractions(conflict.statusLogMapper());
    }

    @Test
    @DisplayName("创建强制根幂等事务且保持零生产调用点")
    void shouldRequireRootTransactionAndRemainUnwired() throws Exception
    {
        Method method =
                InvTransferReceiptDiscrepancyReturnShipmentCreationService
                        .class.getMethod("create", String.class, Long.class,
                                InvTransferReceiptDiscrepancyReturnShipmentCreateRequest
                                        .class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(List.of(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences(
                InvTransferReceiptDiscrepancyReturnShipmentCreationService
                        .class.getSimpleName()))
                .containsExactly(Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyReturnShipmentCreationService.java"));
    }

    private static Fixture fixture(boolean writeEnabled,
            boolean receiptReady)
    {
        var orderMapper = mock(InvTransferOrderMapper.class);
        var revisionMapper = mock(InvTransferRevisionMapper.class);
        var detailMapper = mock(InvTransferDetailMapper.class);
        var v2Mapper = mock(InvTransferShipmentCreationMapper.class);
        var returnMapper = mock(
                InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                        .class);
        var lifecycleService = mock(
                InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .class);
        var shipmentMapper = mock(InvTransferShipmentMapper.class);
        var shipmentDetailMapper = mock(
                InvTransferShipmentDetailMapper.class);
        var stockLogMapper = mock(InvStockLogMapper.class);
        var statusLogMapper = mock(InvTransferStatusLogMapper.class);
        var deptScopeMapper = mock(InvDeptScopeMapper.class);
        var shopScopeService = mock(ShopScopeService.class);
        var plan = InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                NOW, fact(), stock(), balance(), List.of());
        var locked = new
                InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .LockedConsumption(plan, stock(), balance(),
                                List.of());
        var service =
                new InvTransferReceiptDiscrepancyReturnShipmentCreationService(
                        new InvTransferReceiptDiscrepancyReturnShipmentWriteGate(
                                writeEnabled, receiptReady),
                        orderMapper, revisionMapper, detailMapper, v2Mapper,
                        returnMapper, lifecycleService, shipmentMapper,
                        shipmentDetailMapper, stockLogMapper, statusLogMapper,
                        deptScopeMapper, shopScopeService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, orderMapper, revisionMapper,
                detailMapper, v2Mapper, returnMapper, lifecycleService,
                shipmentMapper, shipmentDetailMapper, stockLogMapper,
                statusLogMapper, deptScopeMapper, shopScopeService, plan,
                locked);
    }

    private static void stubLocks(Fixture fixture)
    {
        SecurityContextHolder.setUserId("901");
        SecurityContextHolder.setUserName("operator");
        when(fixture.shopScopeService().resolveRequiredShopDept(202L))
                .thenReturn(202L);
        when(fixture.shopScopeService().hasUserShopScope(901L, 202L))
                .thenReturn(true);
        when(fixture.deptScopeMapper().countDeptInScope(202L, 202L))
                .thenReturn(1);
        when(fixture.deptScopeMapper().selectDeptTypeById(202L))
                .thenReturn("STORE");
        when(fixture.orderMapper()
                .selectInvTransferOrderByIdForUpdate(2101L))
                .thenReturn(order());
        when(fixture.revisionMapper()
                .selectLatestByTransferIdForUpdate(2101L))
                .thenReturn(revision());
        when(fixture.detailMapper().selectByTransferIdForUpdate(2101L))
                .thenReturn(List.of(detail()));
        when(fixture.v2Mapper().selectWarehouseModeForUpdate(202L))
                .thenReturn(mode());
    }

    private static void stubSuccess(Fixture fixture)
    {
        stubLocks(fixture);
        when(fixture.lifecycleService().lockForConsumption(2101L,
                fixture.plan().planVersion(), NOW))
                .thenReturn(fixture.locked());
        when(fixture.lifecycleService().planLockedConsumption(
                new BigDecimal("5.0000"), fixture.locked()))
                .thenReturn(fixture.plan().consumption());
        doAnswer(invocation -> {
            ((InvTransferShipment) invocation.getArgument(0))
                    .setShipmentId(3001L);
            return 1;
        }).when(fixture.shipmentMapper()).insertShipment(any());
        doAnswer(invocation -> {
            ((InvTransferShipmentDetail) invocation.getArgument(0))
                    .setShipmentDetailId(3002L);
            return 1;
        }).when(fixture.shipmentDetailMapper())
                .insertShipmentDetail(any());
        var plan = fixture.plan().consumption();
        var consumption =
                new InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                        .ConsumptionResult(5001L, 1001L,
                                new BigDecimal("5.0000"),
                                new BigDecimal("30.000000"), "CONSUMED",
                                BigDecimal.ZERO.setScale(4), plan.stock(),
                                plan.balance(), List.of());
        when(fixture.lifecycleService().consumeLocked(
                anyString(), anyLong(), anyLong(), any(), anyString(),
                any(), any())).thenReturn(consumption);
        doAnswer(invocation -> {
            ((InvStockLog) invocation.getArgument(0)).setLogId(5002L);
            return 1;
        }).when(fixture.stockLogMapper()).insertInvStockLog(any());
        doAnswer(invocation -> {
            ((InvTransferShipmentAllocationRecord) invocation.getArgument(0))
                    .setAllocationId(5003L);
            return 1;
        }).when(fixture.v2Mapper()).insertAllocation(any());
        doAnswer(invocation -> {
            ((InvStockLedgerDetailRecord) invocation.getArgument(0))
                    .setLedgerId(5004L);
            return 1;
        }).when(fixture.v2Mapper()).insertStockLedger(any());
        when(fixture.returnMapper().advanceChildDetail(any())).thenReturn(1);
        when(fixture.returnMapper().advanceChildOrder(any())).thenReturn(1);
        when(fixture.statusLogMapper().insertLog(any())).thenReturn(1);
    }

    private static
            InvTransferReceiptDiscrepancyReturnShipmentCreateRequest request(
                    InvTransferReceiptDiscrepancyReturnShipmentPolicy
                            .PreparedPlan plan)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnShipmentCreateRequest();
        value.setPlanVersion(plan.planVersion());
        value.setBasis("return-quarantine-reservation-v2");
        value.setQuantity(new BigDecimal("5.0000"));
        return value;
    }

    private static InvTransferOrder order()
    {
        var value = new InvTransferOrder();
        value.setTransferId(2101L);
        value.setOrderNo("TR-RETURN-2101");
        value.setVersion(4L);
        value.setStatus(InvStatusConstants.APPROVED);
        value.setTransferType(InvTransferTypes.STORE_RETURN);
        value.setFromDeptId(202L);
        value.setToDeptId(101L);
        value.setToDeptName("原发货仓");
        value.setReturnReasonCode("DAMAGED_RECEIPT_RETURN");
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setApprovalRound(1);
        return value;
    }

    private static InvTransferRevision revision()
    {
        var value = new InvTransferRevision();
        value.setRevisionId(2601L);
        value.setTransferId(2101L);
        value.setRevisionNo(2);
        value.setApprovalRound(1);
        value.setStatus(InvTransferRevisionStatuses.APPROVED);
        value.setSnapshotHash("d".repeat(64));
        return value;
    }

    private static InvTransferDetail detail()
    {
        var value = new InvTransferDetail();
        value.setDetailId(2102L);
        value.setTransferId(2101L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setItemCode("P-2401");
        value.setItemName("受损测试物料");
        value.setProductName("受损测试物料");
        value.setUnit("件");
        value.setQuantity(new BigDecimal("5.0000"));
        value.setDeliveredQuantity(BigDecimal.ZERO.setScale(4));
        value.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(new BigDecimal("6.000000"));
        return value;
    }

    private static InvWarehouseStockMode mode()
    {
        var value = new InvWarehouseStockMode();
        value.setWarehouseId(202L);
        value.setWriteMode("dual");
        value.setReadMode("detail");
        value.setReconcileStatus("passed");
        value.setLastReconcileBatch("return-reconcile-01");
        return value;
    }

    private static
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact()
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReservationLifecycleFact();
        value.setReservationId(1001L);
        value.setReservationRequestId("return-dispatch-0001");
        value.setActionId(2001L);
        value.setChildTransferId(2101L);
        value.setChildTransferDetailId(2102L);
        value.setReservationRound(1);
        value.setReceiptAllocationId(2201L);
        value.setShipmentAllocationId(2202L);
        value.setStockId(2301L);
        value.setBalanceId(2302L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setTrackingPolicy("lot");
        value.setReservedQuantity(new BigDecimal("5.0000"));
        value.setConsumedQuantity(BigDecimal.ZERO.setScale(4));
        value.setReleasedQuantity(BigDecimal.ZERO.setScale(4));
        value.setSourceCostPrice(new BigDecimal("6.000000"));
        value.setReservedAmount(new BigDecimal("30.000000"));
        value.setDecisionFingerprint("a".repeat(64));
        value.setStatus("ACTIVE");
        value.setVersion(2L);
        value.setChildStatus("approved");
        value.setApprovalRound(1);
        value.setSourceWarehouseId(202L);
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setRequestedQuantity(new BigDecimal("5.0000"));
        value.setDeliveredQuantity(BigDecimal.ZERO.setScale(4));
        value.setWorkflowId(2501L);
        value.setWorkflowType("return");
        value.setInventorySource("quarantine_detail");
        value.setWorkflowFingerprint("b".repeat(64));
        return value;
    }

    private static InvTransferReceiptTargetStock stock()
    {
        var value = new InvTransferReceiptTargetStock();
        value.setStockId(2301L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setShopDeptId(202L);
        value.setWarehouseId(202L);
        value.setCurrentQuantity(new BigDecimal("10.0000"));
        value.setAvailableQuantity(new BigDecimal("2.0000"));
        value.setLockedQuantity(new BigDecimal("5.0000"));
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("60.000000"));
        value.setVersion(7L);
        return value;
    }

    private static InvTransferReceiptTargetBalance balance()
    {
        var value = new InvTransferReceiptTargetBalance();
        value.setBalanceId(2302L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setCurrentQuantity(new BigDecimal("5.0000"));
        value.setAvailableQuantity(BigDecimal.ZERO.setScale(4));
        value.setLockedQuantity(new BigDecimal("5.0000"));
        value.setQuarantineQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setVersion(8L);
        return value;
    }

    private static List<Path> productionReferences(String value)
            throws Exception
    {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try
                        {
                            return Files.readString(path,
                                    StandardCharsets.UTF_8).contains(value);
                        }
                        catch (java.io.IOException error)
                        {
                            throw new IllegalStateException(error);
                        }
                    })
                    .map(root::relativize).toList();
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyReturnShipmentCreationService
                    service,
            InvTransferOrderMapper orderMapper,
            InvTransferRevisionMapper revisionMapper,
            InvTransferDetailMapper detailMapper,
            InvTransferShipmentCreationMapper v2Mapper,
            InvTransferReceiptDiscrepancyReturnShipmentCreationMapper
                    returnMapper,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    lifecycleService,
            InvTransferShipmentMapper shipmentMapper,
            InvTransferShipmentDetailMapper shipmentDetailMapper,
            InvStockLogMapper stockLogMapper,
            InvTransferStatusLogMapper statusLogMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService,
            InvTransferReceiptDiscrepancyReturnShipmentPolicy.PreparedPlan
                    plan,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleService
                    .LockedConsumption locked)
    {
    }
}
