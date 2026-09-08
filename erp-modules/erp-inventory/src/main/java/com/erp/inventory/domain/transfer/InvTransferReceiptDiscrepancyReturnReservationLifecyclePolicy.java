package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

/** Pure conservation policy for consuming or releasing return quarantine. */
public final class
        InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
{
    public static final String ACTIVE = "ACTIVE";
    public static final String PARTIAL = "PARTIAL";
    public static final String CONSUMED = "CONSUMED";
    public static final String RELEASED = "RELEASED";
    public static final String CLOSED = "CLOSED";
    public static final String SERIAL_RESERVED = "quarantine_reserved";
    public static final String SERIAL_SHIPPED = "shipped";
    public static final String SERIAL_QUARANTINE = "quarantine";

    private static final Pattern REQUEST_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> OPEN_STATUSES = Set.of(ACTIVE, PARTIAL);
    private static final Set<String> CONSUMABLE_CHILD_STATUSES = Set.of(
            InvStatusConstants.APPROVED, InvStatusConstants.RESERVED,
            InvStatusConstants.PARTIAL_DELIVERED);
    private static final Set<String> RELEASABLE_CHILD_STATUSES = Set.of(
            InvStatusConstants.SUBMITTED, InvStatusConstants.APPROVED,
            InvStatusConstants.RESERVED,
            InvStatusConstants.PARTIAL_DELIVERED);

    private InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy()
    {
    }

    public static PreparedConsumption prepareConsumption(String requestId,
            Long shipmentId, Long shipmentDetailId, BigDecimal quantity,
            String operator, Instant occurredAt,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts)
    {
        Command command = command(requestId, operator, occurredAt);
        requireIdentifier(shipmentId, "退回发货批次标识无效");
        requireIdentifier(shipmentDetailId, "退回发货明细标识无效");
        ConsumptionPlan plan = planConsumption(quantity, fact, stock,
                balance, serialFacts);
        return new PreparedConsumption(command, fact, shipmentId,
                shipmentDetailId, plan.quantity(), plan.amount(),
                plan.reservedQuantity(), plan.consumedBefore(),
                plan.consumedAfter(), plan.releasedQuantity(),
                plan.remainingAfter(), plan.statusBefore(),
                plan.statusAfter(), plan.versionBefore(),
                plan.versionAfter(), plan.stock(), plan.balance(),
                plan.serials());
    }

    public static ConsumptionPlan planAllRemainingConsumption(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts)
    {
        Base base = base(fact, stock, balance, serialFacts,
                CONSUMABLE_CHILD_STATUSES);
        return planConsumption(base.remaining(), fact, stock, balance,
                serialFacts, base);
    }

    public static ConsumptionPlan planConsumption(BigDecimal inputQuantity,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts)
    {
        Base base = base(fact, stock, balance, serialFacts,
                CONSUMABLE_CHILD_STATUSES);
        return planConsumption(inputQuantity, fact, stock, balance,
                serialFacts, base);
    }

    private static ConsumptionPlan planConsumption(BigDecimal inputQuantity,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts,
            Base base)
    {
        BigDecimal consumed = quantity(inputQuantity, "退回发货数量无效");
        if (base.remaining().compareTo(consumed) < 0)
        {
            throw invalid("退回发货超过隔离预留剩余数量");
        }
        BigDecimal amount = consumed.multiply(base.sourceCostPrice())
                .setScale(6, RoundingMode.HALF_UP);
        QuantityTransition stockTransition = consume(stock, consumed,
                amount, "退回汇总冻结库存不足或守恒无效");
        QuantityTransition balanceTransition = consume(balance, consumed,
                amount, "退回批次库位冻结余额不足或守恒无效");
        List<SerialTransition> serials = consumeSerials(fact, balance,
                consumed, serialFacts);
        BigDecimal consumedAfter = base.consumed().add(consumed)
                .setScale(4);
        BigDecimal remainingAfter = base.reserved()
                .subtract(consumedAfter).subtract(base.released())
                .setScale(4);
        String statusAfter = remainingAfter.signum() == 0
                ? CONSUMED : PARTIAL;
        return new ConsumptionPlan(consumed, amount, base.reserved(),
                base.consumed(), consumedAfter, base.released(),
                remainingAfter, fact.getStatus(), statusAfter,
                fact.getVersion(), increment(fact.getVersion()),
                stockTransition, balanceTransition, serials);
    }

    public static PreparedRelease prepareRelease(String requestId,
            String reasonCode, String reasonReference, String operator,
            Instant occurredAt,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts)
    {
        Command command = command(requestId, operator, occurredAt);
        String normalizedReason = text(reasonCode, 32,
                "隔离预留释放原因无效");
        String normalizedReference = text(reasonReference, 128,
                "隔离预留释放引用无效");
        Base base = base(fact, stock, balance, serialFacts,
                RELEASABLE_CHILD_STATUSES);
        if (base.remaining().signum() <= 0)
        {
            throw invalid("隔离预留没有可释放剩余数量");
        }
        QuantityTransition stockTransition = release(stock,
                base.remaining(), "退回汇总冻结库存不足或释放守恒无效");
        QuantityTransition balanceTransition = release(balance,
                base.remaining(), "退回批次库位冻结余额不足或释放守恒无效");
        List<SerialTransition> serials = releaseSerials(fact, balance,
                serialFacts);
        BigDecimal releasedAfter = base.released().add(base.remaining())
                .setScale(4);
        String statusAfter = base.consumed().signum() == 0
                ? RELEASED : CLOSED;
        return new PreparedRelease(command, fact, normalizedReason,
                normalizedReference, base.remaining(), base.reserved(),
                base.consumed(), base.released(), releasedAfter,
                fact.getStatus(), statusAfter, fact.getVersion(),
                increment(fact.getVersion()), stockTransition,
                balanceTransition, serials);
    }

    private static Base base(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts,
            Set<String> allowedChildStatuses)
    {
        if (fact == null || !positive(fact.getReservationId())
                || fact.getReservationRequestId() == null
                || !REQUEST_ID.matcher(fact.getReservationRequestId())
                        .matches()
                || !positive(fact.getActionId())
                || !positive(fact.getChildTransferId())
                || !positive(fact.getChildTransferDetailId())
                || fact.getReservationRound() == null
                || fact.getReservationRound() <= 0
                || !Objects.equals(fact.getReservationRound(),
                        fact.getApprovalRound())
                || !positive(fact.getReceiptAllocationId())
                || !positive(fact.getShipmentAllocationId())
                || !positive(fact.getStockId())
                || !positive(fact.getBalanceId())
                || !positive(fact.getLotId())
                || !positive(fact.getLocationId())
                || !positive(fact.getSourceWarehouseId())
                || !Set.of("product", "oe", "gift")
                        .contains(fact.getItemType())
                || !positive(fact.getItemId())
                || ("product".equals(fact.getItemType())
                        && !positive(fact.getProductId()))
                || !Set.of("lot", "serial")
                        .contains(fact.getTrackingPolicy())
                || !OPEN_STATUSES.contains(fact.getStatus())
                || fact.getVersion() == null || fact.getVersion() < 0
                || !allowedChildStatuses.contains(fact.getChildStatus())
                || !InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .SOURCE_RETURN.equals(fact.getSourceBusinessType())
                || !Objects.equals(fact.getSourceBusinessId(),
                        fact.getActionId())
                || !positive(fact.getWorkflowId())
                || !InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .WORKFLOW_RETURN.equals(fact.getWorkflowType())
                || !InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .INVENTORY_QUARANTINE.equals(
                                fact.getInventorySource())
                || !hash(fact.getDecisionFingerprint())
                || !hash(fact.getWorkflowFingerprint()))
        {
            throw invalid("退回隔离预留归属或工作流事实无效");
        }
        BigDecimal reserved = quantity(fact.getReservedQuantity(),
                "退回隔离预留数量无效");
        BigDecimal consumed = nonNegative(fact.getConsumedQuantity(), 4,
                "退回隔离预留已消费数量无效");
        BigDecimal released = nonNegative(fact.getReleasedQuantity(), 4,
                "退回隔离预留已释放数量无效");
        BigDecimal sourceCost = nonNegative(fact.getSourceCostPrice(), 6,
                "退回隔离预留来源成本无效");
        BigDecimal amount = nonNegative(fact.getReservedAmount(), 6,
                "退回隔离预留金额无效");
        BigDecimal remaining = reserved.subtract(consumed).subtract(released)
                .setScale(4);
        if (remaining.signum() < 0
                || released.signum() != 0
                || (ACTIVE.equals(fact.getStatus())
                        && consumed.signum() != 0)
                || (PARTIAL.equals(fact.getStatus())
                        && (consumed.signum() <= 0
                            || remaining.signum() <= 0))
                || amount.compareTo(reserved.multiply(sourceCost)
                        .setScale(6, RoundingMode.HALF_UP)) != 0
                || !same(fact.getRequestedQuantity(), reserved)
                || !same(fact.getDeliveredQuantity(), consumed))
        {
            throw invalid("退回隔离预留累计数量或成本不守恒");
        }
        requireDimensions(fact, stock, balance, sourceCost);
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                values = serialFacts == null ? List.of() : serialFacts;
        if ("lot".equals(fact.getTrackingPolicy()) && !values.isEmpty())
        {
            throw invalid("批次跟踪隔离预留不得包含序列号绑定");
        }
        if ("serial".equals(fact.getTrackingPolicy()))
        {
            int expected;
            try
            {
                expected = remaining.intValueExact();
            }
            catch (ArithmeticException exception)
            {
                throw invalid("序列号隔离预留剩余数量必须为整数");
            }
            if (expected <= 0 || values.size() != expected)
            {
                throw invalid("序列号隔离预留绑定数量与剩余量不一致");
            }
        }
        return new Base(reserved, consumed, released, remaining,
                sourceCost);
    }

    private static void requireDimensions(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance, BigDecimal sourceCost)
    {
        if (stock == null || !Objects.equals(stock.getStockId(),
                fact.getStockId())
                || !Objects.equals(stock.getItemType(), fact.getItemType())
                || !Objects.equals(stock.getItemId(), fact.getItemId())
                || !Objects.equals(stock.getProductId(), fact.getProductId())
                || !Objects.equals(stock.getWarehouseId(),
                        fact.getSourceWarehouseId())
                || !Objects.equals(stock.getShopDeptId(),
                        fact.getSourceWarehouseId())
                || stock.getVersion() == null || stock.getVersion() < 0
                || balance == null || !Objects.equals(balance.getBalanceId(),
                        fact.getBalanceId())
                || !Objects.equals(balance.getItemType(), fact.getItemType())
                || !Objects.equals(balance.getItemId(), fact.getItemId())
                || !Objects.equals(balance.getProductId(),
                        fact.getProductId())
                || !Objects.equals(balance.getWarehouseId(),
                        stock.getWarehouseId())
                || !Objects.equals(balance.getLotId(), fact.getLotId())
                || !Objects.equals(balance.getLocationId(),
                        fact.getLocationId())
                || balance.getVersion() == null || balance.getVersion() < 0
                || !same(balance.getCostPrice(), sourceCost))
        {
            throw invalid("退回隔离预留库存维度或成本锚点无效");
        }
    }

    private static QuantityTransition consume(
            InvTransferReceiptTargetStock value, BigDecimal quantity,
            BigDecimal amount, String message)
    {
        return transition(value.getCurrentQuantity(),
                value.getAvailableQuantity(), value.getLockedQuantity(),
                value.getQuarantineQuantity(), value.getTotalCost(),
                value.getVersion(), quantity, amount, true, message);
    }

    private static QuantityTransition consume(
            InvTransferReceiptTargetBalance value, BigDecimal quantity,
            BigDecimal amount, String message)
    {
        return transition(value.getCurrentQuantity(),
                value.getAvailableQuantity(), value.getLockedQuantity(),
                value.getQuarantineQuantity(), value.getTotalCost(),
                value.getVersion(), quantity, amount, true, message);
    }

    private static QuantityTransition release(
            InvTransferReceiptTargetStock value, BigDecimal quantity,
            String message)
    {
        return transition(value.getCurrentQuantity(),
                value.getAvailableQuantity(), value.getLockedQuantity(),
                value.getQuarantineQuantity(), value.getTotalCost(),
                value.getVersion(), quantity, BigDecimal.ZERO.setScale(6),
                false, message);
    }

    private static QuantityTransition release(
            InvTransferReceiptTargetBalance value, BigDecimal quantity,
            String message)
    {
        return transition(value.getCurrentQuantity(),
                value.getAvailableQuantity(), value.getLockedQuantity(),
                value.getQuarantineQuantity(), value.getTotalCost(),
                value.getVersion(), quantity, BigDecimal.ZERO.setScale(6),
                false, message);
    }

    private static QuantityTransition transition(BigDecimal current,
            BigDecimal available, BigDecimal locked, BigDecimal quarantine,
            BigDecimal totalCost, Long version, BigDecimal quantity,
            BigDecimal amount, boolean consuming, String message)
    {
        BigDecimal currentBefore = nonNegative(current, 4, message);
        BigDecimal availableBefore = nonNegative(available, 4, message);
        BigDecimal lockedBefore = nonNegative(locked, 4, message);
        BigDecimal quarantineBefore = nonNegative(quarantine, 4, message);
        BigDecimal costBefore = nonNegative(totalCost, 6, message);
        if (currentBefore.compareTo(availableBefore.add(lockedBefore)
                .add(quarantineBefore)) != 0
                || lockedBefore.compareTo(quantity) < 0
                || (consuming && (currentBefore.compareTo(quantity) < 0
                        || costBefore.compareTo(amount) < 0)))
        {
            throw invalid(message);
        }
        BigDecimal currentAfter = consuming
                ? currentBefore.subtract(quantity).setScale(4)
                : currentBefore;
        BigDecimal lockedAfter = lockedBefore.subtract(quantity).setScale(4);
        BigDecimal quarantineAfter = consuming ? quarantineBefore
                : quarantineBefore.add(quantity).setScale(4);
        BigDecimal costAfter = consuming
                ? costBefore.subtract(amount).setScale(6) : costBefore;
        if (currentAfter.compareTo(availableBefore.add(lockedAfter)
                .add(quarantineAfter)) != 0)
        {
            throw invalid(message);
        }
        return new QuantityTransition(currentBefore, currentAfter,
                availableBefore, availableBefore, lockedBefore, lockedAfter,
                quarantineBefore, quarantineAfter, costBefore, costAfter,
                version, increment(version));
    }

    private static List<SerialTransition> consumeSerials(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetBalance balance, BigDecimal quantity,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    facts)
    {
        if (facts == null || facts.isEmpty())
        {
            return List.of();
        }
        int count;
        try
        {
            count = quantity.intValueExact();
        }
        catch (ArithmeticException exception)
        {
            throw invalid("序列号退回发货数量必须为整数");
        }
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                sorted = sortedSerials(fact, balance, facts);
        if (count <= 0 || sorted.size() < count)
        {
            throw invalid("序列号退回发货缺少足量冻结单件");
        }
        List<SerialTransition> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            result.add(serial(sorted.get(index), CONSUMED, SERIAL_SHIPPED));
        }
        return List.copyOf(result);
    }

    private static List<SerialTransition> releaseSerials(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    facts)
    {
        if (facts == null || facts.isEmpty())
        {
            return List.of();
        }
        return sortedSerials(fact, balance, facts).stream()
                .map(value -> serial(value, RELEASED, SERIAL_QUARANTINE))
                .toList();
    }

    private static List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
            sortedSerials(
                    InvTransferReceiptDiscrepancyReturnReservationLifecycleFact
                            fact,
                    InvTransferReceiptTargetBalance balance,
                    List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                            facts)
    {
        List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                values = new ArrayList<>(facts);
        values.sort(Comparator
                .comparing(
                        InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
                                ::getReceiptSerialId,
                        Comparator.nullsFirst(Long::compareTo))
                .thenComparing(
                        InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
                                ::getSerialId,
                        Comparator.nullsFirst(Long::compareTo)));
        Set<Long> bindingIds = new HashSet<>();
        Set<Long> receiptSerialIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        for (InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
                value : values)
        {
            if (value == null || !positive(value.getBindingId())
                    || !bindingIds.add(value.getBindingId())
                    || !Objects.equals(value.getReservationId(),
                            fact.getReservationId())
                    || !positive(value.getReceiptSerialId())
                    || !receiptSerialIds.add(value.getReceiptSerialId())
                    || !positive(value.getSerialId())
                    || !serialIds.add(value.getSerialId())
                    || value.getSerialNoSnapshot() == null
                    || value.getSerialNoSnapshot().isBlank()
                    || !Objects.equals(value.getSerialNoSnapshot(),
                            value.getCurrentSerialNo())
                    || !ACTIVE.equals(value.getLifecycleStatus())
                    || value.getVersion() == null || value.getVersion() < 0
                    || !SERIAL_RESERVED.equals(
                            value.getCurrentSerialStatus())
                    || !Objects.equals(value.getWarehouseId(),
                            balance.getWarehouseId())
                    || !Objects.equals(value.getBalanceId(),
                            fact.getBalanceId())
                    || !Objects.equals(value.getLotId(), fact.getLotId())
                    || !Objects.equals(value.getLocationId(),
                            fact.getLocationId())
                    || !Objects.equals(value.getWarehouseId(),
                            value.getCurrentWarehouseId())
                    || !Objects.equals(value.getBalanceId(),
                            value.getCurrentBalanceId())
                    || !Objects.equals(value.getLotId(),
                            value.getCurrentLotId())
                    || !Objects.equals(value.getLocationId(),
                            value.getCurrentLocationId()))
            {
                throw invalid("退回隔离序列号绑定事实无效");
            }
        }
        return values;
    }

    private static SerialTransition serial(
            InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact
                    value,
            String lifecycleAfter, String physicalAfter)
    {
        return new SerialTransition(value.getBindingId(),
                value.getReceiptSerialId(), value.getSerialId(),
                value.getSerialNoSnapshot(), value.getVersion(),
                increment(value.getVersion()), ACTIVE, lifecycleAfter,
                SERIAL_RESERVED, physicalAfter);
    }

    private static Command command(String requestId, String operator,
            Instant occurredAt)
    {
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches())
        {
            throw invalid("隔离预留生命周期请求标识无效");
        }
        String normalizedOperator = text(operator, 64,
                "隔离预留生命周期操作人无效");
        if (occurredAt == null || !occurredAt.equals(
                occurredAt.truncatedTo(ChronoUnit.SECONDS)))
        {
            throw invalid("隔离预留生命周期时间无效");
        }
        return new Command(requestId, normalizedOperator, occurredAt);
    }

    private static String text(String value, int max, String message)
    {
        if (value == null || value.isBlank()
                || !value.equals(value.trim()) || value.length() > max)
        {
            throw invalid(message);
        }
        return value;
    }

    private static BigDecimal quantity(BigDecimal value, String message)
    {
        BigDecimal result = nonNegative(value, 4, message);
        if (result.signum() <= 0)
        {
            throw invalid(message);
        }
        return result;
    }

    private static BigDecimal nonNegative(BigDecimal value, int scale,
            String message)
    {
        if (value == null)
        {
            throw invalid(message);
        }
        try
        {
            BigDecimal result = value.setScale(scale,
                    RoundingMode.UNNECESSARY);
            if (result.signum() < 0)
            {
                throw invalid(message);
            }
            return result;
        }
        catch (ArithmeticException exception)
        {
            throw invalid(message);
        }
    }

    private static long increment(Long value)
    {
        if (value == null || value < 0 || value == Long.MAX_VALUE)
        {
            throw invalid("隔离预留生命周期版本无效");
        }
        return value + 1;
    }

    private static void requireIdentifier(Long value, String message)
    {
        if (!positive(value))
        {
            throw invalid(message);
        }
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean hash(String value)
    {
        return value != null && HASH.matcher(value).matches();
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static ServiceException invalid(String message)
    {
        return new ServiceException(message);
    }

    private record Base(BigDecimal reserved, BigDecimal consumed,
            BigDecimal released, BigDecimal remaining,
            BigDecimal sourceCostPrice)
    {
    }

    public record Command(String requestId, String operator,
            Instant occurredAt)
    {
    }

    public record QuantityTransition(BigDecimal currentBefore,
            BigDecimal currentAfter, BigDecimal availableBefore,
            BigDecimal availableAfter, BigDecimal lockedBefore,
            BigDecimal lockedAfter, BigDecimal quarantineBefore,
            BigDecimal quarantineAfter, BigDecimal totalCostBefore,
            BigDecimal totalCostAfter, Long versionBefore,
            Long versionAfter)
    {
    }

    public record SerialTransition(Long bindingId, Long receiptSerialId,
            Long serialId, String serialNo, Long versionBefore,
            Long versionAfter, String lifecycleBefore,
            String lifecycleAfter, String physicalBefore,
            String physicalAfter)
    {
    }

    public record ConsumptionPlan(BigDecimal quantity, BigDecimal amount,
            BigDecimal reservedQuantity, BigDecimal consumedBefore,
            BigDecimal consumedAfter, BigDecimal releasedQuantity,
            BigDecimal remainingAfter, String statusBefore,
            String statusAfter, Long versionBefore, Long versionAfter,
            QuantityTransition stock, QuantityTransition balance,
            List<SerialTransition> serials)
    {
        public ConsumptionPlan
        {
            serials = List.copyOf(serials);
        }
    }

    public record PreparedConsumption(Command command,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            Long shipmentId, Long shipmentDetailId, BigDecimal quantity,
            BigDecimal amount, BigDecimal reservedQuantity,
            BigDecimal consumedBefore, BigDecimal consumedAfter,
            BigDecimal releasedQuantity, BigDecimal remainingAfter,
            String statusBefore, String statusAfter, Long versionBefore,
            Long versionAfter, QuantityTransition stock,
            QuantityTransition balance, List<SerialTransition> serials)
    {
        public PreparedConsumption
        {
            serials = List.copyOf(serials);
        }
    }

    public record PreparedRelease(Command command,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            String reasonCode, String reasonReference, BigDecimal quantity,
            BigDecimal reservedQuantity, BigDecimal consumedQuantity,
            BigDecimal releasedBefore, BigDecimal releasedAfter,
            String statusBefore, String statusAfter, Long versionBefore,
            Long versionAfter, QuantityTransition stock,
            QuantityTransition balance, List<SerialTransition> serials)
    {
        public PreparedRelease
        {
            serials = List.copyOf(serials);
        }
    }
}
