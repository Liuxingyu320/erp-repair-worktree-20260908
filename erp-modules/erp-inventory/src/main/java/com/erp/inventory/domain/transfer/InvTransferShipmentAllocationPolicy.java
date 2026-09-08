package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.domain.InvTransferDetail;

/** Pure fail-closed allocation rules for transfer shipment planning. */
public final class InvTransferShipmentAllocationPolicy
{
    public static final String FEFO = "FEFO";
    public static final String FIFO = "FIFO";
    public static final String TRACK_LOT = "lot";
    public static final String TRACK_SERIAL = "serial";
    public static final String READY = "ready";
    public static final String BLOCKED = "blocked";
    public static final String COMPLETE = "complete";

    private InvTransferShipmentAllocationPolicy()
    {
    }

    public static LinePlan plan(Long warehouseId, InvTransferDetail detail,
            InvItemFulfillmentPolicy policy,
            List<InvShipmentAllocationCandidate> inputCandidates)
    {
        if (warehouseId == null || warehouseId <= 0 || detail == null
                || detail.getDetailId() == null || detail.getDetailId() <= 0)
        {
            throw new ServiceException("发货规划缺少有效仓库或调拨明细");
        }
        BigDecimal approved = requirePositive(detail.getQuantity(),
                "审批数量必须大于0");
        BigDecimal shipped = nullToZero(detail.getDeliveredQuantity());
        if (shipped.signum() < 0 || shipped.compareTo(approved) > 0)
        {
            throw new ServiceException("累计发货数量不符合审批数量守恒");
        }
        BigDecimal remaining = approved.subtract(shipped);
        if (remaining.signum() == 0)
        {
            return new LinePlan(null, null, COMPLETE, approved, shipped,
                    remaining, BigDecimal.ZERO, List.of(), List.of());
        }

        String itemType = InvItemTypes.normalize(detail.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(),
                detail.getProductId());
        if (itemId == null || itemId <= 0)
        {
            return blocked(null, null, approved, shipped, remaining,
                    "物料缺少有效业务标识");
        }
        if (policy == null)
        {
            return blocked(null, null, approved, shipped, remaining,
                    "物料履约策略未配置");
        }
        if (policy.getPolicyId() == null || policy.getPolicyId() <= 0
                || policy.getVersion() == null || policy.getVersion() < 0)
        {
            return blocked(null, null, approved, shipped, remaining,
                    "物料履约策略缺少可核验版本");
        }
        String allocationPolicy = normalizeAllocationPolicy(
                policy.getAllocationPolicy());
        String trackingPolicy = normalizeTrackingPolicy(
                policy.getTrackingPolicy());
        if (!"0".equals(policy.getStatus())
                || !itemType.equals(policy.getItemType())
                || !itemId.equals(policy.getItemId()))
        {
            return blocked(allocationPolicy, trackingPolicy, approved,
                    shipped, remaining, "物料履约策略未启用或归属不一致");
        }
        if (allocationPolicy == null || trackingPolicy == null)
        {
            return blocked(allocationPolicy, trackingPolicy, approved,
                    shipped, remaining, "物料履约策略值不受支持");
        }

        List<InvShipmentAllocationCandidate> candidates = inputCandidates == null
                ? new ArrayList<>() : new ArrayList<>(inputCandidates);
        String candidateError = validateCandidates(warehouseId, itemType,
                itemId, allocationPolicy, trackingPolicy, candidates);
        if (candidateError != null)
        {
            return blocked(allocationPolicy, trackingPolicy, approved,
                    shipped, remaining, candidateError);
        }
        if (candidates.isEmpty())
        {
            return blocked(allocationPolicy, trackingPolicy, approved,
                    shipped, remaining, "没有符合条件的批次库位库存");
        }

        candidates.sort(candidateComparator(allocationPolicy));
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal pending = remaining;
        int rank = 1;
        for (InvShipmentAllocationCandidate candidate : candidates)
        {
            if (pending.signum() <= 0)
            {
                break;
            }
            BigDecimal quantity = candidate.getAvailableQuantity().min(pending);
            List<Serial> serials = selectedSerials(candidate, trackingPolicy,
                    quantity);
            allocations.add(new Allocation(candidate.getBalanceId(),
                    candidate.getLotId(), candidate.getLotNo(),
                    candidate.getExpiryDate(), candidate.getReceivedAt(),
                    candidate.getLocationId(), candidate.getLocationCode(),
                    candidate.getAvailableQuantity(), quantity,
                    candidate.getVersion(), rank++, serials));
            pending = pending.subtract(quantity);
        }
        BigDecimal suggested = remaining.subtract(pending);
        if (suggested.signum() <= 0)
        {
            return blocked(allocationPolicy, trackingPolicy, approved,
                    shipped, remaining, "没有可形成发货建议的正数库存");
        }
        return new LinePlan(allocationPolicy, trackingPolicy, READY,
                approved, shipped, remaining, suggested,
                List.copyOf(allocations), List.of());
    }

    private static String validateCandidates(Long warehouseId,
            String itemType, Long itemId, String allocationPolicy,
            String trackingPolicy,
            List<InvShipmentAllocationCandidate> candidates)
    {
        Set<Long> balanceIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        for (InvShipmentAllocationCandidate candidate : candidates)
        {
            if (candidate == null || candidate.getBalanceId() == null
                    || candidate.getLotId() == null
                    || candidate.getLocationId() == null
                    || candidate.getReceivedAt() == null
                    || candidate.getVersion() == null
                    || candidate.getVersion() < 0
                    || !warehouseId.equals(candidate.getWarehouseId())
                    || !itemType.equals(candidate.getItemType())
                    || !itemId.equals(candidate.getItemId())
                    || candidate.getAvailableQuantity() == null
                    || candidate.getAvailableQuantity().signum() <= 0)
            {
                return "批次库位候选事实不完整或归属不一致";
            }
            if (!balanceIds.add(candidate.getBalanceId()))
            {
                return "同一批次库位余额重复返回";
            }
            if (FEFO.equals(allocationPolicy)
                    && candidate.getExpiryDate() == null)
            {
                return "FEFO 物料存在缺失有效期的候选批次";
            }
            if (FIFO.equals(allocationPolicy)
                    && candidate.getExpiryDate() != null)
            {
                return "FIFO 物料出现带有效期批次，策略配置不一致";
            }
            List<InvShipmentSerialCandidate> serials = candidate.getSerials();
            if (TRACK_LOT.equals(trackingPolicy)
                    && serials != null && !serials.isEmpty())
            {
                return "非序列号物料混入序列号事实";
            }
            if (TRACK_SERIAL.equals(trackingPolicy))
            {
                if (candidate.getAvailableQuantity().stripTrailingZeros()
                        .scale() > 0)
                {
                    return "序列号物料可用数量必须为整数";
                }
                int expected;
                try
                {
                    expected = candidate.getAvailableQuantity().intValueExact();
                }
                catch (ArithmeticException exception)
                {
                    return "序列号物料可用数量超出逐件处理范围";
                }
                if (serials == null || serials.size() != expected)
                {
                    return "序列号可用事实与批次库位余额不一致";
                }
                for (InvShipmentSerialCandidate serial : serials)
                {
                    if (serial == null || serial.getSerialId() == null
                            || !candidate.getBalanceId().equals(
                                    serial.getBalanceId())
                            || !"available".equals(serial.getSerialStatus())
                            || serial.getSerialNo() == null
                            || serial.getSerialNo().isBlank()
                            || !serialIds.add(serial.getSerialId()))
                    {
                        return "序列号事实缺失、重复或归属不一致";
                    }
                }
            }
        }
        return null;
    }

    private static Comparator<InvShipmentAllocationCandidate> candidateComparator(
            String allocationPolicy)
    {
        Comparator<InvShipmentAllocationCandidate> tieBreak = Comparator
                .comparing(InvShipmentAllocationCandidate::getReceivedAt)
                .thenComparing(InvShipmentAllocationCandidate::getBalanceId);
        if (FEFO.equals(allocationPolicy))
        {
            return Comparator.comparing(
                    InvShipmentAllocationCandidate::getExpiryDate)
                    .thenComparing(tieBreak);
        }
        return tieBreak;
    }

    private static List<Serial> selectedSerials(
            InvShipmentAllocationCandidate candidate, String trackingPolicy,
            BigDecimal quantity)
    {
        if (!TRACK_SERIAL.equals(trackingPolicy))
        {
            return List.of();
        }
        int count = quantity.intValueExact();
        return candidate.getSerials().stream()
                .sorted(Comparator.comparing(
                        InvShipmentSerialCandidate::getSerialId))
                .limit(count)
                .map(serial -> new Serial(serial.getSerialId(),
                        serial.getSerialNo()))
                .toList();
    }

    private static LinePlan blocked(String allocationPolicy,
            String trackingPolicy, BigDecimal approved, BigDecimal shipped,
            BigDecimal remaining, String reason)
    {
        return new LinePlan(allocationPolicy, trackingPolicy, BLOCKED,
                approved, shipped, remaining, BigDecimal.ZERO,
                List.of(), List.of(reason));
    }

    private static String normalizeAllocationPolicy(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return FEFO.equals(normalized) || FIFO.equals(normalized)
                ? normalized : null;
    }

    private static String normalizeTrackingPolicy(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return TRACK_LOT.equals(normalized) || TRACK_SERIAL.equals(normalized)
                ? normalized : null;
    }

    private static BigDecimal requirePositive(BigDecimal value,
            String message)
    {
        if (value == null || value.signum() <= 0)
        {
            throw new ServiceException(message);
        }
        return value;
    }

    private static BigDecimal nullToZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record LinePlan(String allocationPolicy, String trackingPolicy,
            String recommendationStatus, BigDecimal approvedQuantity,
            BigDecimal shippedQuantity, BigDecimal remainingQuantity,
            BigDecimal suggestedShipmentQuantity,
            List<Allocation> allocations, List<String> blockers)
    {
    }

    public record Allocation(Long balanceId, Long lotId, String lotNo,
            java.util.Date expiryDate, java.util.Date receivedAt,
            Long locationId, String locationCode,
            BigDecimal availableQuantity, BigDecimal suggestedQuantity,
            Long balanceVersion, int policyRank, List<Serial> serials)
    {
    }

    public record Serial(Long serialId, String serialNo)
    {
    }
}
