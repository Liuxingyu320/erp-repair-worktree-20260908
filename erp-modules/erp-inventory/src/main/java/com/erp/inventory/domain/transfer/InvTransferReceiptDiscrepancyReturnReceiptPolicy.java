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
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferShipment;

/** Pure read plan for receiving an expected damaged return into quarantine. */
public final class InvTransferReceiptDiscrepancyReturnReceiptPolicy
{
    public static final String DATA_SOURCE =
            "return-receipt-quarantine-plan-v1";

    private static final String SOURCE_BUSINESS_TYPE =
            "transfer_discrepancy_return";
    private static final String FIXED_RETURN = "FIXED_RETURN";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<String> RECEIVABLE_ORDER_STATUSES = Set.of(
            InvStatusConstants.PARTIAL_DELIVERED,
            InvStatusConstants.DELIVERED,
            InvStatusConstants.PARTIAL_RECEIVED);

    private InvTransferReceiptDiscrepancyReturnReceiptPolicy()
    {
    }

    public static Plan compose(InvTransferShipment shipment,
            InvTransferOrder order, InvWarehouseStockMode inputMode,
            List<InvTransferReceiptPlanningAllocationFact> inputAllocations,
            List<InvTransferReceiptPlanningSerialFact> inputSerials,
            List<InvTransferReceiptLocationCandidate> inputLocations)
    {
        requireInputs(shipment, order);
        Long targetWarehouseId = targetWarehouse(order);
        InvWarehouseStockMode mode = normalizedMode(inputMode,
                targetWarehouseId);
        LinkedHashSet<String> globalBlockers = new LinkedHashSet<>();
        validateHeader(shipment, order, globalBlockers);
        if (inputMode != null && !Objects.equals(targetWarehouseId,
                inputMode.getWarehouseId()))
        {
            globalBlockers.add("退回目标仓库存模式归属不可核验");
        }
        addAuthorityBlockers(mode, globalBlockers);

        List<InvTransferReceiptLocationCandidate> quarantineLocations =
                quarantineLocations(inputLocations, targetWarehouseId,
                        globalBlockers);
        if (quarantineLocations.isEmpty())
        {
            globalBlockers.add("退回目标仓库缺少启用的残损隔离库位");
        }

        List<InvTransferReceiptPlanningAllocationFact> allocations =
                sortedAllocations(inputAllocations);
        List<InvTransferReceiptPlanningSerialFact> serials =
                sortedSerials(inputSerials);
        if (allocations.isEmpty())
        {
            globalBlockers.add("退回发货批次缺少固定隔离来源分配");
        }
        Map<Long, List<InvTransferReceiptPlanningSerialFact>> byAllocation =
                serialsByAllocation(serials, globalBlockers);
        GroupAudit audit = auditDetails(allocations);

        List<Line> lines = new ArrayList<>();
        Set<Long> allocationIds = new HashSet<>();
        for (InvTransferReceiptPlanningAllocationFact fact : allocations)
        {
            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            validateAllocation(fact, shipment, order, allocationIds,
                    blockers);
            if (fact != null && audit.invalidQuantityDetailIds().contains(
                    fact.getShipmentDetailId()))
            {
                blockers.add("退回发货明细分配数量与已发数量不守恒");
            }
            if (fact != null && audit.invalidReceiptDetailIds().contains(
                    fact.getShipmentDetailId()))
            {
                blockers.add("退回发货明细累计实收与固定来源进度不守恒");
            }
            Progress progress = progress(fact);
            List<InvTransferReceiptPlanningSerialFact> lineSerials =
                    fact == null ? List.of() : byAllocation.getOrDefault(
                            fact.getAllocationId(), List.of());
            validateTracking(fact, lineSerials, progress.remaining(),
                    blockers);
            String status = !blockers.isEmpty() ? "blocked"
                    : progress.remaining() != null
                            && progress.remaining().signum() == 0
                                    ? "complete" : "ready";
            BigDecimal suggested = "ready".equals(status)
                    ? progress.remaining() : ZERO;
            lines.add(new Line(fact, progress.returned(),
                    progress.shortage(), progress.remaining(), suggested,
                    List.copyOf(lineSerials), status,
                    List.copyOf(blockers)));
        }

        Set<Long> known = allocations.stream().filter(Objects::nonNull)
                .map(InvTransferReceiptPlanningAllocationFact
                        ::getAllocationId)
                .filter(Objects::nonNull).collect(
                        java.util.stream.Collectors.toSet());
        if (serials.stream().anyMatch(value -> value == null
                || !known.contains(value.getAllocationId())))
        {
            globalBlockers.add("退回发货序列号存在无法归属的来源分配");
        }
        boolean ready = lines.stream().anyMatch(
                value -> "ready".equals(value.status()));
        if (lines.stream().anyMatch(
                value -> "blocked".equals(value.status())))
        {
            globalBlockers.add("退回收货规划存在不可核验的来源分配");
        }
        if (!ready)
        {
            globalBlockers.add("当前退回发货批次没有可安全规划的实收分配");
        }
        List<String> blockers = List.copyOf(globalBlockers);
        String version = fingerprint(shipment, order, mode, allocations,
                serials, quarantineLocations, lines, blockers);
        return new Plan(version, mode, List.copyOf(lines),
                quarantineLocations, blockers.isEmpty() && ready, blockers);
    }

    private static void requireInputs(InvTransferShipment shipment,
            InvTransferOrder order)
    {
        if (shipment == null || !positive(shipment.getShipmentId())
                || !positive(shipment.getTransferId()) || order == null
                || !positive(order.getTransferId())
                || order.getVersion() == null || order.getVersion() < 0)
        {
            throw new ServiceException("退回收货规划缺少可核验业务事实");
        }
    }

    private static void validateHeader(InvTransferShipment shipment,
            InvTransferOrder order, Set<String> blockers)
    {
        if (!Objects.equals(shipment.getTransferId(), order.getTransferId())
                || !SOURCE_BUSINESS_TYPE.equals(
                        order.getSourceBusinessType())
                || !positive(order.getSourceBusinessId())
                || order.getApprovalRound() == null
                || order.getApprovalRound() <= 0)
        {
            blockers.add("退回发货批次与裁决子调拨归属不可核验");
        }
        if (!member(order.getTransferType(), Set.of(
                InvTransferTypes.WAREHOUSE,
                InvTransferTypes.STORE_RETURN,
                InvTransferTypes.CROSS_STORE))
                || (InvTransferTypes.STORE_RETURN.equals(
                        order.getTransferType())
                        && blank(order.getReturnReasonCode())))
        {
            blockers.add("退回子调拨类型或返仓原因不可核验");
        }
        if (!InvTransferShipmentWriteVersions.V2_DETAIL.equals(
                shipment.getInventoryWriteVersion())
                || !member(shipment.getStatus(), Set.of(
                        InvStatusConstants.PENDING_RECEIVE,
                        InvStatusConstants.PARTIAL_RECEIVED))
                || !member(order.getStatus(), RECEIVABLE_ORDER_STATUSES))
        {
            blockers.add("退回发货批次或子调拨当前状态不允许收货");
        }
        if (!hash(shipment.getPlanVersion())
                || !positive(shipment.getSealedRevisionId())
                || blank(shipment.getReconcileBatch())
                || blank(shipment.getCommandRequestId()))
        {
            blockers.add("退回V2发货审计快照不完整");
        }
        if (!positive(shipment.getWarehouseId())
                || !Objects.equals(shipment.getWarehouseId(),
                        shipment.getSourceLocationDeptId())
                || !Objects.equals(shipment.getWarehouseId(),
                        sourceWarehouse(order)))
        {
            blockers.add("退回V2发货来源仓与子调拨快照不可核验");
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
            blockers.add("退回来源分配归属、标识或唯一性不可核验");
        }
        if (fact == null)
        {
            return;
        }
        if (!FIXED_RETURN.equals(fact.getAllocationPolicy()))
        {
            blockers.add("退回来源分配不是FIXED_RETURN固定隔离来源");
        }
        String itemType = InvItemTypes.normalize(fact.getItemType());
        if (!Objects.equals(fact.getSourceWarehouseId(),
                shipment.getWarehouseId())
                || !Objects.equals(fact.getSourceWarehouseId(),
                        shipment.getSourceLocationDeptId()))
        {
            blockers.add("退回来源仓库与V2发货快照不一致");
        }
        if (!positive(fact.getItemId()) || blank(itemType)
                || blank(fact.getItemCode()) || blank(fact.getItemName())
                || blank(fact.getUnit())
                || !positive(fact.getSourceBalanceId())
                || !positive(fact.getSourceLotId())
                || !positive(fact.getSourceLocationId())
                || blank(fact.getSourceLotNo())
                || blank(fact.getSourceLocationCode()))
        {
            blockers.add("退回来源分配缺少物料、批次或库位事实");
        }
        if (!"damaged".equals(normalized(
                fact.getSourceReceiptDisposition()))
                || !"quarantine".equals(normalized(fact.getQcStatus()))
                || !"active".equals(normalized(fact.getLotStatus()))
                || !"quarantine".equals(normalized(
                        fact.getSourceLocationType())))
        {
            blockers.add("退回来源批次或库位不再属于残损隔离库存");
        }
        if (!quantity(fact.getAllocatedQuantity(), true, 4)
                || !quantity(fact.getShipmentDetailShippedQuantity(),
                        true, 4)
                || !quantity(fact.getShipmentDetailReceivedQuantity(),
                        false, 4)
                || !quantity(fact.getTransferDetailRequestedQuantity(),
                        true, 4)
                || !quantity(fact.getTransferDetailDeliveredQuantity(),
                        false, 4)
                || !quantity(fact.getTransferDetailReceivedQuantity(),
                        false, 4)
                || fact.getShipmentDetailReceivedQuantity().compareTo(
                        fact.getShipmentDetailShippedQuantity()) > 0
                || fact.getTransferDetailReceivedQuantity().compareTo(
                        fact.getTransferDetailDeliveredQuantity()) > 0
                || fact.getTransferDetailDeliveredQuantity().compareTo(
                        fact.getTransferDetailRequestedQuantity()) > 0
                || fact.getShipmentDetailShippedQuantity().compareTo(
                        fact.getTransferDetailDeliveredQuantity()) > 0
                || fact.getShipmentDetailReceivedQuantity().compareTo(
                        fact.getTransferDetailReceivedQuantity()) > 0)
        {
            blockers.add("退回发货或累计实收数量无效");
        }
        if (!quantity(fact.getCostPrice(), false, 6)
                || !quantity(fact.getTotalCost(), false, 6)
                || fact.getBalanceVersionAfter() == null
                || fact.getBalanceVersionAfter() < 0)
        {
            blockers.add("退回来源成本或余额版本不可核验");
        }
        else if (fact.getAllocatedQuantity() != null)
        {
            BigDecimal expected = fact.getAllocatedQuantity()
                    .multiply(fact.getCostPrice())
                    .setScale(6, RoundingMode.HALF_UP);
            if (expected.compareTo(fact.getTotalCost()) != 0)
            {
                blockers.add("退回来源金额与实际批次成本不守恒");
            }
        }
        if (!progress(fact).valid())
        {
            blockers.add("退回来源逐次实收进度不可核验");
        }
    }

    private static Progress progress(
            InvTransferReceiptPlanningAllocationFact fact)
    {
        if (fact == null
                || !quantity(fact.getAllocatedQuantity(), true, 4)
                || !quantity(fact.getAcceptedReceivedQuantity(), false, 4)
                || !quantity(fact.getDamagedReceivedQuantity(), false, 4)
                || !quantity(fact.getShortageReportedQuantity(), false, 4)
                || fact.getAcceptedReceivedQuantity().signum() != 0
                || fact.getReceiptVersion() == null
                || fact.getReceiptVersion() < 0)
        {
            return Progress.invalid();
        }
        BigDecimal classified = fact.getDamagedReceivedQuantity()
                .add(fact.getShortageReportedQuantity());
        if (classified.compareTo(fact.getAllocatedQuantity()) > 0)
        {
            return Progress.invalid();
        }
        return new Progress(fact.getDamagedReceivedQuantity(),
                fact.getShortageReportedQuantity(),
                fact.getAllocatedQuantity().subtract(classified), true);
    }

    private static GroupAudit auditDetails(
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
        Set<Long> invalidQuantity = new HashSet<>();
        Set<Long> invalidReceipt = new HashSet<>();
        for (Map.Entry<Long, List<InvTransferReceiptPlanningAllocationFact>>
                entry : groups.entrySet())
        {
            BigDecimal allocated = ZERO;
            BigDecimal returned = ZERO;
            BigDecimal shipped = null;
            BigDecimal received = null;
            for (InvTransferReceiptPlanningAllocationFact fact
                    : entry.getValue())
            {
                if (fact.getAllocatedQuantity() == null
                        || fact.getDamagedReceivedQuantity() == null
                        || fact.getShipmentDetailShippedQuantity() == null
                        || fact.getShipmentDetailReceivedQuantity() == null)
                {
                    invalidQuantity.add(entry.getKey());
                    invalidReceipt.add(entry.getKey());
                    continue;
                }
                allocated = allocated.add(fact.getAllocatedQuantity());
                returned = returned.add(fact.getDamagedReceivedQuantity());
                if (shipped != null && shipped.compareTo(
                        fact.getShipmentDetailShippedQuantity()) != 0)
                {
                    invalidQuantity.add(entry.getKey());
                }
                if (received != null && received.compareTo(
                        fact.getShipmentDetailReceivedQuantity()) != 0)
                {
                    invalidReceipt.add(entry.getKey());
                }
                shipped = fact.getShipmentDetailShippedQuantity();
                received = fact.getShipmentDetailReceivedQuantity();
            }
            if (shipped == null || allocated.compareTo(shipped) != 0)
            {
                invalidQuantity.add(entry.getKey());
            }
            if (received == null || returned.compareTo(received) != 0)
            {
                invalidReceipt.add(entry.getKey());
            }
        }
        return new GroupAudit(Set.copyOf(invalidQuantity),
                Set.copyOf(invalidReceipt));
    }

    private static void validateTracking(
            InvTransferReceiptPlanningAllocationFact fact,
            List<InvTransferReceiptPlanningSerialFact> serials,
            BigDecimal remaining, Set<String> blockers)
    {
        if (fact == null)
        {
            return;
        }
        String tracking = normalized(fact.getTrackingPolicy());
        if (!member(tracking, Set.of("lot", "serial")))
        {
            blockers.add("退回来源跟踪策略不可核验");
            return;
        }
        if ("lot".equals(tracking))
        {
            if (!serials.isEmpty())
            {
                blockers.add("批次跟踪退回分配不应包含序列号");
            }
            return;
        }
        int expected;
        try
        {
            expected = remaining.intValueExact();
        }
        catch (ArithmeticException | NullPointerException exception)
        {
            blockers.add("序列号退回剩余数量必须为整数");
            return;
        }
        if (serials.size() != expected)
        {
            blockers.add("序列号数量与退回剩余数量不一致");
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
                    || !"shipped".equals(normalized(
                            serial.getStatusAfter()))
                    || !"shipped".equals(normalized(
                            serial.getCurrentStatus()))
                    || blank(serial.getSerialNoSnapshot())
                    || !Objects.equals(serial.getSerialNoSnapshot(),
                            serial.getCurrentSerialNo()))
            {
                blockers.add("退回序列号发货审计或当前位置不可核验");
            }
        }
    }

    private static Map<Long, List<InvTransferReceiptPlanningSerialFact>>
            serialsByAllocation(
                    List<InvTransferReceiptPlanningSerialFact> serials,
                    Set<String> blockers)
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
                blockers.add("退回发货序列号审计标识重复或无效");
            }
            if (serial != null && serial.getAllocationId() != null)
            {
                result.computeIfAbsent(serial.getAllocationId(),
                        ignored -> new ArrayList<>()).add(serial);
            }
        }
        return result;
    }

    private static List<InvTransferReceiptLocationCandidate>
            quarantineLocations(
                    List<InvTransferReceiptLocationCandidate> input,
                    Long warehouseId, Set<String> blockers)
    {
        List<InvTransferReceiptLocationCandidate> values = input == null
                ? new ArrayList<>() : new ArrayList<>(input);
        values.sort(Comparator
                .comparing((InvTransferReceiptLocationCandidate value) ->
                        value == null || value.getSortOrder() == null ? 0
                                : value.getSortOrder())
                .thenComparing(value -> value == null
                        || value.getLocationId() == null ? Long.MIN_VALUE
                                : value.getLocationId()));
        List<InvTransferReceiptLocationCandidate> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (InvTransferReceiptLocationCandidate location : values)
        {
            if (location == null || !positive(location.getLocationId())
                    || !ids.add(location.getLocationId())
                    || !Objects.equals(warehouseId,
                            location.getWarehouseId())
                    || blank(location.getLocationCode())
                    || blank(location.getLocationName())
                    || !"0".equals(location.getStatus())
                    || !"0".equals(location.getVirtualFlag())
                    || !member(normalized(location.getLocationType()),
                            Set.of("storage", "quarantine")))
            {
                blockers.add("退回目标库位归属、状态或类型不可核验");
                continue;
            }
            if ("quarantine".equals(normalized(
                    location.getLocationType())))
            {
                result.add(location);
            }
        }
        return List.copyOf(result);
    }

    private static void addAuthorityBlockers(InvWarehouseStockMode mode,
            Set<String> blockers)
    {
        if (!"dual".equals(mode.getWriteMode()))
        {
            blockers.add("退回目标仓库尚未启用dual明细库存双写");
        }
        if (!"detail".equals(mode.getReadMode()))
        {
            blockers.add("退回目标仓库尚未切换detail明细库存读取");
        }
        if (!"passed".equals(mode.getReconcileStatus())
                || blank(mode.getLastReconcileBatch()))
        {
            blockers.add("退回目标仓库明细库存对账未通过或批次缺失");
        }
    }

    private static InvWarehouseStockMode normalizedMode(
            InvWarehouseStockMode input, Long warehouseId)
    {
        InvWarehouseStockMode result = new InvWarehouseStockMode();
        result.setWarehouseId(warehouseId);
        result.setWriteMode(allowed(input == null ? null
                : input.getWriteMode(), Set.of("legacy", "dual"),
                "legacy"));
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

    private static String fingerprint(InvTransferShipment shipment,
            InvTransferOrder order, InvWarehouseStockMode mode,
            List<InvTransferReceiptPlanningAllocationFact> allocations,
            List<InvTransferReceiptPlanningSerialFact> serials,
            List<InvTransferReceiptLocationCandidate> locations,
            List<Line> lines, List<String> blockers)
    {
        StringBuilder value = new StringBuilder(DATA_SOURCE)
                .append('|').append(shipment.getShipmentId())
                .append('|').append(shipment.getShipmentNo())
                .append('|').append(shipment.getTransferId())
                .append('|').append(shipment.getStatus())
                .append('|').append(shipment.getInventoryWriteVersion())
                .append('|').append(shipment.getWarehouseId())
                .append('|').append(shipment.getSourceLocationDeptId())
                .append('|').append(shipment.getCommandRequestId())
                .append('|').append(shipment.getPlanVersion())
                .append('|').append(shipment.getSealedRevisionId())
                .append('|').append(shipment.getReconcileBatch())
                .append('|').append(order.getOrderNo())
                .append('|').append(order.getVersion())
                .append('|').append(order.getStatus())
                .append('|').append(order.getTransferType())
                .append('|').append(order.getReturnReasonCode())
                .append('|').append(order.getSourceBusinessType())
                .append('|').append(order.getSourceBusinessId())
                .append('|').append(order.getApprovalRound())
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
                    .append(':').append(fact.getProductId())
                    .append(':').append(fact.getItemCode())
                    .append(':').append(fact.getItemName())
                    .append(':').append(fact.getUnit())
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
                    .append(':').append(fact.getSourceReceiptDisposition())
                    .append(':').append(fact.getQcStatus())
                    .append(':').append(fact.getLotStatus())
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
        blockers.forEach(blocker -> value.append("|blocker:")
                .append(blocker));
        return sha256(value.toString());
    }

    private static Long targetWarehouse(InvTransferOrder order)
    {
        Long value = positive(order.getToWarehouseId())
                ? order.getToWarehouseId() : order.getToDeptId();
        if (!positive(value))
        {
            throw new ServiceException("退回子调拨缺少有效目标仓库");
        }
        return value;
    }

    private static Long sourceWarehouse(InvTransferOrder order)
    {
        return positive(order.getFromWarehouseId())
                ? order.getFromWarehouseId() : order.getFromDeptId();
    }

    private static boolean quantity(BigDecimal value, boolean positive,
            int scale)
    {
        return value != null && value.scale() <= scale
                && (positive ? value.signum() > 0 : value.signum() >= 0);
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
        return member(normalized, allowed) ? normalized : fallback;
    }

    private static <T> boolean member(T value, Set<T> allowed)
    {
        return value != null && allowed.contains(value);
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

    public record Plan(String planVersion,
            InvWarehouseStockMode targetStockMode, List<Line> lines,
            List<InvTransferReceiptLocationCandidate> quarantineLocations,
            boolean canCreateReceipt, List<String> blockers)
    {
    }

    public record Line(InvTransferReceiptPlanningAllocationFact fact,
            BigDecimal returnedQuantity, BigDecimal shortageQuantity,
            BigDecimal remainingQuantity,
            BigDecimal suggestedReturnedQuantity,
            List<InvTransferReceiptPlanningSerialFact> serials,
            String status, List<String> blockers)
    {
    }

    private record GroupAudit(Set<Long> invalidQuantityDetailIds,
            Set<Long> invalidReceiptDetailIds)
    {
    }

    private record Progress(BigDecimal returned, BigDecimal shortage,
            BigDecimal remaining, boolean valid)
    {
        private static Progress invalid()
        {
            return new Progress(ZERO, ZERO, null, false);
        }
    }
}
