package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.PreparedRelease;

@DisplayName("V2差异退回隔离预留消费释放纯策略")
class InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicyTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T10:00:00Z");

    @Test
    @DisplayName("全量消费同时扣减汇总和精确余额并闭合预留")
    void shouldConsumeWholeLotReservation()
    {
        var fact = fact("lot", "0.0000", "ACTIVE");

        PreparedConsumption prepared = policyConsume(fact, "5.0000",
                stock("0.0000"), balance("0.0000"), List.of());

        assertThat(prepared.statusAfter()).isEqualTo("CONSUMED");
        assertThat(prepared.remainingAfter()).isEqualByComparingTo("0.0000");
        assertThat(prepared.amount()).isEqualByComparingTo("30.000000");
        assertThat(prepared.stock().currentAfter())
                .isEqualByComparingTo("5.0000");
        assertThat(prepared.stock().lockedAfter())
                .isEqualByComparingTo("0.0000");
        assertThat(prepared.stock().quarantineAfter())
                .isEqualByComparingTo("3.0000");
        assertThat(prepared.stock().totalCostAfter())
                .isEqualByComparingTo("30.000000");
        assertThat(prepared.balance().currentAfter())
                .isEqualByComparingTo("0.0000");
        assertThat(prepared.serials()).isEmpty();
    }

    @Test
    @DisplayName("分批序列号消费按收货单件稳定排序并保持剩余冻结")
    void shouldPartiallyConsumeSerialsDeterministically()
    {
        var fact = fact("serial", "0.0000", "ACTIVE");
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = new ArrayList<>(List.of(
                        serial(fact, 13L, 103L, "S-3"),
                        serial(fact, 11L, 101L, "S-1"),
                        serial(fact, 15L, 105L, "S-5"),
                        serial(fact, 12L, 102L, "S-2"),
                        serial(fact, 14L, 104L, "S-4")));

        PreparedConsumption prepared = policyConsume(fact, "2.0000",
                stock("0.0000"), balance("0.0000"), serials);

        assertThat(prepared.statusAfter()).isEqualTo("PARTIAL");
        assertThat(prepared.remainingAfter()).isEqualByComparingTo("3.0000");
        assertThat(prepared.serials())
                .extracting(value -> value.receiptSerialId())
                .containsExactly(11L, 12L);
        assertThat(prepared.serials())
                .allSatisfy(value -> {
                    assertThat(value.lifecycleAfter()).isEqualTo("CONSUMED");
                    assertThat(value.physicalAfter()).isEqualTo("shipped");
                });
    }

    @Test
    @DisplayName("未消费预留全部释放回隔离数量且不改当前量和成本")
    void shouldReleaseWholeLotReservation()
    {
        var fact = fact("lot", "0.0000", "ACTIVE");

        PreparedRelease prepared = policyRelease(fact,
                stock("0.0000"), balance("0.0000"), List.of());

        assertThat(prepared.statusAfter()).isEqualTo("RELEASED");
        assertThat(prepared.quantity()).isEqualByComparingTo("5.0000");
        assertThat(prepared.stock().currentAfter())
                .isEqualByComparingTo("10.0000");
        assertThat(prepared.stock().lockedAfter())
                .isEqualByComparingTo("0.0000");
        assertThat(prepared.stock().quarantineAfter())
                .isEqualByComparingTo("8.0000");
        assertThat(prepared.stock().totalCostAfter())
                .isEqualByComparingTo("60.000000");
        assertThat(prepared.balance().quarantineAfter())
                .isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("分批消费后释放剩余序列号形成关闭状态")
    void shouldClosePartiallyConsumedReservationByRelease()
    {
        var fact = fact("serial", "2.0000", "PARTIAL");
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = List.of(
                        serial(fact, 13L, 103L, "S-3"),
                        serial(fact, 14L, 104L, "S-4"),
                        serial(fact, 15L, 105L, "S-5"));

        PreparedRelease prepared = policyRelease(fact,
                stock("2.0000"), balance("2.0000"), serials);

        assertThat(prepared.statusAfter()).isEqualTo("CLOSED");
        assertThat(prepared.quantity()).isEqualByComparingTo("3.0000");
        assertThat(prepared.releasedAfter()).isEqualByComparingTo("3.0000");
        assertThat(prepared.serials()).hasSize(3).allSatisfy(value -> {
            assertThat(value.lifecycleAfter()).isEqualTo("RELEASED");
            assertThat(value.physicalAfter()).isEqualTo("quarantine");
        });
    }

    @Test
    @DisplayName("只读规划复用同一守恒策略并建议全部剩余冻结量")
    void shouldPlanAllRemainingConsumptionWithoutCommandIdentity()
    {
        var fact = fact("serial", "2.0000", "PARTIAL");
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                serials = List.of(
                        serial(fact, 15L, 105L, "S-5"),
                        serial(fact, 13L, 103L, "S-3"),
                        serial(fact, 14L, 104L, "S-4"));

        var plan =
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .planAllRemainingConsumption(fact,
                                stock("2.0000"), balance("2.0000"),
                                serials);

        assertThat(plan.quantity()).isEqualByComparingTo("3.0000");
        assertThat(plan.amount()).isEqualByComparingTo("18.000000");
        assertThat(plan.statusBefore()).isEqualTo("PARTIAL");
        assertThat(plan.statusAfter()).isEqualTo("CONSUMED");
        assertThat(plan.serials()).extracting(value -> value.serialId())
                .containsExactly(103L, 104L, 105L);
    }

    @Test
    @DisplayName("超量消费和成本漂移均失败关闭")
    void shouldRejectOverConsumptionAndCostDrift()
    {
        var fact = fact("lot", "2.0000", "PARTIAL");
        assertThatThrownBy(() -> policyConsume(fact, "4.0000",
                stock("2.0000"), balance("2.0000"), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过");

        InvTransferReceiptTargetBalance drifted = balance("2.0000");
        drifted.setCostPrice(new BigDecimal("6.100000"));
        assertThatThrownBy(() -> policyRelease(fact,
                stock("2.0000"), drifted, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("成本锚点");
    }

    @Test
    @DisplayName("预留请求身份与开放状态累计数量必须自洽")
    void shouldRejectInvalidIdentityOrOpenLifecycleDrift()
    {
        var invalidIdentity = fact("lot", "0.0000", "ACTIVE");
        invalidIdentity.setReservationRequestId("short");
        assertThatThrownBy(() -> policyRelease(invalidIdentity,
                stock("0.0000"), balance("0.0000"), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("归属或工作流事实无效");

        var activeButConsumed = fact("lot", "2.0000", "ACTIVE");
        assertThatThrownBy(() -> policyRelease(activeButConsumed,
                stock("2.0000"), balance("2.0000"), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("累计数量或成本不守恒");

        var partialWithoutConsumption = fact("lot", "0.0000", "PARTIAL");
        assertThatThrownBy(() -> policyRelease(partialWithoutConsumption,
                stock("0.0000"), balance("0.0000"), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("累计数量或成本不守恒");
    }

    @Test
    @DisplayName("序列号绑定重复或物理状态漂移均失败关闭")
    void shouldRejectDuplicateOrDriftedSerialBinding()
    {
        var fact = fact("serial", "2.0000", "PARTIAL");
        var first = serial(fact, 13L, 103L, "S-3");
        var duplicate = serial(fact, 13L, 104L, "S-4");
        assertThatThrownBy(() -> policyRelease(fact,
                stock("2.0000"), balance("2.0000"),
                List.of(first, duplicate,
                        serial(fact, 15L, 105L, "S-5"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序列号绑定事实无效");

        var drifted = serial(fact, 14L, 104L, "S-4");
        drifted.setCurrentSerialStatus("quarantine");
        assertThatThrownBy(() -> policyRelease(fact,
                stock("2.0000"), balance("2.0000"),
                List.of(first, drifted,
                        serial(fact, 15L, 105L, "S-5"))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("序列号绑定事实无效");
    }

    private static PreparedConsumption policyConsume(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            String quantity, InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serials)
    {
        return InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                .prepareConsumption("return-consume-0001", 3001L, 3002L,
                        new BigDecimal(quantity), "operator", NOW, fact,
                        stock, balance, serials);
    }

    private static PreparedRelease policyRelease(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serials)
    {
        return InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                .prepareRelease("return-release-0001", "APPROVAL_REJECT",
                        "approval:event-1", "operator", NOW, fact, stock,
                        balance, serials);
    }

    private static
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact(
                    String trackingPolicy, String consumed, String status)
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
        value.setConsumedQuantity(new BigDecimal(consumed));
        value.setReleasedQuantity(BigDecimal.ZERO.setScale(4));
        value.setSourceCostPrice(new BigDecimal("6.000000"));
        value.setReservedAmount(new BigDecimal("30.000000"));
        value.setDecisionFingerprint("a".repeat(64));
        value.setStatus(status);
        value.setVersion(2L);
        value.setChildStatus("approved");
        value.setApprovalRound(1);
        value.setSourceWarehouseId(202L);
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(2001L);
        value.setRequestedQuantity(new BigDecimal("5.0000"));
        value.setDeliveredQuantity(new BigDecimal(consumed));
        value.setWorkflowId(2501L);
        value.setWorkflowType("return");
        value.setInventorySource("quarantine_detail");
        value.setWorkflowFingerprint("b".repeat(64));
        return value;
    }

    private static InvTransferReceiptTargetStock stock(String consumed)
    {
        BigDecimal used = new BigDecimal(consumed);
        var value = new InvTransferReceiptTargetStock();
        value.setStockId(2301L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setShopDeptId(202L);
        value.setWarehouseId(202L);
        value.setCurrentQuantity(new BigDecimal("10.0000").subtract(used));
        value.setAvailableQuantity(new BigDecimal("2.0000"));
        value.setLockedQuantity(new BigDecimal("5.0000").subtract(used));
        value.setQuarantineQuantity(new BigDecimal("3.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("60.000000")
                .subtract(used.multiply(new BigDecimal("6.000000"))));
        value.setVersion(7L);
        return value;
    }

    private static InvTransferReceiptTargetBalance balance(String consumed)
    {
        BigDecimal used = new BigDecimal(consumed);
        var value = new InvTransferReceiptTargetBalance();
        value.setBalanceId(2302L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(2401L);
        value.setProductId(2401L);
        value.setLotId(2303L);
        value.setLocationId(2304L);
        value.setCurrentQuantity(new BigDecimal("5.0000").subtract(used));
        value.setAvailableQuantity(BigDecimal.ZERO.setScale(4));
        value.setLockedQuantity(new BigDecimal("5.0000").subtract(used));
        value.setQuarantineQuantity(BigDecimal.ZERO.setScale(4));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000")
                .subtract(used.multiply(new BigDecimal("6.000000"))));
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
        value.setWarehouseId(fact.getSourceWarehouseId());
        value.setBalanceId(fact.getBalanceId());
        value.setLotId(fact.getLotId());
        value.setLocationId(fact.getLocationId());
        value.setLifecycleStatus("ACTIVE");
        value.setVersion(0L);
        value.setCurrentSerialNo(serialNo);
        value.setCurrentSerialStatus("quarantine_reserved");
        value.setCurrentWarehouseId(fact.getSourceWarehouseId());
        value.setCurrentBalanceId(fact.getBalanceId());
        value.setCurrentLotId(fact.getLotId());
        value.setCurrentLocationId(fact.getLocationId());
        return value;
    }
}
