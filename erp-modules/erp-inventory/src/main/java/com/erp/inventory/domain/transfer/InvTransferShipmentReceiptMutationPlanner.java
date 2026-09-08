package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

/** Pure construction and conservation checks for one atomic receipt write. */
public final class InvTransferShipmentReceiptMutationPlanner
{
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Comparator<InvShipmentPlanningItemKey> ITEM_ORDER =
            Comparator.comparing(InvShipmentPlanningItemKey::itemType)
                    .thenComparing(InvShipmentPlanningItemKey::itemId);
    private static final Comparator<InvTransferReceiptDerivedLotKey>
            LOT_ORDER = Comparator
                    .comparing(InvTransferReceiptDerivedLotKey::warehouseId)
                    .thenComparing(InvTransferReceiptDerivedLotKey::itemType)
                    .thenComparing(InvTransferReceiptDerivedLotKey::itemId)
                    .thenComparing(
                            InvTransferReceiptDerivedLotKey::sourceLotId)
                    .thenComparing(
                            InvTransferReceiptDerivedLotKey::disposition);
    private static final Comparator<InvTransferShipmentReceiptPreparedMutation
            .BalanceKey> BALANCE_ORDER = Comparator
                    .comparing((InvTransferShipmentReceiptPreparedMutation
                            .BalanceKey value) -> value.lotKey(), LOT_ORDER)
                    .thenComparing(InvTransferShipmentReceiptPreparedMutation
                            .BalanceKey::locationId);

    private InvTransferShipmentReceiptMutationPlanner()
    {
    }

    public static InvTransferShipmentReceiptPreparedMutation prepare(
            String requestId,
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command,
            Long operatorUserId, String operatorName, String createBy,
            Instant createdTime)
    {
        return prepare(requestId, boundary, command,
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .ORDINARY,
                boundary == null || boundary.composition() == null ? null
                        : boundary.composition().receiptPlanVersion(),
                boundary != null && boundary.composition() != null
                        && boundary.composition().canCreateReceipt(),
                operatorUserId, operatorName, createBy, createdTime);
    }

    public static InvTransferShipmentReceiptPreparedMutation prepareReturn(
            String requestId,
            InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary boundary,
            InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand command,
            Long operatorUserId, String operatorName, String createBy,
            Instant createdTime)
    {
        InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan =
                boundary == null ? null : boundary.returnPlan();
        return prepare(requestId,
                boundary == null ? null : boundary.lockedFacts(),
                toCommonCommand(command),
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .FIXED_RETURN,
                plan == null ? null : plan.planVersion(),
                plan != null && plan.canCreateReceipt(), operatorUserId,
                operatorName, createBy, createdTime);
    }

    private static InvTransferShipmentReceiptPreparedMutation prepare(
            String requestId,
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                    semantic,
            String trustedPlanVersion, boolean executable,
            Long operatorUserId, String operatorName, String createBy,
            Instant createdTime)
    {
        requireInputs(requestId, boundary, command, operatorUserId,
                operatorName, createBy, createdTime, semantic,
                trustedPlanVersion, executable);
        InvTransferShipmentReceiptLockedBoundary.Header header =
                boundary.header();

        List<InvTransferShipmentReceiptValidatedCommand.Allocation>
                requested = new ArrayList<>(command.allocations());
        requested.sort(Comparator.comparing(
                InvTransferShipmentReceiptValidatedCommand.Allocation
                        ::shipmentAllocationId));

        Map<InvShipmentPlanningItemKey, StockAccumulator> stocks =
                new TreeMap<>(ITEM_ORDER);
        Map<InvTransferReceiptDerivedLotKey, LotAccumulator> lots =
                new TreeMap<>(LOT_ORDER);
        Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                BalanceAccumulator> balances = new TreeMap<>(BALANCE_ORDER);
        Map<Long, DetailAccumulator> shipmentDetails = new TreeMap<>();
        Map<Long, DetailAccumulator> transferDetails = new TreeMap<>();
        List<InvTransferShipmentReceiptPreparedMutation.Allocation>
                allocationMutations = new ArrayList<>();
        List<InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase>
                discrepancyCases = new ArrayList<>();
        List<InvTransferShipmentReceiptPreparedMutation.Serial>
                serialMutations = new ArrayList<>();
        List<InvTransferShipmentReceiptPreparedMutation.Ledger>
                ledgerMutations = new ArrayList<>();
        Set<Long> consumedSerialIds = new HashSet<>();
        BigDecimal acceptedTotal = ZERO;
        BigDecimal damagedTotal = ZERO;
        BigDecimal shortageTotal = ZERO;

        for (InvTransferShipmentReceiptValidatedCommand.Allocation value
                : requested)
        {
            InvTransferShipmentReceiptLockedBoundary.Allocation source =
                    boundary.allocations().get(
                            value.shipmentAllocationId());
            requireAllocation(source, value, header, semantic);
            InvShipmentPlanningItemKey itemKey =
                    new InvShipmentPlanningItemKey(source.itemType(),
                            source.itemId());
            BigDecimal acceptedCost = cost(source.costPrice(),
                    value.acceptedQuantity());
            BigDecimal damagedCost = cost(source.costPrice(),
                    value.damagedQuantity());

            if (value.acceptedQuantity().signum() > 0
                    || value.damagedQuantity().signum() > 0)
            {
                StockAccumulator stock = stocks.computeIfAbsent(itemKey,
                        ignored -> new StockAccumulator(itemKey,
                                source.productId()));
                stock.add(value.acceptedQuantity(), value.damagedQuantity(),
                        acceptedCost.add(damagedCost));
            }

            InvTransferReceiptDerivedLotKey acceptedLotKey = lotKey(
                    header, source, "accepted", value.acceptedQuantity());
            InvTransferReceiptDerivedLotKey damagedLotKey = lotKey(header,
                    source, "damaged", value.damagedQuantity());
            BalanceStep acceptedBalance = BalanceStep.none();
            BalanceStep damagedBalance = BalanceStep.none();
            if (acceptedLotKey != null)
            {
                requireLocation(boundary, value.acceptedLocationId(),
                        header.targetWarehouseId(), "storage");
                acceptedBalance = addLotAndBalance(boundary, lots, balances,
                        source,
                        acceptedLotKey, value.acceptedLocationId(),
                        value.acceptedQuantity(), ZERO, acceptedCost,
                        semantic);
            }
            if (damagedLotKey != null)
            {
                requireLocation(boundary, value.quarantineLocationId(),
                        header.targetWarehouseId(), "quarantine");
                damagedBalance = addLotAndBalance(boundary, lots, balances,
                        source,
                        damagedLotKey, value.quarantineLocationId(), ZERO,
                        value.damagedQuantity(), damagedCost, semantic);
            }

            addSerials(serialMutations, consumedSerialIds, boundary,
                    source, value.acceptedSerialIds(), "accepted",
                    acceptedLotKey, value.acceptedLocationId(), "available");
            addSerials(serialMutations, consumedSerialIds, boundary,
                    source, value.damagedSerialIds(), "damaged",
                    damagedLotKey, value.quarantineLocationId(),
                    "quarantine");
            addSerials(serialMutations, consumedSerialIds, boundary,
                    source, value.shortageSerialIds(), "shortage", null,
                    null, "shipped");
            addLedger(ledgerMutations, requestId, source, "accepted",
                    acceptedLotKey, value.acceptedLocationId(),
                    value.acceptedQuantity(), acceptedBalance,
                    acceptedCost);
            addLedger(ledgerMutations, requestId, source, "damaged",
                    damagedLotKey, value.quarantineLocationId(),
                    value.damagedQuantity(), damagedBalance, damagedCost);

            BigDecimal fulfillment = fulfillment(value, semantic);
            if (fulfillment.signum() > 0)
            {
                shipmentDetails.compute(source.shipmentDetailId(),
                        (ignored, current) -> addDetail(current,
                                source.shipmentDetailId(),
                                source.shipmentDetailReceivedQuantity(),
                                source.shipmentDetailShippedQuantity(),
                                fulfillment));
                transferDetails.compute(source.transferDetailId(),
                        (ignored, current) -> addDetail(current,
                                source.transferDetailId(),
                                source.transferDetailReceivedQuantity(),
                                source.transferDetailDeliveredQuantity(),
                                fulfillment));
            }

            allocationMutations.add(
                    new InvTransferShipmentReceiptPreparedMutation
                            .Allocation(source.allocationId(),
                                    source.shipmentDetailId(),
                                    source.transferDetailId(), itemKey,
                                    source.productId(),
                                    source.trackingPolicy(),
                                    source.sourceBalanceId(),
                                    source.sourceLotId(),
                                    source.sourceLocationId(),
                                    source.costPrice(),
                                    value.acceptedQuantity(),
                                    value.damagedQuantity(),
                                    value.shortageQuantity(), acceptedCost,
                                    damagedCost, source.receiptVersion(),
                                    value.acceptedLocationId(),
                                    acceptedLotKey,
                                    acceptedBalance.quantityBefore(),
                                    acceptedBalance.quantityAfter(),
                                    acceptedBalance.versionBefore(),
                                    acceptedBalance.versionAfter(),
                                    value.quarantineLocationId(),
                                    damagedLotKey,
                                    damagedBalance.quantityBefore(),
                                    damagedBalance.quantityAfter(),
                                    damagedBalance.versionBefore(),
                                    damagedBalance.versionAfter(),
                                    value.discrepancyNote(),
                                    value.attachmentRefs()));
            if (semantic == InvTransferShipmentReceiptPreparedMutation
                    .ReceiptSemantic.ORDINARY)
            {
                addDiscrepancyCase(discrepancyCases,
                        command.receiptPlanVersion(), source, "damaged",
                        value.damagedQuantity(), damagedCost,
                        value.discrepancyNote(), value.attachmentRefs());
            }
            addDiscrepancyCase(discrepancyCases,
                    command.receiptPlanVersion(), source, "shortage",
                    value.shortageQuantity(),
                    cost(source.costPrice(), value.shortageQuantity()),
                    value.discrepancyNote(), value.attachmentRefs());
            acceptedTotal = acceptedTotal.add(value.acceptedQuantity());
            damagedTotal = damagedTotal.add(value.damagedQuantity());
            shortageTotal = shortageTotal.add(value.shortageQuantity());
        }

        requireTotals(boundary, command, acceptedTotal, damagedTotal,
                shortageTotal);
        String shipmentStatus = shipmentLifecycleStatus(command, semantic);
        String transferStatus = transferLifecycleStatus(boundary, command,
                transferDetails, semantic);
        String receiptStatus = InvStatusConstants.DISCREPANCY.equals(
                shipmentStatus) ? "discrepancy"
                        : InvStatusConstants.RECEIVED.equals(shipmentStatus)
                                ? "completed" : "partial";
        InvTransferShipmentReceiptPreparedMutation.Receipt receipt =
                new InvTransferShipmentReceiptPreparedMutation.Receipt(
                        requestId, command.requestFingerprint(),
                        receiptNo(requestId, semantic), semantic,
                        header.shipmentId(),
                        header.transferId(), command.receiptPlanVersion(),
                        header.shipmentPlanVersion(),
                        header.sourceReconcileBatch(),
                        header.targetWarehouseId(),
                        header.targetReconcileBatch(), command.arrivedTime(),
                        createdTime, command.finalizeShipment(), acceptedTotal,
                        damagedTotal, shortageTotal,
                        command.remainingQuantityAfter(), receiptStatus,
                        operatorUserId, operatorName, createBy,
                        command.remark());
        InvTransferShipmentReceiptPreparedMutation.Lifecycle lifecycle =
                new InvTransferShipmentReceiptPreparedMutation.Lifecycle(
                        header.shipmentStatus(), header.transferStatus(),
                        header.transferVersion(), shipmentStatus,
                        transferStatus,
                        semantic == InvTransferShipmentReceiptPreparedMutation
                                .ReceiptSemantic.FIXED_RETURN
                                        ? "return_receipt_" + receiptStatus
                                        : "receipt_" + receiptStatus);

        serialMutations.sort(Comparator.comparing(
                InvTransferShipmentReceiptPreparedMutation.Serial
                        ::serialId));
        List<InvTransferShipmentReceiptPreparedMutation.TargetStock>
                stockMutations = stockMutations(stocks, boundary);
        return new InvTransferShipmentReceiptPreparedMutation(receipt,
                stockMutations, stockLogs(stockMutations), lotMutations(lots),
                balanceMutations(balances), allocationMutations,
                discrepancyCases, serialMutations, ledgerMutations,
                detailMutations(shipmentDetails),
                detailMutations(transferDetails), lifecycle);
    }

    private static InvTransferShipmentReceiptValidatedCommand toCommonCommand(
            InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand command)
    {
        if (command == null)
        {
            return null;
        }
        List<InvTransferShipmentReceiptValidatedCommand.Allocation>
                allocations = command.allocations().stream().map(value ->
                        new InvTransferShipmentReceiptValidatedCommand
                                .Allocation(value.shipmentAllocationId(), null,
                                        ZERO, value.quarantineLocationId(),
                                        value.returnedQuantity(),
                                        value.shortageQuantity(), List.of(),
                                        value.returnedSerialIds(),
                                        value.shortageSerialIds(),
                                        value.discrepancyNote(),
                                        value.attachmentRefs()))
                        .toList();
        return new InvTransferShipmentReceiptValidatedCommand(
                command.requestFingerprint(),
                command.returnReceiptPlanVersion(), command.arrivedTime(),
                command.finalizeShipment(), allocations, ZERO,
                command.returnedQuantity(), command.shortageQuantity(),
                command.remainingQuantityAfter(), command.remark());
    }

    private static BigDecimal fulfillment(
            InvTransferShipmentReceiptValidatedCommand.Allocation allocation,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        return semantic == InvTransferShipmentReceiptPreparedMutation
                .ReceiptSemantic.FIXED_RETURN
                        ? allocation.damagedQuantity()
                        : allocation.acceptedQuantity();
    }

    private static void addDiscrepancyCase(
            List<InvTransferShipmentReceiptPreparedMutation.DiscrepancyCase>
                    cases,
            String receiptPlanVersion,
            InvTransferShipmentReceiptLockedBoundary.Allocation source,
            String discrepancyType, BigDecimal quantity,
            BigDecimal discrepancyAmount, String discrepancyNote,
            String attachmentRefs)
    {
        if (quantity.signum() == 0)
        {
            return;
        }
        String fingerprint = InvTransferReceiptDiscrepancyFacts.fingerprint(
                receiptPlanVersion, source.allocationId(), discrepancyType,
                quantity, source.costPrice(), discrepancyAmount,
                discrepancyNote, attachmentRefs);
        cases.add(new InvTransferShipmentReceiptPreparedMutation
                .DiscrepancyCase(source.allocationId(), discrepancyType,
                        quantity, source.costPrice(), discrepancyAmount,
                        fingerprint, discrepancyNote, attachmentRefs));
    }

    private static void requireInputs(String requestId,
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command,
            Long operatorUserId, String operatorName, String createBy,
            Instant createdTime,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                    semantic,
            String trustedPlanVersion, boolean executable)
    {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128
                || boundary == null || command == null
                || command.allocations() == null
                || command.allocations().isEmpty()
                || operatorUserId == null || operatorUserId <= 0
                || operatorName == null || operatorName.isBlank()
                || createBy == null || createBy.isBlank()
                || createdTime == null
                || semantic == null
                || boundary.header() == null
                || boundary.transferDetails().isEmpty()
                || !executable
                || command.requestFingerprint() == null
                || !command.requestFingerprint().matches("[a-f0-9]{64}")
                || command.receiptPlanVersion() == null
                || !command.receiptPlanVersion().matches("[a-f0-9]{64}")
                || !Objects.equals(command.receiptPlanVersion(),
                        trustedPlanVersion)
                || boundary.header().shipmentPlanVersion() == null
                || !boundary.header().shipmentPlanVersion()
                        .matches("[a-f0-9]{64}")
                || blank(boundary.header().sourceReconcileBatch())
                || blank(boundary.header().targetReconcileBatch())
                || boundary.header().transferVersion() == null
                || boundary.header().transferVersion() < 0)
        {
            throw new ServiceException("原子收货变更缺少可信输入");
        }
    }

    private static void requireAllocation(
            InvTransferShipmentReceiptLockedBoundary.Allocation source,
            InvTransferShipmentReceiptValidatedCommand.Allocation requested,
            InvTransferShipmentReceiptLockedBoundary.Header header,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        if (source == null || source.allocationId() == null
                || source.receiptVersion() == null
                || source.receiptVersion() < 0
                || blank(source.itemType()) || source.itemId() == null
                || source.itemId() <= 0 || source.sourceBalanceId() == null
                || source.sourceBalanceId() <= 0
                || source.sourceLotId() == null
                || source.sourceLotId() <= 0
                || source.sourceLocationId() == null
                || source.sourceLocationId() <= 0
                || !quantity(source.allocatedQuantity(), true)
                || !quantity(source.acceptedReceivedQuantity(), false)
                || !quantity(source.damagedReceivedQuantity(), false)
                || !quantity(source.shortageReportedQuantity(), false)
                || !quantity(source.costPrice(), false)
                || source.costPrice().scale() > 6)
        {
            throw new ServiceException("来源分配快照不可核验");
        }
        BigDecimal before = source.acceptedReceivedQuantity()
                .add(source.damagedReceivedQuantity())
                .add(source.shortageReportedQuantity());
        BigDecimal delta = requested.acceptedQuantity()
                .add(requested.damagedQuantity())
                .add(requested.shortageQuantity());
        boolean fixedReturn = semantic ==
                InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic
                        .FIXED_RETURN;
        if (delta.signum() <= 0
                || before.add(delta).compareTo(
                        source.allocatedQuantity()) > 0
                || source.trackingPolicy() == null
                || !Set.of("lot", "serial").contains(
                        source.trackingPolicy())
                || header.sourceWarehouseId() == null
                || header.sourceWarehouseId() <= 0
                || (fixedReturn && (!"fixed_return".equals(
                        source.allocationPolicy())
                        || source.acceptedReceivedQuantity().signum() != 0
                        || requested.acceptedQuantity().signum() != 0))
                || (!fixedReturn && "fixed_return".equals(
                        source.allocationPolicy())))
        {
            throw new ServiceException("来源分配收货数量或跟踪策略不守恒");
        }
    }

    private static InvTransferReceiptDerivedLotKey lotKey(
            InvTransferShipmentReceiptLockedBoundary.Header header,
            InvTransferShipmentReceiptLockedBoundary.Allocation source,
            String disposition, BigDecimal quantity)
    {
        return quantity.signum() == 0 ? null
                : new InvTransferReceiptDerivedLotKey(
                        header.targetWarehouseId(), source.itemType(),
                        source.itemId(), source.sourceLotId(), disposition);
    }

    private static void requireLocation(
            InvTransferShipmentReceiptLockedBoundary boundary,
            Long locationId, Long warehouseId, String type)
    {
        InvTransferShipmentReceiptLockedBoundary.Location location =
                boundary.targetLocations().get(locationId);
        if (location == null || !Objects.equals(warehouseId,
                location.warehouseId()) || !type.equals(location.locationType()))
        {
            throw new ServiceException("目标收货库位与处置类型不一致");
        }
    }

    private static BalanceStep addLotAndBalance(
            InvTransferShipmentReceiptLockedBoundary boundary,
            Map<InvTransferReceiptDerivedLotKey, LotAccumulator> lots,
            Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                    BalanceAccumulator> balances,
            InvTransferShipmentReceiptLockedBoundary.Allocation source,
            InvTransferReceiptDerivedLotKey lotKey, Long locationId,
            BigDecimal available, BigDecimal quarantine,
            BigDecimal incomingCost,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        InvTransferShipmentReceiptLockedBoundary.SourceLot sourceLot =
                boundary.sourceLots().get(source.sourceLotId());
        if (sourceLot == null
                || !Objects.equals(source.itemType(), sourceLot.itemType())
                || !Objects.equals(source.itemId(), sourceLot.itemId())
                || !Objects.equals(boundary.header().sourceWarehouseId(),
                        sourceLot.warehouseId())
                || blank(sourceLot.lotNo()))
        {
            throw new ServiceException("来源批次快照与收货分配不一致");
        }
        if (semantic == InvTransferShipmentReceiptPreparedMutation
                .ReceiptSemantic.FIXED_RETURN
                && (!"quarantine".equalsIgnoreCase(sourceLot.qcStatus())
                        || !"active".equalsIgnoreCase(
                                sourceLot.lotStatus())))
        {
            throw new ServiceException("专属退回收货要求固定隔离来源批次");
        }
        lots.computeIfAbsent(lotKey, ignored -> new LotAccumulator(lotKey,
                source.productId(), sourceLot,
                boundary.targetLots().get(lotKey)));

        InvTransferShipmentReceiptPreparedMutation.BalanceKey balanceKey =
                new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                        lotKey, locationId);
        InvTransferShipmentReceiptLockedBoundary.TargetLot existingLot =
                boundary.targetLots().get(lotKey);
        InvTransferShipmentReceiptLockedBoundary.TargetBalance
                existingBalance = existingLot == null ? null
                        : boundary.targetBalances().get(
                                new InvTransferReceiptBalanceKey(
                                        existingLot.lotId(), locationId));
        requireBalance(existingBalance);
        BalanceAccumulator balance = balances.computeIfAbsent(balanceKey,
                ignored -> new BalanceAccumulator(balanceKey,
                        existingBalance));
        return balance.add(available, quarantine, incomingCost);
    }

    private static void addSerials(
            List<InvTransferShipmentReceiptPreparedMutation.Serial> output,
            Set<Long> consumed,
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptLockedBoundary.Allocation allocation,
            List<Long> serialIds, String disposition,
            InvTransferReceiptDerivedLotKey targetLotKey,
            Long targetLocationId, String statusAfter)
    {
        for (Long serialId : serialIds)
        {
            InvTransferShipmentReceiptLockedBoundary.Serial serial =
                    boundary.serials().get(serialId);
            if (serial == null || !consumed.add(serialId)
                    || !Objects.equals(allocation.allocationId(),
                            serial.allocationId())
                    || !Objects.equals(boundary.header().shipmentId(),
                            serial.shipmentId())
                    || !Objects.equals(allocation.itemType(),
                            serial.itemType())
                    || !Objects.equals(allocation.itemId(), serial.itemId())
                    || !Objects.equals(boundary.header().sourceWarehouseId(),
                            serial.sourceWarehouseId())
                    || !Objects.equals(allocation.sourceBalanceId(),
                            serial.sourceBalanceId())
                    || !Objects.equals(allocation.sourceLotId(),
                            serial.sourceLotId())
                    || !Objects.equals(allocation.sourceLocationId(),
                            serial.sourceLocationId())
                    || !"shipped".equals(serial.currentStatus()))
            {
                throw new ServiceException("序列号不再位于锁定来源分配");
            }
            output.add(new InvTransferShipmentReceiptPreparedMutation.Serial(
                    serial.shipmentSerialId(), allocation.allocationId(),
                    serial.serialId(), serial.serialNo(), disposition,
                    serial.sourceWarehouseId(), serial.sourceBalanceId(),
                    serial.sourceLotId(), serial.sourceLocationId(),
                    targetLotKey, targetLocationId, "shipped", statusAfter));
        }
    }

    private static void addLedger(
            List<InvTransferShipmentReceiptPreparedMutation.Ledger> output,
            String requestId,
            InvTransferShipmentReceiptLockedBoundary.Allocation source,
            String disposition, InvTransferReceiptDerivedLotKey lotKey,
            Long locationId, BigDecimal quantity, BalanceStep balance,
            BigDecimal totalCost)
    {
        if (quantity.signum() == 0)
        {
            return;
        }
        output.add(new InvTransferShipmentReceiptPreparedMutation.Ledger(
                "RCL-" + sha256(requestId + "|" + source.allocationId()
                        + "|" + disposition),
                source.allocationId(), disposition,
                new InvShipmentPlanningItemKey(source.itemType(),
                        source.itemId()), source.productId(),
                new InvTransferShipmentReceiptPreparedMutation.BalanceKey(
                        lotKey, locationId), quantity,
                balance.quantityBefore(), balance.quantityAfter(),
                source.costPrice(), totalCost));
    }

    private static DetailAccumulator addDetail(DetailAccumulator current,
            Long detailId, BigDecimal expected, BigDecimal maximum,
            BigDecimal delta)
    {
        if (!quantity(expected, false) || !quantity(maximum, false)
                || expected.compareTo(maximum) > 0)
        {
            throw new ServiceException("收货明细累计快照不可核验");
        }
        DetailAccumulator result = current == null
                ? new DetailAccumulator(detailId, expected, maximum)
                : current;
        if (!same(result.expected, expected)
                || !same(result.maximum, maximum))
        {
            throw new ServiceException("同一收货明细锁定快照不一致");
        }
        result.delta = result.delta.add(delta);
        if (result.expected.add(result.delta).compareTo(result.maximum) > 0)
        {
            throw new ServiceException("收货履约累计超过已发或调拨数量");
        }
        return result;
    }

    private static void requireTotals(
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command,
            BigDecimal accepted, BigDecimal damaged, BigDecimal shortage)
    {
        BigDecimal remainingBefore = ZERO;
        for (InvTransferShipmentReceiptLockedBoundary.Allocation source
                : boundary.allocations().values())
        {
            remainingBefore = remainingBefore.add(source.allocatedQuantity())
                    .subtract(source.acceptedReceivedQuantity())
                    .subtract(source.damagedReceivedQuantity())
                    .subtract(source.shortageReportedQuantity());
        }
        BigDecimal classified = accepted.add(damaged).add(shortage);
        if (!same(accepted, command.acceptedQuantity())
                || !same(damaged, command.damagedQuantity())
                || !same(shortage, command.shortageQuantity())
                || !same(remainingBefore.subtract(classified),
                        command.remainingQuantityAfter())
                || command.remainingQuantityAfter().signum() < 0
                || command.finalizeShipment()
                        != (command.remainingQuantityAfter().signum() == 0))
        {
            throw new ServiceException("收货命令分类总量或剩余量不守恒");
        }
    }

    private static String shipmentLifecycleStatus(
            InvTransferShipmentReceiptValidatedCommand command,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        boolean discrepancy = command.shortageQuantity().signum() > 0
                || (semantic == InvTransferShipmentReceiptPreparedMutation
                        .ReceiptSemantic.ORDINARY
                        && command.damagedQuantity().signum() > 0);
        if (discrepancy)
        {
            return InvStatusConstants.DISCREPANCY;
        }
        return command.remainingQuantityAfter().signum() == 0
                ? InvStatusConstants.RECEIVED
                : InvStatusConstants.PARTIAL_RECEIVED;
    }

    private static String transferLifecycleStatus(
            InvTransferShipmentReceiptLockedBoundary boundary,
            InvTransferShipmentReceiptValidatedCommand command,
            Map<Long, DetailAccumulator> receiptDeltas,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        boolean discrepancy = command.shortageQuantity().signum() > 0
                || (semantic == InvTransferShipmentReceiptPreparedMutation
                        .ReceiptSemantic.ORDINARY
                        && command.damagedQuantity().signum() > 0);
        boolean allReceived = true;
        for (InvTransferShipmentReceiptLockedBoundary.TransferDetail detail
                : boundary.transferDetails().values())
        {
            DetailAccumulator delta = receiptDeltas.get(detail.detailId());
            BigDecimal after = detail.receivedQuantity().add(delta == null
                    ? ZERO : delta.delta);
            if (after.compareTo(detail.quantity()) != 0)
            {
                allReceived = false;
            }
        }
        return InvTransferLifecyclePolicy.afterReceipt(
                boundary.header().transferStatus(), discrepancy,
                allReceived);
    }

    private static List<InvTransferShipmentReceiptPreparedMutation.TargetStock>
            stockMutations(Map<InvShipmentPlanningItemKey,
                    StockAccumulator> values,
                    InvTransferShipmentReceiptLockedBoundary boundary)
    {
        List<InvTransferShipmentReceiptPreparedMutation.TargetStock> result =
                new ArrayList<>();
        values.forEach((key, value) -> {
            InvTransferShipmentReceiptLockedBoundary.TargetStock existing =
                    boundary.targetStocks().get(key);
            requireStock(existing, key, boundary.header().targetWarehouseId());
            result.add(new InvTransferShipmentReceiptPreparedMutation
                    .TargetStock(key, value.productId,
                            existing == null ? null : existing.stockId(),
                            existing == null ? null : existing.version(),
                            existing == null ? ZERO
                                    : existing.currentQuantity(),
                            (existing == null ? ZERO
                                    : existing.currentQuantity())
                                            .add(value.accepted)
                                            .add(value.damaged),
                            value.accepted, value.damaged, value.cost));
        });
        return List.copyOf(result);
    }

    private static List<InvTransferShipmentReceiptPreparedMutation.StockLog>
            stockLogs(List<InvTransferShipmentReceiptPreparedMutation
                    .TargetStock> stocks)
    {
        return stocks.stream().map(value -> {
            BigDecimal quantity = value.acceptedQuantity()
                    .add(value.damagedQuantity());
            BigDecimal price = value.incomingCost().divide(quantity, 6,
                    RoundingMode.HALF_UP);
            return new InvTransferShipmentReceiptPreparedMutation.StockLog(
                    value.itemKey(), value.productId(), quantity,
                    value.currentQuantityBefore(),
                    value.currentQuantityAfter(), price,
                    value.acceptedQuantity(), value.damagedQuantity());
        }).toList();
    }

    private static void requireStock(
            InvTransferShipmentReceiptLockedBoundary.TargetStock value,
            InvShipmentPlanningItemKey key, Long warehouseId)
    {
        if (value == null)
        {
            return;
        }
        if (!Objects.equals(key, value.itemKey())
                || !Objects.equals(warehouseId, value.shopDeptId())
                || !Objects.equals(warehouseId, value.warehouseId())
                || value.version() == null || value.version() < 0
                || !balance(value.currentQuantity(), value.lockedQuantity(),
                        value.availableQuantity(),
                        value.quarantineQuantity(), value.totalCost()))
        {
            throw new ServiceException("目标汇总库存快照不守恒");
        }
    }

    private static List<InvTransferShipmentReceiptPreparedMutation.TargetLot>
            lotMutations(Map<InvTransferReceiptDerivedLotKey,
                    LotAccumulator> values)
    {
        return values.values().stream().map(value ->
                new InvTransferShipmentReceiptPreparedMutation.TargetLot(
                        value.key, value.productId,
                        value.existing == null ? null
                                : value.existing.lotId(),
                        value.existing == null ? lotNo(value.key)
                                : value.existing.lotNo(),
                        value.source.supplierBatchNo(),
                        value.source.productionDate(),
                        value.source.expiryDate(),
                        "damaged".equals(value.key.disposition())
                                ? "quarantine" : value.source.qcStatus(),
                        value.source.lotStatus()))
                .toList();
    }

    private static List<InvTransferShipmentReceiptPreparedMutation
            .TargetBalance> balanceMutations(
                    Map<InvTransferShipmentReceiptPreparedMutation.BalanceKey,
                            BalanceAccumulator> values)
    {
        List<InvTransferShipmentReceiptPreparedMutation.TargetBalance>
                result = new ArrayList<>();
        for (BalanceAccumulator value : values.values())
        {
            requireBalance(value.existing);
            result.add(new InvTransferShipmentReceiptPreparedMutation
                    .TargetBalance(value.key,
                            value.existing == null ? null
                                    : value.existing.balanceId(),
                            value.existing == null ? null
                                    : value.existing.version(),
                            value.initial,
                            value.running,
                            value.available, value.quarantine, value.cost));
        }
        return List.copyOf(result);
    }

    private static void requireBalance(
            InvTransferShipmentReceiptLockedBoundary.TargetBalance value)
    {
        if (value != null && (value.version() == null || value.version() < 0
                || !balance(value.currentQuantity(), value.lockedQuantity(),
                        value.availableQuantity(),
                        value.quarantineQuantity(), value.totalCost())))
        {
            throw new ServiceException("目标明细库存快照不守恒");
        }
    }

    private static List<InvTransferShipmentReceiptPreparedMutation
            .DetailProgress> detailMutations(
                    Map<Long, DetailAccumulator> values)
    {
        return values.values().stream().map(value ->
                new InvTransferShipmentReceiptPreparedMutation.DetailProgress(
                        value.detailId, value.expected, value.delta,
                        value.expected.add(value.delta))).toList();
    }

    private static boolean balance(BigDecimal current, BigDecimal locked,
            BigDecimal available, BigDecimal quarantine,
            BigDecimal totalCost)
    {
        return quantity(current, false) && quantity(locked, false)
                && quantity(available, false)
                && quantity(quarantine, false)
                && quantity(totalCost, false)
                && current.compareTo(available.add(locked)
                        .add(quarantine)) == 0;
    }

    private static BigDecimal cost(BigDecimal price, BigDecimal quantity)
    {
        return price.multiply(quantity).setScale(6, RoundingMode.HALF_UP);
    }

    private static String receiptNo(String requestId,
            InvTransferShipmentReceiptPreparedMutation.ReceiptSemantic semantic)
    {
        String prefix = semantic == InvTransferShipmentReceiptPreparedMutation
                .ReceiptSemantic.FIXED_RETURN ? "TRR-R-" : "TRR-";
        return prefix + sha256(requestId).substring(0, 64 - prefix.length());
    }

    private static String lotNo(InvTransferReceiptDerivedLotKey key)
    {
        return "TRR-" + key.warehouseId() + "-" + key.sourceLotId()
                + "-" + ("accepted".equals(key.disposition()) ? "A" : "D");
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes)
            {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean quantity(BigDecimal value, boolean positive)
    {
        return value != null && (positive ? value.signum() > 0
                : value.signum() >= 0) && value.scale() <= 6;
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static final class StockAccumulator
    {
        private final InvShipmentPlanningItemKey key;
        private final Long productId;
        private BigDecimal accepted = ZERO;
        private BigDecimal damaged = ZERO;
        private BigDecimal cost = ZERO.setScale(6);

        private StockAccumulator(InvShipmentPlanningItemKey key,
                Long productId)
        {
            this.key = key;
            this.productId = productId;
        }

        private void add(BigDecimal acceptedQuantity,
                BigDecimal damagedQuantity, BigDecimal incomingCost)
        {
            accepted = accepted.add(acceptedQuantity);
            damaged = damaged.add(damagedQuantity);
            cost = cost.add(incomingCost);
        }
    }

    private static final class LotAccumulator
    {
        private final InvTransferReceiptDerivedLotKey key;
        private final Long productId;
        private final InvTransferShipmentReceiptLockedBoundary.SourceLot source;
        private final InvTransferShipmentReceiptLockedBoundary.TargetLot existing;

        private LotAccumulator(InvTransferReceiptDerivedLotKey key,
                Long productId,
                InvTransferShipmentReceiptLockedBoundary.SourceLot source,
                InvTransferShipmentReceiptLockedBoundary.TargetLot existing)
        {
            this.key = key;
            this.productId = productId;
            this.source = source;
            this.existing = existing;
            if (existing != null && !Objects.equals(key, existing.key()))
            {
                throw new ServiceException("目标派生批次快照维度不一致");
            }
        }
    }

    private static final class BalanceAccumulator
    {
        private final InvTransferShipmentReceiptPreparedMutation.BalanceKey key;
        private final InvTransferShipmentReceiptLockedBoundary.TargetBalance existing;
        private BigDecimal available = ZERO;
        private BigDecimal quarantine = ZERO;
        private BigDecimal cost = ZERO.setScale(6);
        private final BigDecimal initial;
        private BigDecimal running;

        private BalanceAccumulator(
                InvTransferShipmentReceiptPreparedMutation.BalanceKey key,
                InvTransferShipmentReceiptLockedBoundary.TargetBalance existing)
        {
            this.key = key;
            this.existing = existing;
            initial = existing == null ? ZERO : existing.currentQuantity();
            running = initial;
        }

        private BalanceStep add(BigDecimal availableQuantity,
                BigDecimal quarantineQuantity, BigDecimal incomingCost)
        {
            BigDecimal before = running;
            available = available.add(availableQuantity);
            quarantine = quarantine.add(quarantineQuantity);
            cost = cost.add(incomingCost);
            running = running.add(availableQuantity)
                    .add(quarantineQuantity);
            Long versionBefore = existing == null ? null
                    : existing.version();
            Long versionAfter = existing == null ? 0L
                    : existing.version() + 1;
            return new BalanceStep(before, running, versionBefore,
                    versionAfter);
        }
    }

    private record BalanceStep(BigDecimal quantityBefore,
            BigDecimal quantityAfter, Long versionBefore, Long versionAfter)
    {
        private static BalanceStep none()
        {
            return new BalanceStep(null, null, null, null);
        }
    }

    private static final class DetailAccumulator
    {
        private final Long detailId;
        private final BigDecimal expected;
        private final BigDecimal maximum;
        private BigDecimal delta = ZERO;

        private DetailAccumulator(Long detailId, BigDecimal expected,
                BigDecimal maximum)
        {
            this.detailId = detailId;
            this.expected = expected;
            this.maximum = maximum;
        }
    }
}
