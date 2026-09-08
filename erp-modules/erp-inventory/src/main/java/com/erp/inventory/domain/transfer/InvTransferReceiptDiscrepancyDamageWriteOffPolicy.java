package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;

/** Pure conservation and deterministic-serial policy for damaged write-off. */
public final class InvTransferReceiptDiscrepancyDamageWriteOffPolicy
{
    public static final String EFFECT_KIND =
            "quarantine_write_off_and_loss_ledger";
    public static final String LOSS_DAMAGED_GOODS = "damaged_goods";
    public static final String LOSS_TRANSPORT_DAMAGE = "transport_damage";

    private static final Set<String> ACTION_TYPES = Set.of(
            "damage_write_off", "transport_loss_write_off");

    private InvTransferReceiptDiscrepancyDamageWriteOffPolicy()
    {
    }

    public static Prepared prepare(PreparedExecution execution,
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            BigDecimal writtenOffQuantity,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetLot lot,
            InvTransferReceiptLocationCandidate location,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact>
                    serialFacts)
    {
        validateExecution(execution);
        requireFact(execution, fact, writtenOffQuantity);
        requireDimensions(fact, stock, lot, location, balance);

        BigDecimal quantity = normalized(execution.quantity(), 4);
        BigDecimal amount = normalized(execution.amount(), 6);
        Totals stockTotals = totals(stock.getCurrentQuantity(),
                stock.getQuarantineQuantity(), stock.getTotalCost(), quantity,
                amount, "受损写销汇总库存不足或成本不可核验");
        Totals balanceTotals = totals(balance.getCurrentQuantity(),
                balance.getQuarantineQuantity(), balance.getTotalCost(),
                quantity, amount, "受损写销隔离余额不足或成本不可核验");
        if (balance.getAvailableQuantity().signum() != 0)
        {
            throw invalid("受损写销隔离余额混入可用数量");
        }

        List<Serial> serials = serials(fact, quantity, serialFacts);
        String lossType = "damage_write_off".equals(execution.actionType())
                ? LOSS_DAMAGED_GOODS : LOSS_TRANSPORT_DAMAGE;
        BigDecimal allocationWrittenOffBefore = normalized(
                writtenOffQuantity, 4);
        return new Prepared(execution, fact, lossType,
                stockLedgerRequestId(execution), quantity, amount,
                allocationWrittenOffBefore,
                allocationWrittenOffBefore.add(quantity).setScale(4),
                stock.getStockId(), stock.getVersion(),
                increment(stock.getVersion()), stockTotals.currentBefore(),
                stockTotals.currentAfter(), stock.getAvailableQuantity(),
                stock.getLockedQuantity(), stockTotals.quarantineBefore(),
                stockTotals.quarantineAfter(), stockTotals.totalCostBefore(),
                stockTotals.totalCostAfter(), stockTotals.costPriceAfter(),
                balance.getBalanceId(), balance.getVersion(),
                increment(balance.getVersion()),
                balanceTotals.currentBefore(), balanceTotals.currentAfter(),
                balance.getAvailableQuantity(), balance.getLockedQuantity(),
                balanceTotals.quarantineBefore(),
                balanceTotals.quarantineAfter(),
                balanceTotals.totalCostBefore(),
                balanceTotals.totalCostAfter(),
                balanceTotals.costPriceAfter(), serials);
    }

    public static void validateExecution(PreparedExecution value)
    {
        if (value == null || !requestId(value.requestId())
                || !positive(value.caseId())
                || !positive(value.adjudicationId())
                || !positive(value.actionId())
                || value.actionSequence() == null
                || value.actionSequence() <= 0
                || !"damaged".equals(value.discrepancyType())
                || !ACTION_TYPES.contains(value.actionType())
                || !EFFECT_KIND.equals(value.effectKind())
                || !"dispatch".equals(value.command())
                || value.effectReference() != null
                || !"pending".equals(value.actionStatusBefore())
                || !"completed".equals(value.actionStatusAfter())
                || value.executionVersionBefore() == null
                || value.executionVersionBefore() != 0
                || value.executionVersionAfter() == null
                || !exactlyNext(value.executionVersionBefore(),
                        value.executionVersionAfter())
                || value.caseVersionBefore() == null
                || value.caseVersionBefore() < 0
                || value.caseVersionAfter() == null
                || !exactlyNext(value.caseVersionBefore(),
                        value.caseVersionAfter())
                || !Set.of("adjudication_planned",
                        "adjudication_executing").contains(
                                value.caseStatusBefore())
                || !Set.of("adjudication_executing", "resolved")
                        .contains(value.caseStatusAfter())
                || !Objects.equals(value.caseStatusBefore(),
                        value.planStatusBefore())
                || !Objects.equals(value.caseStatusAfter(),
                        value.planStatusAfter())
                || !quantity(value.quantity()) || !amount(value.amount())
                || !amount(value.sourceCostPrice())
                || !Set.of("source", "target", "carrier", "company")
                        .contains(value.responsibleParty())
                || !InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .REQUIRED_PERMISSION.equals(
                                value.requiredPermission())
                || !positive(value.executorUserId())
                || blank(value.executorName())
                || value.executorName().length() > 64
                || value.executedAt() == null
                || !hash(value.decisionFingerprint())
                || !hash(value.eventFingerprint()))
        {
            throw invalid("受损隔离库存写销执行事实无效");
        }
    }

    private static void requireFact(PreparedExecution execution,
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            BigDecimal writtenOffQuantity)
    {
        BigDecimal prior = normalized(writtenOffQuantity, 4);
        if (fact == null || !positive(fact.getCaseId())
                || !Objects.equals(fact.getCaseId(), execution.caseId())
                || !positive(fact.getReceiptId())
                || !positive(fact.getReceiptAllocationId())
                || !positive(fact.getShipmentId())
                || !positive(fact.getTransferId())
                || !positive(fact.getShipmentAllocationId())
                || !positive(fact.getTargetWarehouseId())
                || blank(fact.getItemType()) || !positive(fact.getItemId())
                || !Set.of("lot", "serial").contains(
                        fact.getTrackingPolicy())
                || !quantity(fact.getDamagedQuantity())
                || !amount(fact.getSourceCostPrice())
                || !amount(fact.getDamagedCost())
                || fact.getSourceCostPrice()
                        .multiply(fact.getDamagedQuantity())
                        .setScale(6, RoundingMode.HALF_UP)
                        .compareTo(fact.getDamagedCost()) != 0
                || !positive(fact.getDamagedTargetLotId())
                || !positive(fact.getQuarantineLocationId())
                || !positive(fact.getDamagedTargetBalanceId())
                || prior.signum() < 0
                || !same(fact.getSourceCostPrice(),
                        execution.sourceCostPrice())
                || prior.add(execution.quantity()).compareTo(
                        fact.getDamagedQuantity()) > 0)
        {
            throw invalid("受损写销收货分配锚点或剩余数量无效");
        }
    }

    private static void requireDimensions(
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetLot lot,
            InvTransferReceiptLocationCandidate location,
            InvTransferReceiptTargetBalance balance)
    {
        if (stock == null || !positive(stock.getStockId())
                || !Objects.equals(stock.getItemType(), fact.getItemType())
                || !Objects.equals(stock.getItemId(), fact.getItemId())
                || !Objects.equals(stock.getProductId(), fact.getProductId())
                || !Objects.equals(stock.getShopDeptId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(stock.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !quantityOrZero(stock.getCurrentQuantity())
                || !quantityOrZero(stock.getAvailableQuantity())
                || !quantityOrZero(stock.getLockedQuantity())
                || !quantityOrZero(stock.getQuarantineQuantity())
                || !amount(stock.getTotalCost())
                || stock.getVersion() == null || stock.getVersion() < 0
                || stock.getCurrentQuantity().compareTo(
                        stock.getAvailableQuantity()
                                .add(stock.getLockedQuantity())
                                .add(stock.getQuarantineQuantity())) != 0)
        {
            throw invalid("受损写销汇总库存维度或守恒无效");
        }
        if (lot == null
                || !Objects.equals(lot.getLotId(),
                        fact.getDamagedTargetLotId())
                || !Objects.equals(lot.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(lot.getItemType(), fact.getItemType())
                || !Objects.equals(lot.getItemId(), fact.getItemId())
                || !Objects.equals(lot.getProductId(), fact.getProductId())
                || !"damaged".equals(lot.getReceiptDisposition())
                || !"quarantine".equals(lot.getQcStatus())
                || !"active".equals(lot.getLotStatus()))
        {
            throw invalid("受损写销派生批次无效");
        }
        if (location == null
                || !Objects.equals(location.getLocationId(),
                        fact.getQuarantineLocationId())
                || !Objects.equals(location.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !"quarantine".equals(location.getLocationType())
                || !"0".equals(location.getStatus())
                || !"0".equals(location.getVirtualFlag()))
        {
            throw invalid("受损写销隔离库位无效");
        }
        if (balance == null
                || !Objects.equals(balance.getBalanceId(),
                        fact.getDamagedTargetBalanceId())
                || !Objects.equals(balance.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(balance.getItemType(), fact.getItemType())
                || !Objects.equals(balance.getItemId(), fact.getItemId())
                || !Objects.equals(balance.getProductId(),
                        fact.getProductId())
                || !Objects.equals(balance.getLotId(),
                        fact.getDamagedTargetLotId())
                || !Objects.equals(balance.getLocationId(),
                        fact.getQuarantineLocationId())
                || !quantityOrZero(balance.getCurrentQuantity())
                || !quantityOrZero(balance.getAvailableQuantity())
                || !quantityOrZero(balance.getLockedQuantity())
                || !quantityOrZero(balance.getQuarantineQuantity())
                || !amount(balance.getTotalCost())
                || balance.getVersion() == null || balance.getVersion() < 0
                || balance.getCurrentQuantity().compareTo(
                        balance.getAvailableQuantity()
                                .add(balance.getLockedQuantity())
                                .add(balance.getQuarantineQuantity())) != 0)
        {
            throw invalid("受损写销隔离余额维度或守恒无效");
        }
    }

    private static Totals totals(BigDecimal current,
            BigDecimal quarantine, BigDecimal totalCost,
            BigDecimal quantity, BigDecimal amount, String message)
    {
        BigDecimal currentBefore = normalized(current, 4);
        BigDecimal quarantineBefore = normalized(quarantine, 4);
        BigDecimal costBefore = normalized(totalCost, 6);
        if (currentBefore.compareTo(quantity) < 0
                || quarantineBefore.compareTo(quantity) < 0
                || costBefore.compareTo(amount) < 0)
        {
            throw invalid(message);
        }
        BigDecimal currentAfter = currentBefore.subtract(quantity)
                .setScale(4);
        BigDecimal quarantineAfter = quarantineBefore.subtract(quantity)
                .setScale(4);
        BigDecimal costAfter = costBefore.subtract(amount).setScale(6);
        if (currentAfter.signum() == 0 && costAfter.signum() != 0)
        {
            throw invalid(message);
        }
        BigDecimal priceAfter = currentAfter.signum() == 0
                ? BigDecimal.ZERO.setScale(6)
                : costAfter.divide(currentAfter, 6, RoundingMode.HALF_UP);
        return new Totals(currentBefore, currentAfter, quarantineBefore,
                quarantineAfter, costBefore, costAfter, priceAfter);
    }

    private static List<Serial> serials(
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            BigDecimal quantity,
            List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact>
                    serialFacts)
    {
        List<InvTransferReceiptDiscrepancyDamageWriteOffSerialFact> values =
                serialFacts == null ? List.of() : new ArrayList<>(serialFacts);
        values.sort(Comparator
                .comparing(
                        InvTransferReceiptDiscrepancyDamageWriteOffSerialFact
                                ::getReceiptSerialId,
                        Comparator.nullsFirst(Long::compareTo))
                .thenComparing(
                        InvTransferReceiptDiscrepancyDamageWriteOffSerialFact
                                ::getSerialId,
                        Comparator.nullsFirst(Long::compareTo)));
        if ("lot".equals(fact.getTrackingPolicy()))
        {
            if (!values.isEmpty())
            {
                throw invalid("批次跟踪受损写销不得包含序列号");
            }
            return List.of();
        }
        int expected;
        try
        {
            expected = quantity.intValueExact();
        }
        catch (ArithmeticException invalid)
        {
            throw invalid("序列号受损写销数量必须为整数");
        }
        if (expected <= 0 || values.size() < expected)
        {
            throw invalid("序列号受损写销缺少足量隔离单件");
        }
        Set<Long> receiptSerialIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        List<Serial> selected = new ArrayList<>(expected);
        for (InvTransferReceiptDiscrepancyDamageWriteOffSerialFact value
                : values)
        {
            if (value == null || !positive(value.getReceiptSerialId())
                    || !receiptSerialIds.add(value.getReceiptSerialId())
                    || !positive(value.getSerialId())
                    || !serialIds.add(value.getSerialId())
                    || !Objects.equals(value.getReceiptId(),
                            fact.getReceiptId())
                    || !Objects.equals(value.getReceiptAllocationId(),
                            fact.getReceiptAllocationId())
                    || !Objects.equals(value.getShipmentId(),
                            fact.getShipmentId())
                    || !Objects.equals(value.getShipmentAllocationId(),
                            fact.getShipmentAllocationId())
                    || !Objects.equals(value.getItemType(), fact.getItemType())
                    || !Objects.equals(value.getItemId(), fact.getItemId())
                    || !Objects.equals(value.getCurrentWarehouseId(),
                            fact.getTargetWarehouseId())
                    || !Objects.equals(value.getCurrentBalanceId(),
                            fact.getDamagedTargetBalanceId())
                    || !Objects.equals(value.getCurrentLotId(),
                            fact.getDamagedTargetLotId())
                    || !Objects.equals(value.getCurrentLocationId(),
                            fact.getQuarantineLocationId())
                    || !"damaged".equals(value.getDisposition())
                    || !"quarantine".equals(value.getReceiptStatusAfter())
                    || !"quarantine".equals(value.getCurrentStatus())
                    || blank(value.getSerialNoSnapshot())
                    || !Objects.equals(value.getSerialNoSnapshot(),
                            value.getCurrentSerialNo()))
            {
                throw invalid("受损写销序列号审计或当前位置无效");
            }
            if (selected.size() < expected)
            {
                selected.add(new Serial(value.getReceiptSerialId(),
                        value.getSerialId(), value.getSerialNoSnapshot()));
            }
        }
        return List.copyOf(selected);
    }

    private static String stockLedgerRequestId(PreparedExecution execution)
    {
        String value = "adjdmg:" + execution.actionId() + ":"
                + execution.executionVersionBefore();
        if (value.length() > 100)
        {
            throw invalid("受损写销库存流水幂等标识过长");
        }
        return value;
    }

    private static long increment(Long value)
    {
        try
        {
            return Math.addExact(value, 1L);
        }
        catch (ArithmeticException invalid)
        {
            throw invalid("受损写销库存版本无法继续推进");
        }
    }

    private static BigDecimal normalized(BigDecimal value, int scale)
    {
        if (value == null)
        {
            throw invalid("受损写销数量或成本无效");
        }
        try
        {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException invalid)
        {
            throw invalid("受损写销数量或成本精度无效");
        }
    }

    private static boolean quantity(BigDecimal value)
    {
        return value != null && value.signum() > 0 && value.scale() <= 4;
    }

    private static boolean quantityOrZero(BigDecimal value)
    {
        return value != null && value.signum() >= 0 && value.scale() <= 4;
    }

    private static boolean amount(BigDecimal value)
    {
        return value != null && value.signum() >= 0 && value.scale() <= 6;
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private static ServiceException invalid(String message)
    {
        return new ServiceException(message);
    }

    private static boolean requestId(String value)
    {
        return value != null && value.matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    }

    private static boolean hash(String value)
    {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static boolean exactlyNext(Long before, Long after)
    {
        try
        {
            return Math.addExact(before, 1L) == after;
        }
        catch (NullPointerException | ArithmeticException invalid)
        {
            return false;
        }
    }

    private record Totals(
            BigDecimal currentBefore,
            BigDecimal currentAfter,
            BigDecimal quarantineBefore,
            BigDecimal quarantineAfter,
            BigDecimal totalCostBefore,
            BigDecimal totalCostAfter,
            BigDecimal costPriceAfter)
    {
    }

    public record Serial(Long receiptSerialId, Long serialId,
            String serialNo)
    {
    }

    public record Prepared(
            PreparedExecution execution,
            InvTransferReceiptDiscrepancyDamageWriteOffFact fact,
            String lossType,
            String stockLedgerRequestId,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal allocationWrittenOffBefore,
            BigDecimal allocationWrittenOffAfter,
            Long stockId,
            Long stockVersionBefore,
            Long stockVersionAfter,
            BigDecimal stockCurrentBefore,
            BigDecimal stockCurrentAfter,
            BigDecimal stockAvailableQuantity,
            BigDecimal stockLockedQuantity,
            BigDecimal stockQuarantineBefore,
            BigDecimal stockQuarantineAfter,
            BigDecimal stockTotalCostBefore,
            BigDecimal stockTotalCostAfter,
            BigDecimal stockCostPriceAfter,
            Long balanceId,
            Long balanceVersionBefore,
            Long balanceVersionAfter,
            BigDecimal balanceCurrentBefore,
            BigDecimal balanceCurrentAfter,
            BigDecimal balanceAvailableQuantity,
            BigDecimal balanceLockedQuantity,
            BigDecimal balanceQuarantineBefore,
            BigDecimal balanceQuarantineAfter,
            BigDecimal balanceTotalCostBefore,
            BigDecimal balanceTotalCostAfter,
            BigDecimal balanceCostPriceAfter,
            List<Serial> serials)
    {
        public Prepared
        {
            serials = List.copyOf(serials);
        }
    }
}
