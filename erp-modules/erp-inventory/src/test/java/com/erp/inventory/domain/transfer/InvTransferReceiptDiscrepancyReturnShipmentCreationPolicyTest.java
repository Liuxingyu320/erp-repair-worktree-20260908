package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferRevisionStatuses;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferRevision;

@DisplayName("V2差异退回固定隔离来源发货创建纯策略")
class
        InvTransferReceiptDiscrepancyReturnShipmentCreationPolicyTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @Test
    @DisplayName("全部剩余批次库存形成确定性V2发货命令")
    void shouldPrepareFullFixedLotShipment()
    {
        var plan = plan("lot", List.of());
        var prepared = prepare(plan, new BigDecimal("5.0000"), order(),
                revision(), detail(), mode());

        assertThat(prepared.quantity()).isEqualByComparingTo("5.0000");
        assertThat(prepared.amount()).isEqualByComparingTo("30.000000");
        assertThat(prepared.statusBefore()).isEqualTo("approved");
        assertThat(prepared.statusAfter()).isEqualTo("delivered");
        assertThat(prepared.orderVersionBefore()).isEqualTo(4L);
        assertThat(prepared.orderVersionAfter()).isEqualTo(5L);
        assertThat(prepared.shipmentNo())
                .matches("TSR220260803[a-f0-9]{32}");
        assertThat(prepare(plan, new BigDecimal("5.0000"), order(),
                revision(), detail(), mode()).shipmentNo())
                .isEqualTo(prepared.shipmentNo());
    }

    @Test
    @DisplayName("序列号固定计划允许正数分批并推进部分发货状态")
    void shouldPreparePartialSerialShipment()
    {
        var fact = fact("serial");
        var plan = InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                NOW, fact, stock(), balance(), List.of(
                        serial(fact, 11L, 101L, "S-1"),
                        serial(fact, 12L, 102L, "S-2"),
                        serial(fact, 13L, 103L, "S-3"),
                        serial(fact, 14L, 104L, "S-4"),
                        serial(fact, 15L, 105L, "S-5")));

        var prepared = prepare(plan, new BigDecimal("2.0000"), order(),
                revision(), detail(), mode());

        assertThat(prepared.deliveredBefore())
                .isEqualByComparingTo("0.0000");
        assertThat(prepared.deliveredAfter())
                .isEqualByComparingTo("2.0000");
        assertThat(prepared.amount()).isEqualByComparingTo("12.000000");
        assertThat(prepared.statusAfter()).isEqualTo("partial_delivered");
    }

    @Test
    @DisplayName("计划修订仓库模式和归属任一漂移均失败关闭")
    void shouldRejectAuthorityDrift()
    {
        var plan = plan("lot", List.of());
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .prepare("return-shipment-0001", "c".repeat(64),
                                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                                        .BASIS,
                                new BigDecimal("5.0000"), 901L,
                                "operator", NOW, order(), revision(),
                                detail(), mode(), plan,
                                requested(plan,
                                        new BigDecimal("5.0000"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("规划已变化");

        var driftedRevision = revision();
        driftedRevision.setApprovalRound(2);
        assertThatThrownBy(() -> prepare(plan, new BigDecimal("5.0000"),
                order(), driftedRevision, detail(), mode()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审批修订无效");

        var driftedMode = mode();
        driftedMode.setReconcileStatus("failed");
        assertThatThrownBy(() -> prepare(plan, new BigDecimal("5.0000"),
                order(), revision(), detail(), driftedMode))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权威模式无效");

        var driftedOrder = order();
        driftedOrder.setSourceBusinessId(9999L);
        assertThatThrownBy(() -> prepare(plan, new BigDecimal("5.0000"),
                driftedOrder, revision(), detail(), mode()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("归属、状态或版本无效");
    }

    @Test
    @DisplayName("超出剩余量和非秒精度命令均失败关闭")
    void shouldRejectOverShipmentOrInvalidTime()
    {
        var plan = plan("lot", List.of());
        assertThatThrownBy(() -> prepare(plan, new BigDecimal("6.0000"),
                order(), revision(), detail(), mode()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过隔离预留剩余数量");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                        .prepare("return-shipment-0001",
                                plan.planVersion(),
                                InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                                        .BASIS,
                                new BigDecimal("5.0000"), 901L,
                                "operator", NOW.plusNanos(1), order(),
                                revision(), detail(), mode(), plan,
                                requested(plan,
                                        new BigDecimal("5.0000"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("命令上下文无效");
    }

    private static
            InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                    .PreparedCreation prepare(
                            InvTransferReceiptDiscrepancyReturnShipmentPolicy
                                    .PreparedPlan plan,
                            BigDecimal quantity, InvTransferOrder order,
                            InvTransferRevision revision,
                            InvTransferDetail detail,
                            InvWarehouseStockMode mode)
    {
        return InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                .prepare("return-shipment-0001", plan.planVersion(),
                        InvTransferReceiptDiscrepancyReturnShipmentCreationPolicy
                                .BASIS,
                        quantity, 901L, "operator", NOW, order, revision,
                        detail, mode, plan, requested(plan, quantity));
    }

    private static InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
            .ConsumptionPlan requested(
                    InvTransferReceiptDiscrepancyReturnShipmentPolicy
                            .PreparedPlan plan,
                    BigDecimal quantity)
    {
        var fact = plan.fact();
        var serials = plan.consumption().serials().stream()
                .map(value -> serial(fact, value.receiptSerialId(),
                        value.serialId(), value.serialNo()))
                .toList();
        return InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                .planConsumption(quantity, fact, stock(), balance(), serials);
    }

    private static InvTransferReceiptDiscrepancyReturnShipmentPolicy
            .PreparedPlan plan(String trackingPolicy,
                    List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                            serials)
    {
        return InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(NOW,
                fact(trackingPolicy), stock(), balance(), serials);
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
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact(
                    String trackingPolicy)
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
        value.setTrackingPolicy(trackingPolicy);
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

    private static
            InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
            serial(
                    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
                            fact,
                    Long receiptSerialId, Long serialId, String serialNo)
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact();
        value.setBindingId(4000L + serialId);
        value.setReservationId(fact.getReservationId());
        value.setReceiptSerialId(receiptSerialId);
        value.setSerialId(serialId);
        value.setSerialNoSnapshot(serialNo);
        value.setWarehouseId(202L);
        value.setBalanceId(2302L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setLifecycleStatus("ACTIVE");
        value.setVersion(0L);
        value.setCurrentSerialNo(serialNo);
        value.setCurrentSerialStatus("quarantine_reserved");
        value.setCurrentWarehouseId(202L);
        value.setCurrentBalanceId(2302L);
        value.setCurrentLotId(2303L);
        value.setCurrentLocationId(2304L);
        return value;
    }
}
