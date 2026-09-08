package com.erp.inventory.domain.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

@DisplayName("V2调拨收货纯原子变更规划")
class InvTransferShipmentReceiptMutationPlannerTest
{
    private static final String PLAN = "a".repeat(64);
    private static final String FINGERPRINT = "c".repeat(64);
    private static final Instant ARRIVED = Instant.parse(
            "2026-08-02T10:00:00Z");

    @Test
    @DisplayName("合格残损短缺分别生成库存与不移动序列号动作")
    void shouldSplitDispositionsWithoutStockingShortage()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "serial", "3");
        Map<Long, InvTransferShipmentReceiptLockedBoundary.Serial> serials =
                Map.of(1001L, serial(1001L, 81L, 501L, 801L),
                        1002L, serial(1002L, 81L, 501L, 801L),
                        1003L, serial(1003L, 81L, 501L, 801L));
        InvTransferShipmentReceiptValidatedCommand command = command(true,
                List.of(commandAllocation(81L, "1", "1", "1",
                        List.of(1001L), List.of(1002L), List.of(1003L))),
                "1", "1", "1", "0");

        InvTransferShipmentReceiptPreparedMutation result = prepare(
                boundary(Map.of(81L, source), Map.of(), Map.of(), Map.of(),
                        serials), command);

        assertThat(result.receipt().receiptStatus())
                .isEqualTo("discrepancy");
        assertThat(result.lifecycle().shipmentStatusAfter())
                .isEqualTo(InvStatusConstants.DISCREPANCY);
        assertThat(result.targetStocks()).singleElement().satisfies(stock -> {
            assertThat(stock.acceptedQuantity())
                    .isEqualByComparingTo("1");
            assertThat(stock.damagedQuantity())
                    .isEqualByComparingTo("1");
            assertThat(stock.incomingCost())
                    .isEqualByComparingTo("20.000000");
        });
        assertThat(result.targetLots()).extracting(value ->
                value.key().disposition())
                .containsExactly("accepted", "damaged");
        assertThat(result.targetBalances()).hasSize(2);
        assertThat(result.targetBalances()).allSatisfy(balance ->
                assertThat(balance.availableQuantity()
                        .add(balance.quarantineQuantity()))
                        .isEqualByComparingTo("1"));
        assertThat(result.discrepancyCases()).hasSize(2);
        assertThat(result.discrepancyCases()).extracting(value ->
                value.shipmentAllocationId() + "|"
                        + value.discrepancyType())
                .containsExactly("81|damaged", "81|shortage");
        assertThat(result.discrepancyCases()).allSatisfy(value -> {
            assertThat(value.discrepancyQuantity())
                    .isEqualByComparingTo("1");
            assertThat(value.sourceCostPrice())
                    .isEqualByComparingTo("10.000000");
            assertThat(value.discrepancyAmount())
                    .isEqualByComparingTo("10.000000");
            assertThat(value.factFingerprint()).matches("[a-f0-9]{64}");
            assertThat(value.discrepancyNote()).isEqualTo("差异说明");
            assertThat(value.attachmentRefs()).isEqualTo("attachment-1");
        });
        assertThat(result.discrepancyCases().get(0).factFingerprint())
                .isNotEqualTo(result.discrepancyCases().get(1)
                        .factFingerprint());
        assertThat(result.serials()).extracting(
                InvTransferShipmentReceiptPreparedMutation.Serial
                        ::disposition)
                .containsExactly("accepted", "damaged", "shortage");
        InvTransferShipmentReceiptPreparedMutation.Serial shortage =
                result.serials().get(2);
        assertThat(shortage.targetLotKey()).isNull();
        assertThat(shortage.targetLocationId()).isNull();
        assertThat(shortage.statusAfter()).isEqualTo("shipped");
        assertThat(result.shipmentDetails()).singleElement()
                .satisfies(detail -> {
                    assertThat(detail.fulfillmentDelta())
                            .isEqualByComparingTo("1");
                    assertThat(detail.receivedQuantityAfter())
                            .isEqualByComparingTo("1");
                });
    }

    @Test
    @DisplayName("全部合格收货复用已锁目标行并完成生命周期")
    void shouldReuseExistingTargetsAndCompleteLifecycle()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "lot", "2");
        InvShipmentPlanningItemKey itemKey =
                new InvShipmentPlanningItemKey("product", 1001L);
        InvTransferReceiptDerivedLotKey lotKey =
                new InvTransferReceiptDerivedLotKey(302L, "product",
                        1001L, 501L, "accepted");
        InvTransferShipmentReceiptLockedBoundary.TargetStock stock =
                new InvTransferShipmentReceiptLockedBoundary.TargetStock(
                        601L, itemKey, 1001L, 302L, 302L,
                        new BigDecimal("10"), BigDecimal.ONE,
                        new BigDecimal("8"), BigDecimal.ONE,
                        new BigDecimal("5"), new BigDecimal("50"), 7L);
        InvTransferShipmentReceiptLockedBoundary.TargetLot lot =
                new InvTransferShipmentReceiptLockedBoundary.TargetLot(
                        701L, lotKey, "TRR-302-501-A", 1001L, "passed",
                        "active");
        InvTransferShipmentReceiptLockedBoundary.TargetBalance balance =
                new InvTransferShipmentReceiptLockedBoundary.TargetBalance(
                        8011L, 302L, 701L, 901L,
                        new BigDecimal("4"), BigDecimal.ONE,
                        new BigDecimal("3"), BigDecimal.ZERO,
                        new BigDecimal("5"), new BigDecimal("20"), 3L);
        InvTransferShipmentReceiptValidatedCommand command = command(true,
                List.of(commandAllocation(81L, "2", "0", "0",
                        List.of(), List.of(), List.of())),
                "2", "0", "0", "0");

        InvTransferShipmentReceiptPreparedMutation result = prepare(
                boundary(Map.of(81L, source), Map.of(itemKey, stock),
                        Map.of(lotKey, lot),
                        Map.of(new InvTransferReceiptBalanceKey(701L, 901L),
                                balance), Map.of()), command);

        assertThat(result.receipt().receiptStatus()).isEqualTo("completed");
        assertThat(result.lifecycle().shipmentStatusAfter())
                .isEqualTo(InvStatusConstants.RECEIVED);
        assertThat(result.targetStocks()).singleElement()
                .satisfies(value -> {
                    assertThat(value.existingStockId()).isEqualTo(601L);
                    assertThat(value.expectedVersion()).isEqualTo(7L);
                });
        assertThat(result.targetLots()).singleElement()
                .satisfies(value ->
                        assertThat(value.existingLotId()).isEqualTo(701L));
        assertThat(result.targetBalances()).singleElement()
                .satisfies(value -> {
                    assertThat(value.existingBalanceId()).isEqualTo(8011L);
                    assertThat(value.expectedVersion()).isEqualTo(3L);
                });
        assertThat(result.transferDetails()).singleElement()
                .satisfies(value ->
                        assertThat(value.receivedQuantityAfter())
                                .isEqualByComparingTo("2"));
        assertThat(result.discrepancyCases()).isEmpty();
    }

    @Test
    @DisplayName("仍有待收数量时生成部分收货生命周期")
    void shouldPlanPartialLifecycle()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "lot", "2");
        InvTransferShipmentReceiptValidatedCommand command = command(false,
                List.of(commandAllocation(81L, "1", "0", "0",
                        List.of(), List.of(), List.of())),
                "1", "0", "0", "1");

        InvTransferShipmentReceiptPreparedMutation result = prepare(
                boundary(Map.of(81L, source), Map.of(), Map.of(), Map.of(),
                        Map.of()), command);

        assertThat(result.receipt().receiptStatus()).isEqualTo("partial");
        assertThat(result.lifecycle().transferStatusAfter())
                .isEqualTo(InvStatusConstants.PARTIAL_RECEIVED);
    }

    @Test
    @DisplayName("当前发货批次完成但调拨仍有其他明细时单据保持部分收货")
    void shouldNotCompleteOrderFromShipmentRemainingAlone()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "lot", "1");
        InvTransferShipmentReceiptLockedBoundary base = boundary(
                Map.of(81L, source), Map.of(), Map.of(), Map.of(), Map.of());
        Map<Long, InvTransferShipmentReceiptLockedBoundary.TransferDetail>
                transferDetails = new HashMap<>(base.transferDetails());
        transferDetails.put(402L,
                new InvTransferShipmentReceiptLockedBoundary.TransferDetail(
                        402L, "product", 1002L, 1002L, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ZERO));
        InvTransferShipmentReceiptLockedBoundary withOtherDetail =
                new InvTransferShipmentReceiptLockedBoundary(base.header(),
                        base.composition(), base.allocations(),
                        transferDetails, base.targetStocks(),
                        base.sourceLots(), base.targetLots(),
                        base.targetLocations(), base.targetBalances(),
                        base.serials());
        InvTransferShipmentReceiptValidatedCommand command = command(true,
                List.of(commandAllocation(81L, "1", "0", "0",
                        List.of(), List.of(), List.of())),
                "1", "0", "0", "0");

        InvTransferShipmentReceiptPreparedMutation result = prepare(
                withOtherDetail, command);

        assertThat(result.lifecycle().shipmentStatusAfter())
                .isEqualTo(InvStatusConstants.RECEIVED);
        assertThat(result.lifecycle().transferStatusAfter())
                .isEqualTo(InvStatusConstants.PARTIAL_RECEIVED);
    }

    @Test
    @DisplayName("纯短缺只生成差异审计不生成任何目标库存动作")
    void shouldKeepPureShortageOutOfTargetStock()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "serial", "1");
        InvTransferShipmentReceiptValidatedCommand command = command(true,
                List.of(commandAllocation(81L, "0", "0", "1",
                        List.of(), List.of(), List.of(1001L))),
                "0", "0", "1", "0");

        InvTransferShipmentReceiptPreparedMutation result = prepare(
                boundary(Map.of(81L, source), Map.of(), Map.of(), Map.of(),
                        Map.of(1001L, serial(1001L, 81L, 501L, 801L))),
                command);

        assertThat(result.targetStocks()).isEmpty();
        assertThat(result.targetLots()).isEmpty();
        assertThat(result.targetBalances()).isEmpty();
        assertThat(result.stockLogs()).isEmpty();
        assertThat(result.ledgers()).isEmpty();
        assertThat(result.shipmentDetails()).isEmpty();
        assertThat(result.transferDetails()).isEmpty();
        assertThat(result.discrepancyCases()).singleElement()
                .satisfies(value -> {
                    assertThat(value.discrepancyType())
                            .isEqualTo("shortage");
                    assertThat(value.discrepancyQuantity())
                            .isEqualByComparingTo("1");
                    assertThat(value.discrepancyAmount())
                            .isEqualByComparingTo("10.000000");
                });
        assertThat(result.serials()).singleElement().satisfies(serial -> {
            assertThat(serial.disposition()).isEqualTo("shortage");
            assertThat(serial.targetLotKey()).isNull();
            assertThat(serial.statusAfter()).isEqualTo("shipped");
        });
    }

    @Test
    @DisplayName("输入分配顺序不影响确定性 Prepared Mutation")
    void shouldBeIndependentOfInputOrder()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation first =
                allocation(81L, 71L, 401L, 501L, 801L, "lot", "1");
        InvTransferShipmentReceiptLockedBoundary.Allocation second =
                allocation(82L, 72L, 402L, 502L, 802L, "lot", "1");
        InvTransferShipmentReceiptValidatedCommand.Allocation firstCommand =
                commandAllocation(81L, "0", "1", "0", List.of(),
                        List.of(), List.of());
        InvTransferShipmentReceiptValidatedCommand.Allocation secondCommand =
                commandAllocation(82L, "0", "0", "1", List.of(),
                        List.of(), List.of());
        InvTransferShipmentReceiptLockedBoundary boundary = boundary(
                Map.of(82L, second, 81L, first), Map.of(), Map.of(),
                Map.of(), Map.of());

        InvTransferShipmentReceiptPreparedMutation forward = prepare(boundary,
                command(true, List.of(firstCommand, secondCommand), "0",
                        "1", "1", "0"));
        InvTransferShipmentReceiptPreparedMutation reverse = prepare(boundary,
                command(true, List.of(secondCommand, firstCommand), "0",
                        "1", "1", "0"));

        assertThat(reverse).isEqualTo(forward);
        assertThat(forward.allocations()).extracting(
                InvTransferShipmentReceiptPreparedMutation.Allocation
                        ::shipmentAllocationId)
                .containsExactly(81L, 82L);
        assertThat(forward.discrepancyCases()).extracting(value ->
                value.shipmentAllocationId() + "|"
                        + value.discrepancyType())
                .containsExactly("81|damaged", "82|shortage");
    }

    @Test
    @DisplayName("目标库存锁定快照不守恒时拒绝生成写计划")
    void shouldRejectBrokenTargetStockInvariant()
    {
        InvTransferShipmentReceiptLockedBoundary.Allocation source =
                allocation(81L, 71L, 401L, 501L, 801L, "lot", "1");
        InvShipmentPlanningItemKey itemKey =
                new InvShipmentPlanningItemKey("product", 1001L);
        InvTransferShipmentReceiptLockedBoundary.TargetStock broken =
                new InvTransferShipmentReceiptLockedBoundary.TargetStock(
                        601L, itemKey, 1001L, 302L, 302L,
                        new BigDecimal("10"), BigDecimal.ONE,
                        new BigDecimal("10"), BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.TEN, 1L);
        InvTransferShipmentReceiptValidatedCommand command = command(true,
                List.of(commandAllocation(81L, "1", "0", "0",
                        List.of(), List.of(), List.of())),
                "1", "0", "0", "0");

        assertThatThrownBy(() -> prepare(boundary(Map.of(81L, source),
                Map.of(itemKey, broken), Map.of(), Map.of(), Map.of()),
                command)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("汇总库存快照不守恒");
    }

    @Test
    @DisplayName("预期退回整批实收只进入隔离库存且推进履约累计")
    void shouldPlanExpectedReturnAsQuarantineFulfillment()
    {
        var source = returnAllocation("lot", "2");
        var result = prepareReturn(returnBoundary(source, Map.of(), true),
                returnCommand(true, "2", "0", "0", List.of(),
                        List.of()));

        assertThat(result.receipt().receiptSemantic()).isEqualTo(
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .FIXED_RETURN);
        assertThat(result.receipt().acceptedQuantity()).isZero();
        assertThat(result.receipt().damagedQuantity())
                .isEqualByComparingTo("2");
        assertThat(result.receipt().receiptStatus()).isEqualTo("completed");
        assertThat(result.targetStocks()).singleElement().satisfies(stock -> {
            assertThat(stock.acceptedQuantity()).isZero();
            assertThat(stock.damagedQuantity()).isEqualByComparingTo("2");
        });
        assertThat(result.targetLots()).singleElement().satisfies(lot ->
                assertThat(lot.key().disposition()).isEqualTo("damaged"));
        assertThat(result.targetBalances()).singleElement()
                .satisfies(balance -> {
                    assertThat(balance.availableQuantity()).isZero();
                    assertThat(balance.quarantineQuantity())
                            .isEqualByComparingTo("2");
                });
        assertThat(result.discrepancyCases()).isEmpty();
        assertThat(result.shipmentDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.fulfillmentDelta())
                        .isEqualByComparingTo("2"));
        assertThat(result.transferDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.fulfillmentDelta())
                        .isEqualByComparingTo("2"));
        assertThat(result.lifecycle().action())
                .isEqualTo("return_receipt_completed");
    }

    @Test
    @DisplayName("退回实收不是新差异且只有意外短缺生成差异事项")
    void shouldCreateOnlyUnexpectedShortageDiscrepancy()
    {
        var source = returnAllocation("lot", "2");
        var result = prepareReturn(returnBoundary(source, Map.of(), true),
                returnCommand(true, "1", "1", "0", List.of(),
                        List.of()));

        assertThat(result.receipt().receiptStatus()).isEqualTo("discrepancy");
        assertThat(result.discrepancyCases()).singleElement()
                .satisfies(value -> {
                    assertThat(value.discrepancyType())
                            .isEqualTo("shortage");
                    assertThat(value.discrepancyQuantity())
                            .isEqualByComparingTo("1");
                });
        assertThat(result.shipmentDetails()).singleElement()
                .satisfies(detail -> assertThat(detail.fulfillmentDelta())
                        .isEqualByComparingTo("1"));
        assertThat(result.lifecycle().action())
                .isEqualTo("return_receipt_discrepancy");
    }

    @Test
    @DisplayName("退回序列号分别进入隔离与短缺在途处置")
    void shouldConserveReturnSerialDispositions()
    {
        var source = returnAllocation("serial", "2");
        var serials = Map.of(
                1001L, serial(1001L, 81L, 501L, 801L),
                1002L, serial(1002L, 81L, 501L, 801L));
        var result = prepareReturn(returnBoundary(source, serials, true),
                returnCommand(true, "1", "1", "0", List.of(1001L),
                        List.of(1002L)));

        assertThat(result.serials()).extracting(
                InvTransferShipmentReceiptPreparedMutation.Serial
                        ::disposition)
                .containsExactly("damaged", "shortage");
        assertThat(result.serials().get(0).statusAfter())
                .isEqualTo("quarantine");
        assertThat(result.serials().get(1).statusAfter())
                .isEqualTo("shipped");
    }

    @Test
    @DisplayName("普通来源分配或非隔离来源批次不能进入专属退回写计划")
    void shouldRejectOrdinaryAllocationOrNonQuarantineSource()
    {
        var ordinary = allocation(81L, 71L, 401L, 501L, 801L, "lot",
                "1");
        assertThatThrownBy(() -> prepareReturn(
                returnBoundary(ordinary, Map.of(), true),
                returnCommand(true, "1", "0", "0", List.of(),
                        List.of())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源分配收货数量或跟踪策略不守恒");

        var fixed = returnAllocation("lot", "1");
        assertThatThrownBy(() -> prepareReturn(
                returnBoundary(fixed, Map.of(), false),
                returnCommand(true, "1", "0", "0", List.of(),
                        List.of())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("固定隔离来源批次");
    }

    @Test
    @DisplayName("退回来源追踪策略缺失时按业务校验失败关闭")
    void shouldFailClosedWhenReturnTrackingPolicyIsMissing()
    {
        var source = returnAllocation(null, "1");

        assertThatThrownBy(() -> prepareReturn(
                returnBoundary(source, Map.of(), true),
                returnCommand(true, "1", "0", "0", List.of(),
                        List.of())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源分配收货数量或跟踪策略不守恒");
    }

    private static InvTransferShipmentReceiptPreparedMutation prepare(
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command)
    {
        return InvTransferShipmentReceiptMutationPlanner.prepare(
                "receipt-request-1", boundary, command, 77L, "receiver",
                "receiver", Instant.parse("2026-08-02T10:05:00Z"));
    }

    private static InvTransferShipmentReceiptPreparedMutation prepareReturn(
            InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary boundary,
            InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand command)
    {
        return InvTransferShipmentReceiptMutationPlanner.prepareReturn(
                "return-receipt-request-1", boundary, command, 77L,
                "receiver", "receiver",
                Instant.parse("2026-08-03T00:05:00Z"));
    }

    private static InvTransferShipmentReceiptLockedBoundary boundary(
            Map<Long, InvTransferShipmentReceiptLockedBoundary.Allocation>
                    allocations,
            Map<InvShipmentPlanningItemKey,
                    InvTransferShipmentReceiptLockedBoundary.TargetStock>
                    targetStocks,
            Map<InvTransferReceiptDerivedLotKey,
                    InvTransferShipmentReceiptLockedBoundary.TargetLot>
                    targetLots,
            Map<InvTransferReceiptBalanceKey,
                    InvTransferShipmentReceiptLockedBoundary.TargetBalance>
                    targetBalances,
            Map<Long, InvTransferShipmentReceiptLockedBoundary.Serial>
                    serials)
    {
        InvTransferShipmentReceiptLockedBoundary.Header header =
                new InvTransferShipmentReceiptLockedBoundary.Header(91L,
                        900L, 301L, 301L, 302L, "SHP-91",
                        "b".repeat(64), 11L, "source-reconcile-1",
                        "target-reconcile-1",
                        InvStatusConstants.PENDING_RECEIVE,
                        InvStatusConstants.DELIVERED, 4L);
        InvWarehouseStockMode mode = new InvWarehouseStockMode();
        mode.setWarehouseId(302L);
        mode.setLastReconcileBatch("target-reconcile-1");
        InvTransferShipmentReceiptPlanComposer.Composition composition =
                new InvTransferShipmentReceiptPlanComposer.Composition(PLAN,
                        mode, List.of(), List.of(), List.of(), true,
                        List.of());
        Map<Long, InvTransferShipmentReceiptLockedBoundary.SourceLot>
                sourceLots = new HashMap<>();
        Map<Long, InvTransferShipmentReceiptLockedBoundary.TransferDetail>
                transferDetails = new HashMap<>();
        allocations.values().forEach(value -> sourceLots.put(
                value.sourceLotId(), sourceLot(value.sourceLotId(),
                        value.itemType(), value.itemId(),
                        value.productId())));
        allocations.values().forEach(value -> transferDetails.putIfAbsent(
                value.transferDetailId(),
                new InvTransferShipmentReceiptLockedBoundary.TransferDetail(
                        value.transferDetailId(), value.itemType(),
                        value.itemId(), value.productId(),
                        value.transferDetailDeliveredQuantity(),
                        value.transferDetailDeliveredQuantity(),
                        value.transferDetailReceivedQuantity())));
        Map<Long, InvTransferShipmentReceiptLockedBoundary.Location>
                locations = Map.of(
                        901L, new InvTransferShipmentReceiptLockedBoundary
                                .Location(901L, 302L, "ST-01", "正常库位",
                                        "storage"),
                        902L, new InvTransferShipmentReceiptLockedBoundary
                                .Location(902L, 302L, "QA-01", "隔离库位",
                                        "quarantine"));
        return new InvTransferShipmentReceiptLockedBoundary(header,
                composition, allocations, transferDetails, targetStocks,
                sourceLots, targetLots, locations, targetBalances, serials);
    }

    private static InvTransferShipmentReceiptLockedBoundary.Allocation
            allocation(Long allocationId, Long shipmentDetailId,
                    Long transferDetailId, Long lotId, Long locationId,
                    String tracking, String quantity)
    {
        BigDecimal value = new BigDecimal(quantity);
        return new InvTransferShipmentReceiptLockedBoundary.Allocation(
                allocationId, shipmentDetailId, transferDetailId, "product",
                1001L, 1001L, "FEFO", tracking, value,
                600L + allocationId, lotId,
                locationId, new BigDecimal("10"),
                value.multiply(new BigDecimal("10")).setScale(6),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L,
                value, BigDecimal.ZERO, value, BigDecimal.ZERO);
    }

    private static InvTransferShipmentReceiptLockedBoundary.Allocation
            returnAllocation(String tracking, String quantity)
    {
        BigDecimal value = new BigDecimal(quantity);
        return new InvTransferShipmentReceiptLockedBoundary.Allocation(
                81L, 71L, 401L, "product", 1001L, 1001L,
                "fixed_return", tracking, value, 681L, 501L, 801L,
                new BigDecimal("10"),
                value.multiply(new BigDecimal("10")).setScale(6),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L,
                value, BigDecimal.ZERO, value, BigDecimal.ZERO);
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary
            returnBoundary(
                    InvTransferShipmentReceiptLockedBoundary.Allocation source,
                    Map<Long, InvTransferShipmentReceiptLockedBoundary.Serial>
                            serials,
                    boolean quarantineSource)
    {
        var base = boundary(Map.of(source.allocationId(), source), Map.of(),
                Map.of(), Map.of(), serials);
        var lot = new InvTransferShipmentReceiptLockedBoundary.SourceLot(
                source.sourceLotId(), "LOT-" + source.sourceLotId(),
                source.itemType(), source.itemId(), source.productId(), 301L,
                "SUP-" + source.sourceLotId(), Date.from(Instant.parse(
                        "2026-01-01T00:00:00Z")), Date.from(Instant.parse(
                                "2027-01-01T00:00:00Z")),
                quarantineSource ? "quarantine" : "passed", "active");
        var locked = new InvTransferShipmentReceiptLockedBoundary(
                base.header(), base.composition(), base.allocations(),
                base.transferDetails(), base.targetStocks(),
                Map.of(source.sourceLotId(), lot), base.targetLots(),
                base.targetLocations(), base.targetBalances(),
                base.serials());
        return new InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary(
                locked,
                new InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan(
                        PLAN, new InvWarehouseStockMode(), List.of(), List.of(),
                        true, List.of()));
    }

    private static InvTransferShipmentReceiptLockedBoundary.SourceLot
            sourceLot(Long lotId, String itemType, Long itemId,
                    Long productId)
    {
        return new InvTransferShipmentReceiptLockedBoundary.SourceLot(lotId,
                "LOT-" + lotId, itemType, itemId, productId, 301L,
                "SUP-" + lotId, Date.from(Instant.parse(
                        "2026-01-01T00:00:00Z")), Date.from(Instant.parse(
                                "2027-01-01T00:00:00Z")), "passed",
                "active");
    }

    private static InvTransferShipmentReceiptLockedBoundary.Serial serial(
            Long serialId, Long allocationId, Long lotId, Long locationId)
    {
        return new InvTransferShipmentReceiptLockedBoundary.Serial(
                2000L + serialId, allocationId, 91L, serialId,
                "SN-" + serialId, "product", 1001L, 301L,
                600L + allocationId, lotId, locationId, "shipped");
    }

    private static InvTransferShipmentReceiptValidatedCommand command(
            boolean finalize,
            List<InvTransferShipmentReceiptValidatedCommand.Allocation>
                    allocations,
            String accepted, String damaged, String shortage,
            String remaining)
    {
        return new InvTransferShipmentReceiptValidatedCommand(FINGERPRINT,
                PLAN, ARRIVED, finalize, allocations,
                new BigDecimal(accepted), new BigDecimal(damaged),
                new BigDecimal(shortage), new BigDecimal(remaining),
                "到货收货");
    }

    private static InvTransferShipmentReceiptValidatedCommand.Allocation
            commandAllocation(Long allocationId, String accepted,
                    String damaged, String shortage,
                    List<Long> acceptedSerials, List<Long> damagedSerials,
                    List<Long> shortageSerials)
    {
        BigDecimal acceptedQuantity = new BigDecimal(accepted);
        BigDecimal damagedQuantity = new BigDecimal(damaged);
        BigDecimal shortageQuantity = new BigDecimal(shortage);
        return new InvTransferShipmentReceiptValidatedCommand.Allocation(
                allocationId,
                acceptedQuantity.signum() > 0 ? 901L : null,
                acceptedQuantity,
                damagedQuantity.signum() > 0 ? 902L : null,
                damagedQuantity, shortageQuantity, acceptedSerials,
                damagedSerials, shortageSerials,
                damagedQuantity.signum() > 0
                        || shortageQuantity.signum() > 0 ? "差异说明" : null,
                damagedQuantity.signum() > 0
                        || shortageQuantity.signum() > 0 ? "attachment-1"
                                : null);
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
            returnCommand(boolean finalize, String returned, String shortage,
                    String remaining, List<Long> returnedSerials,
                    List<Long> shortageSerials)
    {
        BigDecimal returnedQuantity = new BigDecimal(returned);
        BigDecimal shortageQuantity = new BigDecimal(shortage);
        var allocation = new
                InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                        .Allocation(81L,
                                returnedQuantity.signum() > 0 ? 902L : null,
                                returnedQuantity, shortageQuantity,
                                returnedSerials, shortageSerials,
                                shortageQuantity.signum() > 0
                                        ? "退回意外短缺" : null,
                                shortageQuantity.signum() > 0
                                        ? "attachment-return-1" : null);
        return new InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand(
                FINGERPRINT, PLAN, ARRIVED, finalize, List.of(allocation),
                returnedQuantity, shortageQuantity,
                new BigDecimal(remaining), "退回隔离收货");
    }
}
