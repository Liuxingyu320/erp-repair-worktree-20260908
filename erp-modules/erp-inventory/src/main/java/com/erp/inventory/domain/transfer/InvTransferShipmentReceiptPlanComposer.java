package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferShipmentWriteVersions;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;

/** Pure, deterministic receipt boundary shared by future readers and writers. */
public final class InvTransferShipmentReceiptPlanComposer
{
    private static final String FIXED_RETURN = "FIXED_RETURN";
    private static final Set<String> RECEIVABLE_ORDER_STATUSES = Set.of(
            InvStatusConstants.PARTIAL_DELIVERED,
            InvStatusConstants.DELIVERED,
            InvStatusConstants.PARTIAL_RECEIVED);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private InvTransferShipmentReceiptPlanComposer()
    {
    }

    public static Composition compose(InvTransferShipment shipment,
            InvTransferOrder order, InvWarehouseStockMode inputMode,
            List<InvTransferReceiptPlanningAllocationFact> inputAllocations,
            List<InvTransferReceiptPlanningSerialFact> inputSerials,
            List<InvTransferReceiptLocationCandidate> inputLocations)
    {
        requireInputs(shipment, order);
        Long targetWarehouseId = resolveTargetWarehouse(order);
        InvWarehouseStockMode mode = normalizedMode(inputMode,
                targetWarehouseId);
        LinkedHashSet<String> globalBlockers = new LinkedHashSet<>();
        validateHeaders(shipment, order, globalBlockers);
        if (inputMode != null && !Objects.equals(targetWarehouseId,
                inputMode.getWarehouseId()))
        {
            globalBlockers.add("目标仓库存模式行归属不可核验");
        }
        globalBlockers.addAll(authorityBlockers(mode));

        Locations locations = locations(inputLocations, targetWarehouseId,
                globalBlockers);
        if (locations.accepted().isEmpty())
        {
            globalBlockers.add("目标仓库缺少启用的正常存储库位");
        }
        if (locations.quarantine().isEmpty())
        {
            globalBlockers.add("目标仓库缺少启用的残损隔离库位");
        }

        List<InvTransferReceiptPlanningAllocationFact> allocations =
                sortedAllocations(inputAllocations);
        List<InvTransferReceiptPlanningSerialFact> serials =
                sortedSerials(inputSerials);
        if (allocations.isEmpty())
        {
            globalBlockers.add("V2发货批次缺少不可变来源分配");
        }
        Map<Long, List<InvTransferReceiptPlanningSerialFact>> serialsByLine =
                serialsByAllocation(serials, globalBlockers);
        GroupAudit groupAudit = auditShipmentDetails(allocations);

        List<Line> lines = new ArrayList<>();
        Set<Long> allocationIds = new HashSet<>();
        for (InvTransferReceiptPlanningAllocationFact fact : allocations)
        {
            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            validateAllocation(fact, shipment, order, allocationIds,
                    blockers);
            if (fact != null && groupAudit.invalidQuantityDetailIds().contains(
                    fact.getShipmentDetailId()))
            {
                blockers.add("发货明细分配数量与已发数量不守恒");
            }
            if (fact != null && groupAudit.invalidAcceptedDetailIds().contains(
                    fact.getShipmentDetailId()))
            {
                blockers.add("发货明细累计合格数量与来源分配不守恒");
            }
            Progress progress = progress(fact);
            List<InvTransferReceiptPlanningSerialFact> lineSerials =
                    fact == null ? List.of() : serialsByLine.getOrDefault(
                            fact.getAllocationId(), List.of());
            validateTracking(fact, lineSerials, progress.remainingQuantity(),
                    blockers);

            BigDecimal remaining = progress.remainingQuantity();
            String status = !blockers.isEmpty() ? "blocked"
                    : remaining != null && remaining.signum() == 0
                            ? "complete" : "ready";
            BigDecimal suggested = "ready".equals(status)
                    ? remaining : ZERO;
            lines.add(new Line(fact, progress.acceptedQuantity(),
                    progress.damagedQuantity(), progress.shortageQuantity(),
                    remaining,
                    suggested, List.copyOf(lineSerials), status,
                    List.copyOf(blockers)));
        }

        Set<Long> knownAllocations = allocations.stream()
                .filter(Objects::nonNull)
                .map(InvTransferReceiptPlanningAllocationFact::getAllocationId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors
                        .toSet());
        if (serials.stream().anyMatch(serial -> serial == null
                || !knownAllocations.contains(serial.getAllocationId())))
        {
            globalBlockers.add("发货序列号存在无法归属的来源分配");
        }

        boolean hasReady = lines.stream().anyMatch(line ->
                "ready".equals(line.recommendationStatus()));
        if (lines.stream().anyMatch(line ->
                "blocked".equals(line.recommendationStatus())))
        {
            globalBlockers.add("收货规划存在不可核验的来源分配");
        }
        if (!hasReady)
        {
            globalBlockers.add("当前发货批次没有可安全规划的收货分配");
        }
        List<String> blockerList = List.copyOf(globalBlockers);
        String version = fingerprint(shipment, order, mode, allocations,
                serials, locations.all(), lines, blockerList);
        return new Composition(version, mode, List.copyOf(lines),
                locations.accepted(), locations.quarantine(),
                blockerList.isEmpty() && hasReady, blockerList);
    }

    private static void requireInputs(InvTransferShipment shipment,
            InvTransferOrder order)
    {
        if (shipment == null || shipment.getShipmentId() == null
                || shipment.getTransferId() == null || order == null
                || order.getTransferId() == null || order.getVersion() == null)
        {
            throw new ServiceException("收货规划缺少可核验业务事实");
        }
    }

    private static void validateHeaders(InvTransferShipment shipment,
            InvTransferOrder order, Set<String> blockers)
    {
        if (!Objects.equals(shipment.getTransferId(), order.getTransferId()))
        {
            blockers.add("发货批次与调拨单归属不一致");
        }
        if (!InvTransferShipmentWriteVersions.V2_DETAIL.equals(
                shipment.getInventoryWriteVersion()))
        {
            blockers.add("发货批次不是V2明细库存写版本");
        }
        if (!Set.of(InvStatusConstants.PENDING_RECEIVE,
                InvStatusConstants.PARTIAL_RECEIVED).contains(
                        shipment.getStatus()))
        {
            blockers.add("发货批次当前状态不允许收货");
        }
        if (!RECEIVABLE_ORDER_STATUSES.contains(order.getStatus()))
        {
            blockers.add("调拨单当前状态不允许收货");
        }
        if (!hash(shipment.getPlanVersion())
                || shipment.getSealedRevisionId() == null
                || shipment.getSealedRevisionId() <= 0
                || blank(shipment.getReconcileBatch()))
        {
            blockers.add("V2发货批次审计快照不完整");
        }
        if (!positive(shipment.getWarehouseId())
                || !Objects.equals(shipment.getWarehouseId(),
                        shipment.getSourceLocationDeptId()))
        {
            blockers.add("V2发货批次来源仓快照不可核验");
        }
    }

    private static void validateAllocation(
            InvTransferReceiptPlanningAllocationFact fact,
            InvTransferShipment shipment, InvTransferOrder order,
            Set<Long> allocationIds, Set<String> blockers)
    {
        if (fact == null || !positive(fact.getAllocationId())
                || !allocationIds.add(fact.getAllocationId())
                || !Objects.equals(fact.getShipmentId(),
                        shipment.getShipmentId())
                || !Objects.equals(fact.getTransferId(),
                        order.getTransferId())
                || !positive(fact.getShipmentDetailId())
                || !positive(fact.getTransferDetailId()))
        {
            blockers.add("来源发货分配归属、标识或唯一性不可核验");
        }
        if (fact == null)
        {
            return;
        }
        if (FIXED_RETURN.equals(fact.getAllocationPolicy()))
        {
            blockers.add("差异退回发货必须使用专属收货边界");
        }
        String itemType = InvItemTypes.normalize(fact.getItemType());
        if (!Objects.equals(fact.getSourceWarehouseId(),
                shipment.getWarehouseId())
                || !Objects.equals(fact.getSourceWarehouseId(),
                        shipment.getSourceLocationDeptId()))
        {
            blockers.add("来源分配仓库与V2发货快照不一致");
        }
        if (!positive(fact.getItemId()) || blank(fact.getItemType())
                || blank(fact.getItemCode()) || blank(fact.getItemName())
                || blank(fact.getUnit()) || !positive(fact.getSourceWarehouseId())
                || !positive(fact.getSourceBalanceId())
                || !positive(fact.getSourceLotId())
                || !positive(fact.getSourceLocationId())
                || blank(fact.getSourceLotNo())
                || blank(fact.getSourceLocationCode())
                || blank(itemType))
        {
            blockers.add("来源分配缺少物料、批次或库位事实");
        }
        if (!quantity(fact.getAllocatedQuantity(), true, 4)
                || !quantity(fact.getShipmentDetailShippedQuantity(),
                        true, 4)
                || !quantity(fact.getShipmentDetailReceivedQuantity(),
                        false, 4)
                || fact.getShipmentDetailReceivedQuantity().compareTo(
                        fact.getShipmentDetailShippedQuantity()) > 0)
        {
            blockers.add("来源分配发货或累计收货数量无效");
        }
        if (!quantity(fact.getCostPrice(), false, 6)
                || !quantity(fact.getTotalCost(), false, 6)
                || fact.getBalanceVersionAfter() == null
                || fact.getBalanceVersionAfter() < 0)
        {
            blockers.add("来源分配成本或余额版本不可核验");
        }
        else if (fact.getAllocatedQuantity() != null)
        {
            BigDecimal expected = fact.getAllocatedQuantity()
                    .multiply(fact.getCostPrice())
                    .setScale(6, RoundingMode.HALF_UP);
            if (expected.compareTo(fact.getTotalCost()) != 0)
            {
                blockers.add("来源分配金额与实际批次成本不守恒");
            }
        }
        if (!progress(fact).valid())
        {
            blockers.add("来源分配逐次收货进度不可核验");
        }
    }

    private static void validateTracking(
            InvTransferReceiptPlanningAllocationFact fact,
            List<InvTransferReceiptPlanningSerialFact> serials,
            BigDecimal remainingQuantity, Set<String> blockers)
    {
        if (fact == null)
        {
            return;
        }
        String tracking = normalized(fact.getTrackingPolicy());
        if (!Set.of("lot", "serial").contains(tracking))
        {
            blockers.add("来源分配跟踪策略不可核验");
            return;
        }
        if ("lot".equals(tracking))
        {
            if (!serials.isEmpty())
            {
                blockers.add("批次跟踪分配不应包含序列号");
            }
            return;
        }
        int expected;
        try
        {
            expected = remainingQuantity.intValueExact();
        }
        catch (ArithmeticException | NullPointerException exception)
        {
            blockers.add("序列号分配数量必须为整数");
            return;
        }
        if (serials.size() != expected)
        {
            blockers.add("序列号数量与来源分配数量不一致");
        }
        Set<Long> unique = new HashSet<>();
        for (InvTransferReceiptPlanningSerialFact serial : serials)
        {
            if (serial == null || !positive(serial.getSerialId())
                    || !unique.add(serial.getSerialId())
                    || !Objects.equals(serial.getShipmentId(),
                            fact.getShipmentId())
                    || !Objects.equals(serial.getAllocationId(),
                            fact.getAllocationId())
                    || !Objects.equals(InvItemTypes.normalize(
                            serial.getItemType()), InvItemTypes.normalize(
                                    fact.getItemType()))
                    || !Objects.equals(serial.getItemId(), fact.getItemId())
                    || !Objects.equals(serial.getSourceBalanceId(),
                            fact.getSourceBalanceId())
                    || !Objects.equals(serial.getSourceLotId(),
                            fact.getSourceLotId())
                    || !Objects.equals(serial.getSourceLocationId(),
                            fact.getSourceLocationId())
                    || !Objects.equals(serial.getCurrentWarehouseId(),
                            fact.getSourceWarehouseId())
                    || !Objects.equals(serial.getCurrentBalanceId(),
                            fact.getSourceBalanceId())
                    || !Objects.equals(serial.getCurrentLotId(),
                            fact.getSourceLotId())
                    || !Objects.equals(serial.getCurrentLocationId(),
                            fact.getSourceLocationId())
                    || !"shipped".equals(serial.getStatusAfter())
                    || !"shipped".equals(serial.getCurrentStatus())
                    || blank(serial.getSerialNoSnapshot())
                    || !Objects.equals(serial.getSerialNoSnapshot(),
                            serial.getCurrentSerialNo()))
            {
                blockers.add("序列号发货审计或当前位置不可核验");
            }
        }
    }

    private static GroupAudit auditShipmentDetails(
            List<InvTransferReceiptPlanningAllocationFact> allocations)
    {
        Map<Long, List<InvTransferReceiptPlanningAllocationFact>> groups =
                new LinkedHashMap<>();
        for (InvTransferReceiptPlanningAllocationFact fact : allocations)
        {
            if (fact != null && fact.getShipmentDetailId() != null)
            {
                groups.computeIfAbsent(fact.getShipmentDetailId(),
                        ignored -> new ArrayList<>()).add(fact);
            }
        }
        Set<Long> invalid = new HashSet<>();
        Set<Long> invalidAccepted = new HashSet<>();
        for (Map.Entry<Long, List<InvTransferReceiptPlanningAllocationFact>>
                entry : groups.entrySet())
        {
            BigDecimal sum = ZERO;
            BigDecimal accepted = ZERO;
            BigDecimal shipped = null;
            BigDecimal received = null;
            for (InvTransferReceiptPlanningAllocationFact fact
                    : entry.getValue())
            {
                if (fact.getAllocatedQuantity() == null
                        || fact.getShipmentDetailShippedQuantity() == null
                        || fact.getShipmentDetailReceivedQuantity() == null)
                {
                    invalid.add(entry.getKey());
                    continue;
                }
                sum = sum.add(fact.getAllocatedQuantity());
                if (fact.getAcceptedReceivedQuantity() == null)
                {
                    invalidAccepted.add(entry.getKey());
                }
                else
                {
                    accepted = accepted.add(
                            fact.getAcceptedReceivedQuantity());
                }
                if (shipped != null && shipped.compareTo(
                        fact.getShipmentDetailShippedQuantity()) != 0)
                {
                    invalid.add(entry.getKey());
                }
                if (received != null && received.compareTo(
                        fact.getShipmentDetailReceivedQuantity()) != 0)
                {
                    invalid.add(entry.getKey());
                }
                shipped = fact.getShipmentDetailShippedQuantity();
                received = fact.getShipmentDetailReceivedQuantity();
            }
            if (shipped == null || sum.compareTo(shipped) != 0)
            {
                invalid.add(entry.getKey());
            }
            if (received == null || accepted.compareTo(received) != 0)
            {
                invalidAccepted.add(entry.getKey());
            }
        }
        return new GroupAudit(Set.copyOf(invalid),
                Set.copyOf(invalidAccepted));
    }

    private static Progress progress(
            InvTransferReceiptPlanningAllocationFact fact)
    {
        if (fact == null || !quantity(fact.getAllocatedQuantity(), true, 4)
                || !quantity(fact.getAcceptedReceivedQuantity(), false, 4)
                || !quantity(fact.getDamagedReceivedQuantity(), false, 4)
                || !quantity(fact.getShortageReportedQuantity(), false, 4)
                || fact.getReceiptVersion() == null
                || fact.getReceiptVersion() < 0)
        {
            return Progress.invalid();
        }
        BigDecimal classified = fact.getAcceptedReceivedQuantity()
                .add(fact.getDamagedReceivedQuantity())
                .add(fact.getShortageReportedQuantity());
        if (classified.compareTo(fact.getAllocatedQuantity()) > 0)
        {
            return Progress.invalid();
        }
        return new Progress(fact.getAcceptedReceivedQuantity(),
                fact.getDamagedReceivedQuantity(),
                fact.getShortageReportedQuantity(),
                fact.getAllocatedQuantity().subtract(classified), true);
    }

    private static Map<Long, List<InvTransferReceiptPlanningSerialFact>>
            serialsByAllocation(
                    List<InvTransferReceiptPlanningSerialFact> serials,
                    Set<String> globalBlockers)
    {
        Map<Long, List<InvTransferReceiptPlanningSerialFact>> result =
                new HashMap<>();
        Set<Long> auditIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        for (InvTransferReceiptPlanningSerialFact serial : serials)
        {
            if (serial == null || !positive(serial.getShipmentSerialId())
                    || !auditIds.add(serial.getShipmentSerialId())
                    || !positive(serial.getSerialId())
                    || !serialIds.add(serial.getSerialId())
                    || !positive(serial.getAllocationId()))
            {
                globalBlockers.add("发货序列号审计标识重复或无效");
            }
            if (serial != null && serial.getAllocationId() != null)
            {
                result.computeIfAbsent(serial.getAllocationId(),
                        ignored -> new ArrayList<>()).add(serial);
            }
        }
        return result;
    }

    private static Locations locations(
            List<InvTransferReceiptLocationCandidate> input,
            Long warehouseId, Set<String> blockers)
    {
        List<InvTransferReceiptLocationCandidate> all = input == null
                ? new ArrayList<>() : new ArrayList<>(input);
        all.sort(Comparator
                .comparing((InvTransferReceiptLocationCandidate value) ->
                        "quarantine".equals(normalized(
                                value == null ? null
                                        : value.getLocationType())) ? 1 : 0)
                .thenComparing(value -> value == null
                        || value.getSortOrder() == null ? 0
                                : value.getSortOrder())
                .thenComparing(value -> value == null
                        || value.getLocationId() == null ? Long.MIN_VALUE
                                : value.getLocationId()));
        List<InvTransferReceiptLocationCandidate> accepted =
                new ArrayList<>();
        List<InvTransferReceiptLocationCandidate> quarantine =
                new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (InvTransferReceiptLocationCandidate location : all)
        {
            if (location == null || !positive(location.getLocationId())
                    || !ids.add(location.getLocationId())
                    || !Objects.equals(warehouseId,
                            location.getWarehouseId())
                    || blank(location.getLocationCode())
                    || blank(location.getLocationName())
                    || !"0".equals(location.getStatus())
                    || !"0".equals(location.getVirtualFlag())
                    || !Set.of("storage", "quarantine").contains(
                            normalized(location.getLocationType())))
            {
                blockers.add("目标收货库位归属、状态或类型不可核验");
                continue;
            }
            if ("quarantine".equals(normalized(
                    location.getLocationType())))
            {
                quarantine.add(location);
            }
            else
            {
                accepted.add(location);
            }
        }
        return new Locations(List.copyOf(all), List.copyOf(accepted),
                List.copyOf(quarantine));
    }

    private static List<InvTransferReceiptPlanningAllocationFact>
            sortedAllocations(
                    List<InvTransferReceiptPlanningAllocationFact> input)
    {
        List<InvTransferReceiptPlanningAllocationFact> result = input == null
                ? new ArrayList<>() : new ArrayList<>(input);
        result.sort(Comparator.comparing(value -> value == null
                || value.getAllocationId() == null ? Long.MIN_VALUE
                        : value.getAllocationId()));
        return result;
    }

    private static List<InvTransferReceiptPlanningSerialFact> sortedSerials(
            List<InvTransferReceiptPlanningSerialFact> input)
    {
        List<InvTransferReceiptPlanningSerialFact> result = input == null
                ? new ArrayList<>() : new ArrayList<>(input);
        result.sort(Comparator
                .comparing((InvTransferReceiptPlanningSerialFact value) ->
                        value == null || value.getAllocationId() == null
                                ? Long.MIN_VALUE : value.getAllocationId())
                .thenComparing(value -> value == null
                        || value.getSerialId() == null ? Long.MIN_VALUE
                                : value.getSerialId()));
        return result;
    }

    private static InvWarehouseStockMode normalizedMode(
            InvWarehouseStockMode input, Long warehouseId)
    {
        InvWarehouseStockMode result = new InvWarehouseStockMode();
        result.setWarehouseId(warehouseId);
        result.setWriteMode(allowed(input == null ? null
                : input.getWriteMode(), Set.of("legacy", "dual"), "legacy"));
        result.setReadMode(allowed(input == null ? null
                : input.getReadMode(), Set.of("legacy", "shadow", "detail"),
                "legacy"));
        result.setReconcileStatus(allowed(input == null ? null
                : input.getReconcileStatus(),
                Set.of("not_run", "passed", "failed"), "not_run"));
        result.setLastReconcileBatch(blank(input == null ? null
                : input.getLastReconcileBatch()) ? null
                        : input.getLastReconcileBatch().trim());
        return result;
    }

    private static List<String> authorityBlockers(InvWarehouseStockMode mode)
    {
        List<String> result = new ArrayList<>();
        if (!"dual".equals(mode.getWriteMode()))
        {
            result.add("目标仓库尚未启用 dual 明细库存双写");
        }
        if (!"detail".equals(mode.getReadMode()))
        {
            result.add("目标仓库尚未切换 detail 明细库存读取");
        }
        if (!"passed".equals(mode.getReconcileStatus())
                || blank(mode.getLastReconcileBatch()))
        {
            result.add("目标仓库明细库存对账尚未通过或批次缺失");
        }
        return result;
    }

    private static String fingerprint(InvTransferShipment shipment,
            InvTransferOrder order, InvWarehouseStockMode mode,
            List<InvTransferReceiptPlanningAllocationFact> allocations,
            List<InvTransferReceiptPlanningSerialFact> serials,
            List<InvTransferReceiptLocationCandidate> locations,
            List<Line> lines, List<String> globalBlockers)
    {
        StringBuilder value = new StringBuilder("receipt-plan-v1")
                .append('|').append(shipment.getShipmentId())
                .append('|').append(shipment.getTransferId())
                .append('|').append(shipment.getStatus())
                .append('|').append(shipment.getInventoryWriteVersion())
                .append('|').append(shipment.getWarehouseId())
                .append('|').append(shipment.getSourceLocationDeptId())
                .append('|').append(shipment.getPlanVersion())
                .append('|').append(shipment.getSealedRevisionId())
                .append('|').append(shipment.getReconcileBatch())
                .append('|').append(order.getVersion())
                .append('|').append(order.getStatus())
                .append('|').append(order.getFromDeptId())
                .append('|').append(order.getFromWarehouseId())
                .append('|').append(order.getToDeptId())
                .append('|').append(order.getToWarehouseId())
                .append('|').append(mode.getWarehouseId())
                .append('|').append(mode.getWriteMode())
                .append('|').append(mode.getReadMode())
                .append('|').append(mode.getReconcileStatus())
                .append('|').append(mode.getLastReconcileBatch());
        for (InvTransferReceiptPlanningAllocationFact fact : allocations)
        {
            if (fact == null)
            {
                value.append("|allocation:null");
                continue;
            }
            value.append("|allocation:").append(fact.getAllocationId())
                    .append(':').append(fact.getShipmentDetailId())
                    .append(':').append(fact.getTransferDetailId())
                    .append(':').append(fact.getItemType())
                    .append(':').append(fact.getItemId())
                    .append(':').append(fact.getAllocationPolicy())
                    .append(':').append(fact.getTrackingPolicy())
                    .append(':').append(decimal(fact.getAllocatedQuantity()))
                    .append(':').append(fact.getBalanceVersionAfter())
                    .append(':').append(decimal(fact.getCostPrice()))
                    .append(':').append(decimal(fact.getTotalCost()))
                    .append(':').append(fact.getSourceWarehouseId())
                    .append(':').append(fact.getSourceBalanceId())
                    .append(':').append(fact.getSourceLotId())
                    .append(':').append(fact.getSourceLocationId())
                    .append(':').append(fact.getSourceLotNo())
                    .append(':').append(fact.getSupplierBatchNo())
                    .append(':').append(epoch(fact.getProductionDate()))
                    .append(':').append(epoch(fact.getExpiryDate()))
                    .append(':').append(fact.getQcStatus())
                    .append(':').append(fact.getLotStatus())
                    .append(':').append(
                            fact.getSourceReceiptDisposition())
                    .append(':').append(fact.getSourceLocationCode())
                    .append(':').append(fact.getSourceLocationType())
                    .append(':').append(decimal(
                            fact.getShipmentDetailShippedQuantity()))
                    .append(':').append(decimal(
                            fact.getShipmentDetailReceivedQuantity()))
                    .append(':').append(decimal(
                            fact.getTransferDetailRequestedQuantity()))
                    .append(':').append(decimal(
                            fact.getTransferDetailDeliveredQuantity()))
                    .append(':').append(decimal(
                            fact.getTransferDetailReceivedQuantity()))
                    .append(':').append(decimal(
                            fact.getAcceptedReceivedQuantity()))
                    .append(':').append(decimal(
                            fact.getDamagedReceivedQuantity()))
                    .append(':').append(decimal(
                            fact.getShortageReportedQuantity()))
                    .append(':').append(fact.getReceiptVersion());
        }
        for (InvTransferReceiptPlanningSerialFact serial : serials)
        {
            if (serial == null)
            {
                value.append("|serial:null");
                continue;
            }
            value.append("|serial:").append(serial.getShipmentSerialId())
                    .append(':').append(serial.getAllocationId())
                    .append(':').append(serial.getSerialId())
                    .append(':').append(serial.getSerialNoSnapshot())
                    .append(':').append(serial.getCurrentSerialNo())
                    .append(':').append(serial.getStatusAfter())
                    .append(':').append(serial.getCurrentStatus())
                    .append(':').append(serial.getCurrentWarehouseId())
                    .append(':').append(serial.getCurrentBalanceId())
                    .append(':').append(serial.getCurrentLotId())
                    .append(':').append(serial.getCurrentLocationId());
        }
        for (InvTransferReceiptLocationCandidate location : locations)
        {
            if (location == null)
            {
                value.append("|location:null");
                continue;
            }
            value.append("|location:").append(location.getLocationId())
                    .append(':').append(location.getWarehouseId())
                    .append(':').append(location.getLocationCode())
                    .append(':').append(location.getLocationName())
                    .append(':').append(location.getLocationType())
                    .append(':').append(location.getSortOrder())
                    .append(':').append(location.getStatus())
                    .append(':').append(location.getVirtualFlag());
        }
        lines.forEach(line -> line.blockers().forEach(blocker -> value
                .append("|line-blocker:")
                .append(line.fact() == null ? null
                        : line.fact().getAllocationId())
                .append(':').append(blocker)));
        globalBlockers.forEach(blocker -> value.append("|blocker:")
                .append(blocker));
        return sha256(value.toString());
    }

    private static Long resolveTargetWarehouse(InvTransferOrder order)
    {
        Long value = order.getToWarehouseId() != null
                && order.getToWarehouseId() > 0 ? order.getToWarehouseId()
                        : order.getToDeptId();
        if (!positive(value))
        {
            throw new ServiceException("调拨单缺少有效目标仓库");
        }
        return value;
    }

    private static boolean quantity(BigDecimal value, boolean strictPositive,
            int scale)
    {
        return value != null && value.scale() <= scale
                && (strictPositive ? value.signum() > 0
                        : value.signum() >= 0);
    }

    private static boolean positive(BigDecimal value)
    {
        return value != null && value.signum() > 0;
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String normalized(String value)
    {
        return value == null ? null : value.trim().toLowerCase();
    }

    private static String allowed(String value, Set<String> allowed,
            String fallback)
    {
        String normalized = normalized(value);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            return "null";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static Long epoch(java.util.Date value)
    {
        return value == null ? null : value.getTime();
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

    public record Composition(String receiptPlanVersion,
            InvWarehouseStockMode targetStockMode, List<Line> lines,
            List<InvTransferReceiptLocationCandidate> acceptedLocations,
            List<InvTransferReceiptLocationCandidate> quarantineLocations,
            boolean canCreateReceipt, List<String> blockingReasons)
    {
    }

    public record Line(InvTransferReceiptPlanningAllocationFact fact,
            BigDecimal acceptedQuantity, BigDecimal damagedQuantity,
            BigDecimal shortageQuantity, BigDecimal remainingQuantity,
            BigDecimal suggestedAcceptedQuantity,
            List<InvTransferReceiptPlanningSerialFact> serials,
            String recommendationStatus, List<String> blockers)
    {
    }

    private record Locations(
            List<InvTransferReceiptLocationCandidate> all,
            List<InvTransferReceiptLocationCandidate> accepted,
            List<InvTransferReceiptLocationCandidate> quarantine)
    {
    }

    private record GroupAudit(Set<Long> invalidQuantityDetailIds,
            Set<Long> invalidAcceptedDetailIds)
    {
    }

    private record Progress(BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity, BigDecimal shortageQuantity,
            BigDecimal remainingQuantity, boolean valid)
    {
        private static Progress invalid()
        {
            return new Progress(ZERO, ZERO, ZERO, null, false);
        }
    }
}
