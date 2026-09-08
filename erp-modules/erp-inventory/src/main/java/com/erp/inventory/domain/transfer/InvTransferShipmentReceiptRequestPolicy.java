package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferShipmentReceiptCreateRequest;

/** Pure request boundary evaluated only against a server-authored plan. */
public final class InvTransferShipmentReceiptRequestPolicy
{
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private InvTransferShipmentReceiptRequestPolicy()
    {
    }

    public static InvTransferShipmentReceiptValidatedCommand validate(
            InvTransferShipmentReceiptCreateRequest request,
            InvTransferShipmentReceiptPlanComposer.Composition plan)
    {
        requireRequest(request, plan);
        Map<Long, InvTransferShipmentReceiptPlanComposer.Line> readyLines =
                readyLines(plan.lines());
        Set<Long> acceptedLocations = locationIds(
                plan.acceptedLocations());
        Set<Long> quarantineLocations = locationIds(
                plan.quarantineLocations());
        BigDecimal planRemaining = totalPlanRemaining(plan.lines());

        List<InvTransferShipmentReceiptAllocationRequest> input =
                new ArrayList<>(request.getAllocations());
        input.sort(Comparator.nullsFirst(Comparator.comparing(
                InvTransferShipmentReceiptAllocationRequest
                        ::getShipmentAllocationId,
                Comparator.nullsFirst(Long::compareTo))));
        Set<Long> allocationIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        List<InvTransferShipmentReceiptValidatedCommand.Allocation> output =
                new ArrayList<>();
        BigDecimal acceptedTotal = ZERO;
        BigDecimal damagedTotal = ZERO;
        BigDecimal shortageTotal = ZERO;

        for (InvTransferShipmentReceiptAllocationRequest value : input)
        {
            if (value == null || value.getShipmentAllocationId() == null
                    || value.getShipmentAllocationId() <= 0
                    || !allocationIds.add(value.getShipmentAllocationId()))
            {
                throw new ServiceException("收货分配标识重复或无效");
            }
            InvTransferShipmentReceiptPlanComposer.Line line = readyLines.get(
                    value.getShipmentAllocationId());
            if (line == null)
            {
                throw new ServiceException("收货分配不在服务端可执行边界内");
            }
            BigDecimal accepted = quantity(value.getAcceptedQuantity(),
                    "合格收货数量无效");
            BigDecimal damaged = quantity(value.getDamagedQuantity(),
                    "残损收货数量无效");
            BigDecimal shortage = quantity(value.getShortageQuantity(),
                    "显式短缺数量无效");
            BigDecimal classified = accepted.add(damaged).add(shortage);
            if (classified.signum() <= 0 || line.remainingQuantity() == null
                    || classified.compareTo(line.remainingQuantity()) > 0)
            {
                throw new ServiceException("本次分类数量必须大于零且不能超过待收数量");
            }

            Long acceptedLocationId = location(value.getAcceptedLocationId(),
                    accepted, acceptedLocations, "合格收货库位不在服务端边界内");
            Long quarantineLocationId = location(
                    value.getQuarantineLocationId(), damaged,
                    quarantineLocations, "残损隔离库位不在服务端边界内");
            String discrepancyNote = trimToNull(value.getDiscrepancyNote());
            String attachmentRefs = trimToNull(value.getAttachmentRefs());
            if ((damaged.signum() > 0 || shortage.signum() > 0)
                    && (discrepancyNote == null || attachmentRefs == null))
            {
                throw new ServiceException("残损或短缺必须提供说明和附件引用");
            }

            List<Long> acceptedSerials = serials(
                    value.getAcceptedSerialIds(), serialIds);
            List<Long> damagedSerials = serials(
                    value.getDamagedSerialIds(), serialIds);
            List<Long> shortageSerials = serials(
                    value.getShortageSerialIds(), serialIds);
            validateTracking(line, accepted, damaged, shortage,
                    acceptedSerials, damagedSerials, shortageSerials);

            output.add(new InvTransferShipmentReceiptValidatedCommand
                    .Allocation(value.getShipmentAllocationId(),
                            acceptedLocationId, accepted,
                            quarantineLocationId, damaged, shortage,
                            acceptedSerials, damagedSerials, shortageSerials,
                            discrepancyNote, attachmentRefs));
            acceptedTotal = acceptedTotal.add(accepted);
            damagedTotal = damagedTotal.add(damaged);
            shortageTotal = shortageTotal.add(shortage);
        }

        BigDecimal classifiedTotal = acceptedTotal.add(damagedTotal)
                .add(shortageTotal);
        BigDecimal remainingAfter = planRemaining.subtract(classifiedTotal);
        if (remainingAfter.signum() < 0)
        {
            throw new ServiceException("收货分类总量超过服务端待收总量");
        }
        boolean shouldFinalize = remainingAfter.signum() == 0;
        if (!Objects.equals(request.getFinalizeShipment(), shouldFinalize))
        {
            throw new ServiceException(shouldFinalize
                    ? "全部待收数量已分类，必须确认完成本批收货"
                    : "仍有待收数量时不能完成本批收货");
        }

        String remark = trimToNull(request.getRemark());
        Instant arrivedTime = request.getArrivedTime().toInstant();
        List<InvTransferShipmentReceiptValidatedCommand.Allocation>
                allocations = List.copyOf(output);
        String fingerprint = fingerprint(request.getReceiptPlanVersion(),
                arrivedTime, shouldFinalize, allocations, remark);
        return new InvTransferShipmentReceiptValidatedCommand(fingerprint,
                request.getReceiptPlanVersion(), arrivedTime,
                shouldFinalize, allocations, acceptedTotal, damagedTotal,
                shortageTotal, remainingAfter, remark);
    }

    private static void requireRequest(
            InvTransferShipmentReceiptCreateRequest request,
            InvTransferShipmentReceiptPlanComposer.Composition plan)
    {
        if (request == null || plan == null)
        {
            throw new ServiceException("收货请求或服务端规划不能为空");
        }
        if (!plan.canCreateReceipt())
        {
            throw new ServiceException("服务端收货规划当前不可执行");
        }
        if (!Objects.equals(request.getReceiptPlanVersion(),
                plan.receiptPlanVersion()))
        {
            throw new ServiceException("收货规划已变化，请刷新后重试");
        }
        if (!"server-recommendation".equals(request.getBasis()))
        {
            throw new ServiceException("收货依据无效");
        }
        if (request.getArrivedTime() == null
                || request.getFinalizeShipment() == null
                || request.getAllocations() == null
                || request.getAllocations().isEmpty())
        {
            throw new ServiceException("收货请求缺少必填事实");
        }
    }

    private static Map<Long, InvTransferShipmentReceiptPlanComposer.Line>
            readyLines(
                    List<InvTransferShipmentReceiptPlanComposer.Line> lines)
    {
        Map<Long, InvTransferShipmentReceiptPlanComposer.Line> result =
                new HashMap<>();
        for (InvTransferShipmentReceiptPlanComposer.Line line : lines)
        {
            if (line != null && "ready".equals(
                    line.recommendationStatus()) && line.fact() != null
                    && line.fact().getAllocationId() != null)
            {
                result.put(line.fact().getAllocationId(), line);
            }
        }
        return result;
    }

    private static Set<Long> locationIds(
            List<InvTransferReceiptLocationCandidate> locations)
    {
        Set<Long> result = new HashSet<>();
        for (InvTransferReceiptLocationCandidate location : locations)
        {
            if (location != null && location.getLocationId() != null)
            {
                result.add(location.getLocationId());
            }
        }
        return result;
    }

    private static BigDecimal totalPlanRemaining(
            List<InvTransferShipmentReceiptPlanComposer.Line> lines)
    {
        BigDecimal result = ZERO;
        for (InvTransferShipmentReceiptPlanComposer.Line line : lines)
        {
            if (line == null || line.remainingQuantity() == null
                    || "blocked".equals(line.recommendationStatus()))
            {
                throw new ServiceException("服务端收货规划包含不可核验分配");
            }
            result = result.add(line.remainingQuantity());
        }
        return result;
    }

    private static BigDecimal quantity(BigDecimal value, String message)
    {
        if (value == null || value.signum() < 0 || value.scale() > 4)
        {
            throw new ServiceException(message);
        }
        return value.signum() == 0 ? ZERO : value.stripTrailingZeros();
    }

    private static Long location(Long locationId, BigDecimal quantity,
            Set<Long> allowed, String message)
    {
        if (quantity.signum() == 0)
        {
            if (locationId != null)
            {
                throw new ServiceException("零数量不能夹带目标库位");
            }
            return null;
        }
        if (locationId == null || !allowed.contains(locationId))
        {
            throw new ServiceException(message);
        }
        return locationId;
    }

    private static List<Long> serials(List<Long> values, Set<Long> global)
    {
        if (values == null)
        {
            throw new ServiceException("序列号列表不能为空");
        }
        List<Long> result = new ArrayList<>(values);
        result.sort(Comparator.nullsFirst(Long::compareTo));
        for (Long value : result)
        {
            if (value == null || value <= 0 || !global.add(value))
            {
                throw new ServiceException("序列号标识重复或无效");
            }
        }
        return List.copyOf(result);
    }

    private static void validateTracking(
            InvTransferShipmentReceiptPlanComposer.Line line,
            BigDecimal accepted, BigDecimal damaged, BigDecimal shortage,
            List<Long> acceptedSerials, List<Long> damagedSerials,
            List<Long> shortageSerials)
    {
        String tracking = line.fact().getTrackingPolicy() == null ? null
                : line.fact().getTrackingPolicy().trim().toLowerCase();
        if ("lot".equals(tracking))
        {
            if (!acceptedSerials.isEmpty() || !damagedSerials.isEmpty()
                    || !shortageSerials.isEmpty())
            {
                throw new ServiceException("批次跟踪分配不得提交序列号");
            }
            return;
        }
        if (!"serial".equals(tracking))
        {
            throw new ServiceException("来源分配跟踪策略无效");
        }
        requireSerialCount(accepted, acceptedSerials, "合格序列号数量不守恒");
        requireSerialCount(damaged, damagedSerials, "残损序列号数量不守恒");
        requireSerialCount(shortage, shortageSerials, "短缺序列号数量不守恒");
        Set<Long> allowed = new HashSet<>();
        line.serials().forEach(value -> allowed.add(value.getSerialId()));
        LinkedHashSet<Long> submitted = new LinkedHashSet<>();
        submitted.addAll(acceptedSerials);
        submitted.addAll(damagedSerials);
        submitted.addAll(shortageSerials);
        if (!allowed.containsAll(submitted))
        {
            throw new ServiceException("提交序列号不在服务端待收边界内");
        }
    }

    private static void requireSerialCount(BigDecimal quantity,
            List<Long> serials, String message)
    {
        try
        {
            if (quantity.intValueExact() != serials.size())
            {
                throw new ServiceException(message);
            }
        }
        catch (ArithmeticException exception)
        {
            throw new ServiceException(message);
        }
    }

    private static String fingerprint(String planVersion, Instant arrivedTime,
            boolean finalizeShipment,
            List<InvTransferShipmentReceiptValidatedCommand.Allocation>
                    allocations,
            String remark)
    {
        StringBuilder value = new StringBuilder("receipt-request-v1")
                .append('|').append(planVersion)
                .append('|').append(arrivedTime.toEpochMilli())
                .append('|').append(finalizeShipment);
        appendText(value, remark);
        for (InvTransferShipmentReceiptValidatedCommand.Allocation allocation
                : allocations)
        {
            value.append("|allocation:")
                    .append(allocation.shipmentAllocationId())
                    .append(':').append(allocation.acceptedLocationId())
                    .append(':').append(decimal(
                            allocation.acceptedQuantity()))
                    .append(':').append(allocation.quarantineLocationId())
                    .append(':').append(decimal(
                            allocation.damagedQuantity()))
                    .append(':').append(decimal(
                            allocation.shortageQuantity()));
            appendIds(value, allocation.acceptedSerialIds());
            appendIds(value, allocation.damagedSerialIds());
            appendIds(value, allocation.shortageSerialIds());
            appendText(value, allocation.discrepancyNote());
            appendText(value, allocation.attachmentRefs());
        }
        return sha256(value.toString());
    }

    private static void appendIds(StringBuilder value, List<Long> ids)
    {
        value.append('|').append(ids.size()).append(':');
        ids.forEach(id -> value.append(id).append(','));
    }

    private static void appendText(StringBuilder value, String text)
    {
        value.append('|');
        if (text == null)
        {
            value.append("null");
            return;
        }
        value.append(text.length()).append(':').append(text);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String trimToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
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
}
