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
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.PreparedChild;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.PreparedReservation;

@DisplayName("V2差异裁决退回子调拨与隔离预留纯策略")
class InvTransferReceiptDiscrepancyReturnPolicyTest
{
    @Test
    @DisplayName("仓库补货受损退回反转为返仓并写固定系统原因")
    void shouldReverseWarehouseTransferToStoreReturn()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact(
                InvTransferTypes.WAREHOUSE, "lot", "2.0000");
        Source source = source(fact);

        PreparedChild child = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(source, fact, 202L);

        assertThat(child.transferType())
                .isEqualTo(InvTransferTypes.STORE_RETURN);
        assertThat(child.fromDeptId()).isEqualTo(202L);
        assertThat(child.toDeptId()).isEqualTo(101L);
        assertThat(child.returnReasonCode()).isEqualTo(
                InvTransferReceiptDiscrepancyReturnPolicy
                        .RETURN_REASON_CODE);
        assertThat(child.sourceBusinessType())
                .isEqualTo("transfer_discrepancy_return");
        assertThat(child.sourceBusinessId()).isEqualTo(41L);
    }

    @Test
    @DisplayName("返仓受损退回反转为仓库补货并由原来源门店发起")
    void shouldReverseStoreReturnToWarehouseTransfer()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact(
                InvTransferTypes.STORE_RETURN, "lot", "2.0000");
        Source source = source(fact);

        PreparedChild child = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(source, fact, 101L);

        assertThat(child.transferType())
                .isEqualTo(InvTransferTypes.WAREHOUSE);
        assertThat(child.fromDeptId()).isEqualTo(202L);
        assertThat(child.toDeptId()).isEqualTo(101L);
        assertThat(child.returnReasonCode()).isNull();
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnPolicy.prepareChild(
                        source, fact, 202L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("发起组织");
    }

    @Test
    @DisplayName("异店受损退回保持异店类型并由反向来源门店发起")
    void shouldReverseCrossStoreFromOriginalTarget()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact(
                InvTransferTypes.CROSS_STORE, "lot", "2.0000");

        PreparedChild child = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(source(fact), fact, 202L);

        assertThat(child.transferType())
                .isEqualTo(InvTransferTypes.CROSS_STORE);
        assertThat(child.fromDeptId()).isEqualTo(202L);
        assertThat(child.toDeptId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("隔离预留保持当前量和成本并把隔离数量精确转入锁定")
    void shouldReserveQuarantineWithConservation()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact(
                InvTransferTypes.WAREHOUSE, "lot", "2.0000");
        Source source = source(fact);
        PreparedChild child = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(source, fact, 202L);

        PreparedReservation reservation =
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(source, fact, child,
                                saved(child), detail(), 1,
                                new BigDecimal("1.0000"), stock(), lot(),
                                location(), balance(), List.of());

        assertThat(reservation.disposedQuantityAfter())
                .isEqualByComparingTo("3.0000");
        assertThat(reservation.stock().currentAfter())
                .isEqualByComparingTo("10.0000");
        assertThat(reservation.stock().lockedAfter())
                .isEqualByComparingTo("4.0000");
        assertThat(reservation.stock().quarantineAfter())
                .isEqualByComparingTo("3.0000");
        assertThat(reservation.balance().lockedBefore())
                .isEqualByComparingTo("1.0000");
        assertThat(reservation.balance().lockedAfter())
                .isEqualByComparingTo("3.0000");
        assertThat(reservation.balance().quarantineAfter())
                .isEqualByComparingTo("2.0000");
        assertThat(reservation.serials()).isEmpty();
    }

    @Test
    @DisplayName("序列号退回按收货单件稳定排序并冻结精确数量")
    void shouldSelectSerialsDeterministically()
    {
        InvTransferReceiptDiscrepancyReturnFact fact = fact(
                InvTransferTypes.WAREHOUSE, "serial", "2.0000");
        Source source = source(fact);
        PreparedChild child = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(source, fact, 202L);

        PreparedReservation reservation =
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(source, fact, child,
                                saved(child), detail(), 1, BigDecimal.ZERO,
                                stock(), lot(), location(), balance(),
                                List.of(serial(fact, 92L, 902L, "S-2"),
                                        serial(fact, 91L, 901L, "S-1"),
                                        serial(fact, 93L, 903L, "S-3")));

        assertThat(reservation.serials())
                .extracting(value -> value.receiptSerialId())
                .containsExactly(91L, 92L);
        assertThat(reservation.serials())
                .allSatisfy(value -> assertThat(value.statusAfter())
                        .isEqualTo("quarantine_reserved"));
    }

    @Test
    @DisplayName("累计处置超量、余额成本漂移和序列号小数数量全部失败关闭")
    void shouldRejectOverDispositionCostDriftAndFractionalSerial()
    {
        InvTransferReceiptDiscrepancyReturnFact lotFact = fact(
                InvTransferTypes.WAREHOUSE, "lot", "2.0000");
        Source lotSource = source(lotFact);
        PreparedChild lotChild = InvTransferReceiptDiscrepancyReturnPolicy
                .prepareChild(lotSource, lotFact, 202L);

        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(lotSource, lotFact, lotChild,
                                saved(lotChild), detail(), 1,
                                new BigDecimal("4.0000"), stock(), lot(),
                                location(), balance(), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("剩余数量");

        InvTransferReceiptTargetBalance drifted = balance();
        drifted.setCostPrice(new BigDecimal("6.100000"));
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(lotSource, lotFact, lotChild,
                                saved(lotChild), detail(), 1,
                                BigDecimal.ZERO, stock(), lot(), location(),
                                drifted, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("成本锚点");

        InvTransferReceiptDiscrepancyReturnFact serialFact = fact(
                InvTransferTypes.WAREHOUSE, "serial", "1.5000");
        Source serialSource = source(serialFact);
        PreparedChild serialChild =
                InvTransferReceiptDiscrepancyReturnPolicy.prepareChild(
                        serialSource, serialFact, 202L);
        assertThatThrownBy(() ->
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(serialSource, serialFact,
                                serialChild, saved(serialChild),
                                detail("1.5000"), 1, BigDecimal.ZERO,
                                stock(), lot(), location(), balance(),
                                List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须为整数");
    }

    private static Source source(
            InvTransferReceiptDiscrepancyReturnFact fact)
    {
        return fact.toWorkflowSource("return-request-0001", 71L,
                "executor", Instant.parse("2026-08-03T01:02:03Z"));
    }

    private static InvTransferReceiptDiscrepancyReturnFact fact(
            String transferType, String trackingPolicy, String quantity)
    {
        InvTransferReceiptDiscrepancyReturnFact value =
                new InvTransferReceiptDiscrepancyReturnFact();
        value.setCaseId(11L);
        value.setCaseVersionBefore(3L);
        value.setAdjudicationId(31L);
        value.setActionId(41L);
        value.setActionVersionBefore(0L);
        value.setDiscrepancyType("damaged");
        value.setActionType("return_to_source");
        value.setParentTransferId(51L);
        value.setParentTransferType(transferType);
        value.setFromDeptId(101L);
        value.setFromWarehouseId(101L);
        value.setToDeptId(202L);
        value.setToWarehouseId(202L);
        value.setReceiptId(61L);
        value.setReceiptAllocationId(62L);
        value.setShipmentId(63L);
        value.setShipmentAllocationId(64L);
        value.setTargetWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(301L);
        value.setProductId(301L);
        value.setTrackingPolicy(trackingPolicy);
        value.setDamagedQuantity(new BigDecimal("5.0000"));
        value.setQuantity(new BigDecimal(quantity));
        value.setSourceCostPrice(new BigDecimal("6.000000"));
        value.setAmount(new BigDecimal(quantity)
                .multiply(new BigDecimal("6.000000")).setScale(6));
        value.setQuarantineBalanceId(701L);
        value.setQuarantineLotId(702L);
        value.setQuarantineLocationId(703L);
        value.setDecisionFingerprint("a".repeat(64));
        return value;
    }

    private static InvTransferOrder saved(PreparedChild child)
    {
        InvTransferOrder value = new InvTransferOrder();
        value.setTransferId(801L);
        value.setStatus(InvStatusConstants.DRAFT);
        value.setTransferType(child.transferType());
        value.setFromDeptId(child.fromDeptId());
        value.setFromWarehouseId(child.fromWarehouseId());
        value.setToDeptId(child.toDeptId());
        value.setToWarehouseId(child.toWarehouseId());
        value.setSourceBusinessType(child.sourceBusinessType());
        value.setSourceBusinessId(child.sourceBusinessId());
        return value;
    }

    private static InvTransferDetail detail()
    {
        return detail("2.0000");
    }

    private static InvTransferDetail detail(String quantity)
    {
        InvTransferDetail value = new InvTransferDetail();
        value.setDetailId(802L);
        value.setTransferId(801L);
        value.setItemType("product");
        value.setItemId(301L);
        value.setProductId(301L);
        value.setQuantity(new BigDecimal(quantity));
        value.setDeliveredQuantity(BigDecimal.ZERO);
        value.setReceivedQuantity(BigDecimal.ZERO);
        return value;
    }

    private static InvTransferReceiptTargetStock stock()
    {
        InvTransferReceiptTargetStock value =
                new InvTransferReceiptTargetStock();
        value.setStockId(901L);
        value.setItemType("product");
        value.setItemId(301L);
        value.setProductId(301L);
        value.setShopDeptId(202L);
        value.setWarehouseId(202L);
        value.setCurrentQuantity(new BigDecimal("10.0000"));
        value.setAvailableQuantity(new BigDecimal("3.0000"));
        value.setLockedQuantity(new BigDecimal("2.0000"));
        value.setQuarantineQuantity(new BigDecimal("5.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("60.000000"));
        value.setVersion(7L);
        return value;
    }

    private static InvTransferReceiptTargetLot lot()
    {
        InvTransferReceiptTargetLot value =
                new InvTransferReceiptTargetLot();
        value.setLotId(702L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(301L);
        value.setProductId(301L);
        value.setReceiptDisposition("damaged");
        value.setQcStatus("quarantine");
        value.setLotStatus("active");
        return value;
    }

    private static InvTransferReceiptLocationCandidate location()
    {
        InvTransferReceiptLocationCandidate value =
                new InvTransferReceiptLocationCandidate();
        value.setLocationId(703L);
        value.setWarehouseId(202L);
        value.setLocationType("quarantine");
        value.setStatus("0");
        value.setVirtualFlag("0");
        return value;
    }

    private static InvTransferReceiptTargetBalance balance()
    {
        InvTransferReceiptTargetBalance value =
                new InvTransferReceiptTargetBalance();
        value.setBalanceId(701L);
        value.setWarehouseId(202L);
        value.setItemType("product");
        value.setItemId(301L);
        value.setProductId(301L);
        value.setLotId(702L);
        value.setLocationId(703L);
        value.setCurrentQuantity(new BigDecimal("5.0000"));
        value.setAvailableQuantity(BigDecimal.ZERO);
        value.setLockedQuantity(new BigDecimal("1.0000"));
        value.setQuarantineQuantity(new BigDecimal("4.0000"));
        value.setCostPrice(new BigDecimal("6.000000"));
        value.setTotalCost(new BigDecimal("30.000000"));
        value.setVersion(8L);
        return value;
    }

    private static InvTransferReceiptDiscrepancyReturnSerialFact serial(
            InvTransferReceiptDiscrepancyReturnFact fact,
            Long receiptSerialId, Long serialId, String serialNo)
    {
        InvTransferReceiptDiscrepancyReturnSerialFact value =
                new InvTransferReceiptDiscrepancyReturnSerialFact();
        value.setReceiptSerialId(receiptSerialId);
        value.setReceiptId(fact.getReceiptId());
        value.setReceiptAllocationId(fact.getReceiptAllocationId());
        value.setShipmentId(fact.getShipmentId());
        value.setShipmentAllocationId(fact.getShipmentAllocationId());
        value.setSerialId(serialId);
        value.setSerialNoSnapshot(serialNo);
        value.setCurrentSerialNo(serialNo);
        value.setDisposition("damaged");
        value.setItemType(fact.getItemType());
        value.setItemId(fact.getItemId());
        value.setCurrentWarehouseId(fact.getTargetWarehouseId());
        value.setCurrentBalanceId(fact.getQuarantineBalanceId());
        value.setCurrentLotId(fact.getQuarantineLotId());
        value.setCurrentLocationId(fact.getQuarantineLocationId());
        value.setReceiptStatusAfter("quarantine");
        value.setCurrentStatus("quarantine");
        return value;
    }
}
