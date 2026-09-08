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
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest;
import com.erp.inventory.domain.dto.InvTransferReceiptDiscrepancyReturnReceiptCreateRequest;

/** Pure request boundary for an expected damaged-return receipt. */
public final class InvTransferReceiptDiscrepancyReturnReceiptRequestPolicy
{
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String BASIS =
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.DATA_SOURCE;

    private InvTransferReceiptDiscrepancyReturnReceiptRequestPolicy()
    {
    }

    public static InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
            validate(
                    InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
                            request,
                    InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan)
    {
        requireRequest(request, plan);
        Map<Long, InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line>
                readyLines = readyLines(plan.lines());
        Set<Long> quarantineLocations = locationIds(
                plan.quarantineLocations());
        BigDecimal planRemaining = totalPlanRemaining(plan.lines());

        List<InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest>
                input = new ArrayList<>(request.getAllocations());
        input.sort(Comparator.nullsFirst(Comparator.comparing(
                InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest
                        ::getShipmentAllocationId,
                Comparator.nullsFirst(Long::compareTo))));
        Set<Long> allocationIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        List<InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                .Allocation> output = new ArrayList<>();
        BigDecimal returnedTotal = ZERO;
        BigDecimal shortageTotal = ZERO;

        for (InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest value
                : input)
        {
            if (value == null || value.getShipmentAllocationId() == null
                    || value.getShipmentAllocationId() <= 0
                    || !allocationIds.add(value.getShipmentAllocationId()))
            {
                throw new ServiceException("退回收货分配标识重复或无效");
            }
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line line =
                    readyLines.get(value.getShipmentAllocationId());
            if (line == null)
            {
                throw new ServiceException("退回收货分配不在专属服务端边界内");
            }
            BigDecimal returned = quantity(value.getReturnedQuantity(),
                    "预期退回实收数量无效");
            BigDecimal shortage = quantity(value.getShortageQuantity(),
                    "退回意外短缺数量无效");
            BigDecimal classified = returned.add(shortage);
            if (classified.signum() <= 0 || line.remainingQuantity() == null
                    || classified.compareTo(line.remainingQuantity()) > 0)
            {
                throw new ServiceException(
                        "本次退回实收和短缺总量必须大于零且不能超过待收数量");
            }

            Long quarantineLocationId = location(
                    value.getQuarantineLocationId(), returned,
                    quarantineLocations);
            String discrepancyNote = trimToNull(
                    value.getDiscrepancyNote());
            String attachmentRefs = trimToNull(value.getAttachmentRefs());
            if (shortage.signum() > 0
                    && (discrepancyNote == null || attachmentRefs == null))
            {
                throw new ServiceException("退回意外短缺必须提供说明和附件引用");
            }
            if (shortage.signum() == 0
                    && (discrepancyNote != null || attachmentRefs != null))
            {
                throw new ServiceException("无短缺的退回实收不能夹带差异证据");
            }

            List<Long> returnedSerials = serials(
                    value.getReturnedSerialIds(), serialIds);
            List<Long> shortageSerials = serials(
                    value.getShortageSerialIds(), serialIds);
            validateTracking(line, returned, shortage, returnedSerials,
                    shortageSerials);

            output.add(new
                    InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                            .Allocation(value.getShipmentAllocationId(),
                                    quarantineLocationId, returned, shortage,
                                    returnedSerials, shortageSerials,
                                    discrepancyNote, attachmentRefs));
            returnedTotal = returnedTotal.add(returned);
            shortageTotal = shortageTotal.add(shortage);
        }

        BigDecimal remainingAfter = planRemaining.subtract(
                returnedTotal.add(shortageTotal));
        if (remainingAfter.signum() < 0)
        {
            throw new ServiceException("退回收货分类总量超过服务端待收总量");
        }
        boolean shouldFinalize = remainingAfter.signum() == 0;
        if (!Objects.equals(request.getFinalizeShipment(), shouldFinalize))
        {
            throw new ServiceException(shouldFinalize
                    ? "全部退回待收数量已分类，必须确认完成本批收货"
                    : "仍有退回待收数量时不能完成本批收货");
        }

        String remark = trimToNull(request.getRemark());
        Instant arrivedTime = request.getArrivedTime().toInstant();
        List<InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                .Allocation> allocations = List.copyOf(output);
        String fingerprint = fingerprint(plan.planVersion(), arrivedTime,
                shouldFinalize, allocations, remark);
        return new InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand(
                fingerprint, plan.planVersion(), arrivedTime, shouldFinalize,
                allocations, returnedTotal, shortageTotal, remainingAfter,
                remark);
    }

    private static void requireRequest(
            InvTransferReceiptDiscrepancyReturnReceiptCreateRequest request,
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan plan)
    {
        if (request == null || plan == null)
        {
            throw new ServiceException("退回收货请求或服务端规划不能为空");
        }
        if (!plan.canCreateReceipt())
        {
            throw new ServiceException("专属退回收货规划当前不可执行");
        }
        if (!Objects.equals(request.getReturnReceiptPlanVersion(),
                plan.planVersion()))
        {
            throw new ServiceException("退回收货规划已变化，请刷新后重试");
        }
        if (!BASIS.equals(request.getBasis()))
        {
            throw new ServiceException("退回收货依据无效");
        }
        if (request.getArrivedTime() == null
                || request.getFinalizeShipment() == null
                || request.getAllocations() == null
                || request.getAllocations().isEmpty()
                || request.getAllocations().size() > 10000)
        {
            throw new ServiceException("退回收货请求缺少必填事实");
        }
    }

    private static Map<Long,
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line> readyLines(
                    List<InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line>
                            lines)
    {
        Map<Long, InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line>
                result = new HashMap<>();
        for (InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line line
                : lines)
        {
            if (line != null && "ready".equals(line.status())
                    && line.fact() != null
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
            List<InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line> lines)
    {
        BigDecimal result = ZERO;
        for (InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line line
                : lines)
        {
            if (line == null || line.remainingQuantity() == null
                    || "blocked".equals(line.status()))
            {
                throw new ServiceException("专属退回收货规划包含不可核验分配");
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
            Set<Long> allowed)
    {
        if (quantity.signum() == 0)
        {
            if (locationId != null)
            {
                throw new ServiceException("零退回实收不能夹带目标隔离库位");
            }
            return null;
        }
        if (locationId == null || !allowed.contains(locationId))
        {
            throw new ServiceException("退回隔离库位不在服务端边界内");
        }
        return locationId;
    }

    private static List<Long> serials(List<Long> values, Set<Long> global)
    {
        if (values == null)
        {
            throw new ServiceException("退回收货序列号列表不能为空");
        }
        List<Long> result = new ArrayList<>(values);
        result.sort(Comparator.nullsFirst(Long::compareTo));
        for (Long value : result)
        {
            if (value == null || value <= 0 || !global.add(value))
            {
                throw new ServiceException("退回收货序列号标识重复或无效");
            }
        }
        return List.copyOf(result);
    }

    private static void validateTracking(
            InvTransferReceiptDiscrepancyReturnReceiptPolicy.Line line,
            BigDecimal returned, BigDecimal shortage,
            List<Long> returnedSerials, List<Long> shortageSerials)
    {
        String tracking = line.fact().getTrackingPolicy() == null ? null
                : line.fact().getTrackingPolicy().trim().toLowerCase();
        if ("lot".equals(tracking))
        {
            if (!returnedSerials.isEmpty() || !shortageSerials.isEmpty())
            {
                throw new ServiceException("批次跟踪退回分配不得提交序列号");
            }
            return;
        }
        if (!"serial".equals(tracking))
        {
            throw new ServiceException("退回来源分配跟踪策略无效");
        }
        requireSerialCount(returned, returnedSerials,
                "退回实收序列号数量不守恒");
        requireSerialCount(shortage, shortageSerials,
                "退回短缺序列号数量不守恒");
        Set<Long> allowed = new HashSet<>();
        line.serials().forEach(value -> allowed.add(value.getSerialId()));
        LinkedHashSet<Long> submitted = new LinkedHashSet<>();
        submitted.addAll(returnedSerials);
        submitted.addAll(shortageSerials);
        if (!allowed.containsAll(submitted))
        {
            throw new ServiceException("提交序列号不在专属退回待收边界内");
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
            List<InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                    .Allocation> allocations,
            String remark)
    {
        StringBuilder value = new StringBuilder(
                "return-receipt-request-v1")
                        .append('|').append(planVersion)
                        .append('|').append(arrivedTime.toEpochMilli())
                        .append('|').append(finalizeShipment);
        appendText(value, remark);
        for (InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
                .Allocation allocation : allocations)
        {
            value.append("|allocation:")
                    .append(allocation.shipmentAllocationId())
                    .append(':').append(allocation.quarantineLocationId())
                    .append(':').append(decimal(
                            allocation.returnedQuantity()))
                    .append(':').append(decimal(
                            allocation.shortageQuantity()));
            appendIds(value, allocation.returnedSerialIds());
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
