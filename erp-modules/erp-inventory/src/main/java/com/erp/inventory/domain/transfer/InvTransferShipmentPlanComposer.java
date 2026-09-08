package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InvTransferRevisionVo;
import com.erp.inventory.domain.transfer.InvTransferShipmentAllocationPolicy.LinePlan;

/** Shared pure composer used by both read planning and locked write replay. */
public final class InvTransferShipmentPlanComposer
{
    private static final String NO_ELIGIBLE_BALANCE =
            "没有符合条件的批次库位库存";

    private InvTransferShipmentPlanComposer()
    {
    }

    public static Composition compose(InvTransferOrder order,
            InvTransferRevisionVo revision, InvWarehouseStockMode inputMode,
            List<InvTransferDetail> details,
            List<InvItemFulfillmentPolicy> policies,
            List<InvShipmentAllocationCandidate> candidates)
    {
        requireInputs(order, revision, details);
        Long warehouseId = resolveLocationDeptId(order.getFromWarehouseId(),
                order.getFromDeptId());
        InvWarehouseStockMode mode = normalizedMode(inputMode, warehouseId);
        List<String> globalBlockers = authorityBlockers(mode);
        boolean authoritative = globalBlockers.isEmpty();
        Map<String, InvItemFulfillmentPolicy> policyByItem = policies(
                policies);
        Map<String, List<InvShipmentAllocationCandidate>> candidatesByItem =
                authoritative ? groupCandidates(candidates)
                        : Map.of();

        List<Line> lines = new ArrayList<>();
        StringBuilder fingerprint = baseFingerprint(order, revision, mode);
        for (InvTransferDetail detail : details)
        {
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            InvItemFulfillmentPolicy policy = itemId == null ? null
                    : policyByItem.get(itemKey(itemType, itemId));
            List<InvShipmentAllocationCandidate> itemCandidates =
                    authoritative && itemId != null
                            ? availableCandidates(candidatesByItem.getOrDefault(
                                    itemKey(itemType, itemId), List.of()))
                            : List.of();
            LinePlan plan = InvTransferShipmentAllocationPolicy.plan(
                    warehouseId, detail, policy, itemCandidates);
            if (!authoritative
                    && !InvTransferShipmentAllocationPolicy.COMPLETE.equals(
                            plan.recommendationStatus()))
            {
                plan = authorityBlocked(plan, globalBlockers);
            }
            if (InvTransferShipmentAllocationPolicy.READY.equals(
                    plan.recommendationStatus()))
            {
                consumeCandidates(itemCandidates, plan);
            }
            lines.add(new Line(detail, itemType, itemId, policy, plan));
            appendFingerprint(fingerprint, detail, itemId, policy, plan);
        }
        boolean hasReadyLine = lines.stream().anyMatch(line ->
                InvTransferShipmentAllocationPolicy.READY.equals(
                        line.plan().recommendationStatus()));
        if (authoritative && !hasReadyLine)
        {
            globalBlockers.add("当前调拨单没有可形成发货建议的明细");
        }
        return new Composition(sha256(fingerprint.toString()), mode,
                List.copyOf(lines), globalBlockers.isEmpty() && hasReadyLine,
                List.copyOf(globalBlockers));
    }

    public static boolean canReadDetailStock(InvWarehouseStockMode mode,
            Long warehouseId)
    {
        return authorityBlockers(normalizedMode(mode, warehouseId)).isEmpty();
    }

    private static void requireInputs(InvTransferOrder order,
            InvTransferRevisionVo revision, List<InvTransferDetail> details)
    {
        if (order == null || order.getTransferId() == null
                || order.getVersion() == null || revision == null
                || revision.getRevisionId() == null || details == null
                || details.isEmpty())
        {
            throw new ServiceException("发货规划缺少可核验业务事实");
        }
    }

    private static Map<String, InvItemFulfillmentPolicy> policies(
            List<InvItemFulfillmentPolicy> values)
    {
        Map<String, InvItemFulfillmentPolicy> result = new LinkedHashMap<>();
        if (values == null)
        {
            return result;
        }
        for (InvItemFulfillmentPolicy policy : values)
        {
            if (policy != null && policy.getItemType() != null
                    && policy.getItemId() != null)
            {
                result.put(itemKey(InvItemTypes.normalize(
                        policy.getItemType()), policy.getItemId()), policy);
            }
        }
        return result;
    }

    private static Map<String, List<InvShipmentAllocationCandidate>>
            groupCandidates(List<InvShipmentAllocationCandidate> values)
    {
        Map<String, List<InvShipmentAllocationCandidate>> result =
                new LinkedHashMap<>();
        if (values == null)
        {
            return result;
        }
        for (InvShipmentAllocationCandidate candidate : values)
        {
            if (candidate != null && candidate.getItemType() != null
                    && candidate.getItemId() != null)
            {
                String key = itemKey(InvItemTypes.normalize(
                        candidate.getItemType()), candidate.getItemId());
                result.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(copy(candidate));
            }
        }
        return result;
    }

    private static List<InvShipmentAllocationCandidate> availableCandidates(
            List<InvShipmentAllocationCandidate> values)
    {
        return values.stream().filter(value -> value.getAvailableQuantity()
                != null && value.getAvailableQuantity().signum() > 0)
                .toList();
    }

    private static void consumeCandidates(
            List<InvShipmentAllocationCandidate> candidates, LinePlan plan)
    {
        Map<Long, InvShipmentAllocationCandidate> byBalance =
                new LinkedHashMap<>();
        candidates.forEach(candidate -> byBalance.put(
                candidate.getBalanceId(), candidate));
        for (InvTransferShipmentAllocationPolicy.Allocation allocation
                : plan.allocations())
        {
            InvShipmentAllocationCandidate candidate = byBalance.get(
                    allocation.balanceId());
            if (candidate == null
                    || candidate.getAvailableQuantity().compareTo(
                            allocation.suggestedQuantity()) < 0)
            {
                throw new ServiceException("发货规划跨明细库存消费不守恒");
            }
            candidate.setAvailableQuantity(candidate.getAvailableQuantity()
                    .subtract(allocation.suggestedQuantity()));
            Set<Long> selectedSerialIds = allocation.serials().stream()
                    .map(InvTransferShipmentAllocationPolicy.Serial::serialId)
                    .collect(java.util.stream.Collectors.toSet());
            candidate.setSerials(candidate.getSerials().stream()
                    .filter(serial -> !selectedSerialIds.contains(
                            serial.getSerialId()))
                    .toList());
        }
    }

    private static InvShipmentAllocationCandidate copy(
            InvShipmentAllocationCandidate source)
    {
        InvShipmentAllocationCandidate result =
                new InvShipmentAllocationCandidate();
        result.setBalanceId(source.getBalanceId());
        result.setItemType(source.getItemType());
        result.setItemId(source.getItemId());
        result.setWarehouseId(source.getWarehouseId());
        result.setLotId(source.getLotId());
        result.setLotNo(source.getLotNo());
        result.setExpiryDate(source.getExpiryDate());
        result.setReceivedAt(source.getReceivedAt());
        result.setLocationId(source.getLocationId());
        result.setLocationCode(source.getLocationCode());
        result.setAvailableQuantity(source.getAvailableQuantity());
        result.setVersion(source.getVersion());
        result.setSerials(source.getSerials());
        return result;
    }

    private static LinePlan authorityBlocked(LinePlan plan,
            List<String> globalBlockers)
    {
        LinkedHashSet<String> blockers = new LinkedHashSet<>(
                plan.blockers());
        blockers.remove(NO_ELIGIBLE_BALANCE);
        blockers.addAll(globalBlockers);
        return new LinePlan(plan.allocationPolicy(), plan.trackingPolicy(),
                InvTransferShipmentAllocationPolicy.BLOCKED,
                plan.approvedQuantity(), plan.shippedQuantity(),
                plan.remainingQuantity(), BigDecimal.ZERO, List.of(),
                List.copyOf(blockers));
    }

    private static StringBuilder baseFingerprint(InvTransferOrder order,
            InvTransferRevisionVo revision, InvWarehouseStockMode mode)
    {
        return new StringBuilder("shipment-plan-v1")
                .append('|').append(order.getTransferId())
                .append('|').append(order.getVersion())
                .append('|').append(order.getStatus())
                .append('|').append(revision.getRevisionId())
                .append('|').append(revision.getRevisionNo())
                .append('|').append(revision.getSnapshotHash())
                .append('|').append(mode.getWarehouseId())
                .append('|').append(mode.getWriteMode())
                .append('|').append(mode.getReadMode())
                .append('|').append(mode.getReconcileStatus())
                .append('|').append(mode.getLastReconcileBatch());
    }

    private static void appendFingerprint(StringBuilder fingerprint,
            InvTransferDetail detail, Long itemId,
            InvItemFulfillmentPolicy policy, LinePlan plan)
    {
        fingerprint.append("|line:").append(detail.getDetailId())
                .append(':').append(itemId)
                .append(':').append(decimal(plan.approvedQuantity()))
                .append(':').append(decimal(plan.shippedQuantity()))
                .append(':').append(policy == null ? null
                        : policy.getPolicyId())
                .append(':').append(policy == null ? null
                        : policy.getVersion())
                .append(':').append(policy == null ? null
                        : policy.getStatus())
                .append(':').append(plan.allocationPolicy())
                .append(':').append(plan.trackingPolicy())
                .append(':').append(plan.recommendationStatus());
        for (InvTransferShipmentAllocationPolicy.Allocation allocation
                : plan.allocations())
        {
            fingerprint.append("|allocation:")
                    .append(allocation.balanceId()).append(':')
                    .append(allocation.balanceVersion()).append(':')
                    .append(allocation.lotId()).append(':')
                    .append(allocation.locationId()).append(':')
                    .append(decimal(allocation.availableQuantity()))
                    .append(':')
                    .append(decimal(allocation.suggestedQuantity()));
            allocation.serials().forEach(serial -> fingerprint
                    .append(":serial:").append(serial.serialId()));
        }
        plan.blockers().forEach(blocker -> fingerprint
                .append("|blocker:").append(blocker));
    }

    private static InvWarehouseStockMode normalizedMode(
            InvWarehouseStockMode input, Long warehouseId)
    {
        InvWarehouseStockMode result = new InvWarehouseStockMode();
        result.setWarehouseId(warehouseId);
        result.setWriteMode(normalizeMode(input == null ? null
                : input.getWriteMode(), Set.of("legacy", "dual"),
                "legacy"));
        result.setReadMode(normalizeMode(input == null ? null
                : input.getReadMode(), Set.of("legacy", "shadow", "detail"),
                "legacy"));
        result.setReconcileStatus(normalizeMode(input == null ? null
                : input.getReconcileStatus(),
                Set.of("not_run", "passed", "failed"), "not_run"));
        result.setLastReconcileBatch(normalizeNullableText(input == null
                ? null : input.getLastReconcileBatch()));
        if (input != null)
        {
            result.setLastReconcileTime(input.getLastReconcileTime());
            result.setApprovedBy(input.getApprovedBy());
            result.setApprovedTime(input.getApprovedTime());
        }
        return result;
    }

    private static List<String> authorityBlockers(
            InvWarehouseStockMode mode)
    {
        List<String> blockers = new ArrayList<>();
        if (!"dual".equals(mode.getWriteMode()))
        {
            blockers.add("来源仓库尚未启用 dual 明细库存双写");
        }
        if (!"detail".equals(mode.getReadMode()))
        {
            blockers.add("来源仓库尚未切换 detail 明细库存读取");
        }
        if (!"passed".equals(mode.getReconcileStatus()))
        {
            blockers.add("来源仓库明细库存对账尚未通过");
        }
        return blockers;
    }

    private static String normalizeMode(String value, Set<String> allowed,
            String fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String normalizeNullableText(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long resolveLocationDeptId(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId > 0 ? warehouseId : deptId;
    }

    private static String itemKey(String itemType, Long itemId)
    {
        return itemType + ':' + itemId;
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("发货规划包含空数量");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String sha256(String value)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : hash)
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

    public record Composition(String planVersion,
            InvWarehouseStockMode stockMode, List<Line> lines,
            boolean canCreateShipment, List<String> blockingReasons)
    {
    }

    public record Line(InvTransferDetail detail, String itemType,
            Long itemId, InvItemFulfillmentPolicy policy, LinePlan plan)
    {
    }
}
