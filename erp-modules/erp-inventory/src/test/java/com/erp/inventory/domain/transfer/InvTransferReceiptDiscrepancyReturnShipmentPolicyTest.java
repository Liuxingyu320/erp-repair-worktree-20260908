package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

@DisplayName("V2差异退回隔离预留只读发货规划纯策略")
class InvTransferReceiptDiscrepancyReturnShipmentPolicyTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @Test
    @DisplayName("唯一隔离批次形成全量剩余建议和确定性版本")
    void shouldPlanFixedLotAndIgnorePresentationTimeInFingerprint()
    {
        var fact = fact("lot");
        var stock = stock();

        var first = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW, fact, stock, balance(), List.of());
        var later = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW.plusSeconds(30), fact, stock, balance(),
                        List.of());

        assertThat(first.planVersion()).matches("[a-f0-9]{64}");
        assertThat(later.planVersion()).isEqualTo(first.planVersion());
        assertThat(first.consumption().quantity())
                .isEqualByComparingTo("5.0000");
        assertThat(first.consumption().amount())
                .isEqualByComparingTo("30.000000");
        assertThat(first.consumption().serials()).isEmpty();

        stock.setVersion(8L);
        var changed = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW, fact, stock, balance(), List.of());
        assertThat(changed.planVersion()).isNotEqualTo(first.planVersion());
    }

    @Test
    @DisplayName("序列号建议按收货单件和序列号稳定排序并进入版本")
    void shouldPlanStableSerialBindings()
    {
        var fact = fact("serial");
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = List.of(
                        serial(fact, 13L, 103L, "S-3"),
                        serial(fact, 11L, 101L, "S-1"),
                        serial(fact, 12L, 102L, "S-2"),
                        serial(fact, 15L, 105L, "S-5"),
                        serial(fact, 14L, 104L, "S-4"));

        var prepared = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW, fact, stock(), balance(), serials);

        assertThat(prepared.consumption().serials())
                .extracting(value -> value.receiptSerialId())
                .containsExactly(11L, 12L, 13L, 14L, 15L);
        assertThat(prepared.consumption().serials())
                .allSatisfy(value -> {
                    assertThat(value.lifecycleBefore()).isEqualTo("ACTIVE");
                    assertThat(value.physicalBefore())
                            .isEqualTo("quarantine_reserved");
                });

        serials.get(0).setVersion(1L);
        var changed = InvTransferReceiptDiscrepancyReturnShipmentPolicy
                .prepare(NOW, fact, stock(), balance(), serials);
        assertThat(changed.planVersion()).isNotEqualTo(
                prepared.planVersion());
    }

    @Test
    @DisplayName("生成时间必须是秒精度且库存漂移继续由P10规则失败关闭")
    void shouldRejectInvalidTimeOrInventoryDrift()
    {
        var fact = fact("lot");
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                        NOW.plusNanos(1), fact, stock(), balance(), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("生成时间无效");

        var drifted = balance();
        drifted.setLockedQuantity(new BigDecimal("4.0000"));
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnShipmentPolicy.prepare(
                        NOW, fact, stock(), drifted, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结余额不足或守恒无效");
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
