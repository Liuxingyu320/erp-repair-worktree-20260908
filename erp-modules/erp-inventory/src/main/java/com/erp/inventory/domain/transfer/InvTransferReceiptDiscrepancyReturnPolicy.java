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
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.DispatchSpec;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;

/** Pure reverse-transfer and quarantine-reservation conservation policy. */
public final class InvTransferReceiptDiscrepancyReturnPolicy
{
    public static final String RETURN_REASON_CODE =
            "DAMAGED_RECEIPT_RETURN";
    public static final String SERIAL_RESERVED_STATUS =
            "quarantine_reserved";

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private InvTransferReceiptDiscrepancyReturnPolicy()
    {
    }

    public static PreparedChild prepareChild(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact,
            Long selectedShopDeptId)
    {
        DispatchSpec spec =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .plan(source);
        requireReturnSource(source, fact, spec);
        if (!positive(selectedShopDeptId)
                || !Objects.equals(selectedShopDeptId,
                        initiatingDept(source)))
        {
            throw invalid("退回子调拨发起组织无效");
        }
        String reasonCode = InvTransferTypes.STORE_RETURN.equals(
                spec.childTransferType()) ? RETURN_REASON_CODE : null;
        String reasonText = reasonCode == null ? null
                : "V2 damaged receipt return action=" + source.actionId();
        return new PreparedChild(spec.childTransferType(),
                fact.getToDeptId(), fact.getToWarehouseId(),
                fact.getFromDeptId(), fact.getFromWarehouseId(),
                reasonCode, reasonText, spec.sourceBusinessType(),
                source.actionId(), source.itemType(), source.itemId(),
                source.productId(), source.quantity(),
                source.sourceCostPrice(),
                "V2 discrepancy return action=" + source.actionId());
    }

    public static PreparedReservation prepareReservation(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact,
            PreparedChild child, InvTransferOrder saved,
            InvTransferDetail detail, int reservationRound,
            BigDecimal previouslyDisposed,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetLot lot,
            InvTransferReceiptLocationCandidate location,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnSerialFact> serialFacts)
    {
        DispatchSpec spec =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .plan(source);
        requireReturnSource(source, fact, spec);
        requireChild(source, child, saved, detail, reservationRound);
        requireDimensions(source, fact, stock, lot, location, balance);

        BigDecimal quantity = normalized(source.quantity(), 4);
        BigDecimal disposed = normalized(previouslyDisposed, 4);
        if (disposed.signum() < 0 || disposed.add(quantity).compareTo(
                normalized(fact.getDamagedQuantity(), 4)) > 0)
        {
            throw invalid("退回动作超过V2受损收货分配剩余数量");
        }

        QuantityTransition stockTransition = transition(
                stock.getCurrentQuantity(), stock.getAvailableQuantity(),
                stock.getLockedQuantity(), stock.getQuarantineQuantity(),
                quantity, "退回汇总隔离库存不足或守恒无效");
        QuantityTransition balanceTransition = transition(
                balance.getCurrentQuantity(), balance.getAvailableQuantity(),
                balance.getLockedQuantity(),
                balance.getQuarantineQuantity(), quantity,
                "退回批次库位隔离余额不足或守恒无效");
        if (balanceTransition.availableBefore().signum() != 0
                || !same(balance.getCostPrice(),
                        source.sourceCostPrice())
                || normalized(balance.getTotalCost(), 6).compareTo(
                        normalized(source.amount(), 6)) < 0
                || normalized(stock.getTotalCost(), 6).compareTo(
                        normalized(source.amount(), 6)) < 0)
        {
            throw invalid("退回隔离余额混入可用数量或成本锚点漂移");
        }

        List<Serial> serials = serials(fact, quantity, serialFacts);
        return new PreparedReservation(source, fact, child,
                saved.getTransferId(), detail.getDetailId(),
                reservationRound, disposed,
                disposed.add(quantity).setScale(4), stock.getStockId(),
                stock.getVersion(), increment(stock.getVersion()),
                stockTransition, normalized(stock.getCostPrice(), 6),
                normalized(stock.getTotalCost(), 6),
                balance.getBalanceId(), balance.getVersion(),
                increment(balance.getVersion()), balanceTransition,
                normalized(balance.getCostPrice(), 6),
                normalized(balance.getTotalCost(), 6), serials);
    }

    private static void requireReturnSource(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact,
            DispatchSpec spec)
    {
        if (fact == null
                || !Objects.equals(spec.workflowType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .WORKFLOW_RETURN)
                || !Objects.equals(spec.inventorySource(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .INVENTORY_QUARANTINE)
                || !Objects.equals(spec.sourceBusinessType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .SOURCE_RETURN)
                || !matches(source, fact)
                || !positive(fact.getReceiptId())
                || !positive(fact.getShipmentId())
                || !positive(fact.getTargetWarehouseId())
                || !same(source.originalTargetLocationDeptId(),
                        fact.getTargetWarehouseId())
                || !positive(fact.getFromDeptId())
                || !positive(fact.getToDeptId())
                || !warehouse(fact.getFromWarehouseId())
                || !warehouse(fact.getToWarehouseId())
                || !positive(fact.getDamagedQuantity())
                || fact.getQuantity().compareTo(
                        fact.getDamagedQuantity()) > 0)
        {
            throw invalid("退回子调拨来源或V2收货锚点无效");
        }
    }

    private static boolean matches(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact)
    {
        return Objects.equals(source.caseId(), fact.getCaseId())
                && Objects.equals(source.caseVersionBefore(),
                        fact.getCaseVersionBefore())
                && Objects.equals(source.adjudicationId(),
                        fact.getAdjudicationId())
                && Objects.equals(source.actionId(), fact.getActionId())
                && Objects.equals(source.actionVersionBefore(),
                        fact.getActionVersionBefore())
                && Objects.equals(source.discrepancyType(),
                        fact.getDiscrepancyType())
                && Objects.equals(source.actionType(), fact.getActionType())
                && Objects.equals(source.parentTransferId(),
                        fact.getParentTransferId())
                && Objects.equals(source.parentTransferType(),
                        fact.getParentTransferType())
                && Objects.equals(source.originalSourceLocationDeptId(),
                        location(fact.getFromWarehouseId(),
                                fact.getFromDeptId()))
                && Objects.equals(source.originalTargetLocationDeptId(),
                        location(fact.getToWarehouseId(),
                                fact.getToDeptId()))
                && Objects.equals(source.receiptAllocationId(),
                        fact.getReceiptAllocationId())
                && Objects.equals(source.shipmentAllocationId(),
                        fact.getShipmentAllocationId())
                && Objects.equals(source.itemType(), fact.getItemType())
                && Objects.equals(source.itemId(), fact.getItemId())
                && Objects.equals(source.productId(), fact.getProductId())
                && Objects.equals(source.trackingPolicy(),
                        fact.getTrackingPolicy())
                && Objects.equals(source.quarantineBalanceId(),
                        fact.getQuarantineBalanceId())
                && Objects.equals(source.quarantineLotId(),
                        fact.getQuarantineLotId())
                && Objects.equals(source.quarantineLocationId(),
                        fact.getQuarantineLocationId())
                && same(source.quantity(), fact.getQuantity())
                && same(source.sourceCostPrice(),
                        fact.getSourceCostPrice())
                && same(source.amount(), fact.getAmount())
                && Objects.equals(source.decisionFingerprint(),
                        fact.getDecisionFingerprint());
    }

    private static void requireChild(Source source, PreparedChild child,
            InvTransferOrder saved, InvTransferDetail detail,
            int reservationRound)
    {
        if (child == null || saved == null || detail == null
                || !positive(saved.getTransferId())
                || Objects.equals(saved.getTransferId(),
                        source.parentTransferId())
                || !InvStatusConstants.DRAFT.equals(saved.getStatus())
                || !Objects.equals(saved.getTransferType(),
                        child.transferType())
                || !Objects.equals(location(saved.getFromWarehouseId(),
                        saved.getFromDeptId()),
                        source.originalTargetLocationDeptId())
                || !Objects.equals(location(saved.getToWarehouseId(),
                        saved.getToDeptId()),
                        source.originalSourceLocationDeptId())
                || !Objects.equals(saved.getSourceBusinessType(),
                        child.sourceBusinessType())
                || !Objects.equals(saved.getSourceBusinessId(),
                        source.actionId())
                || !Objects.equals(detail.getTransferId(),
                        saved.getTransferId())
                || !positive(detail.getDetailId())
                || !Objects.equals(detail.getItemType(), source.itemType())
                || !Objects.equals(detail.getItemId(), source.itemId())
                || !Objects.equals(detail.getProductId(),
                        source.productId())
                || !same(detail.getQuantity(), source.quantity())
                || !zero(detail.getDeliveredQuantity())
                || !zero(detail.getReceivedQuantity())
                || reservationRound <= 0
                || reservationRound != (saved.getApprovalRound() == null
                        ? 1 : saved.getApprovalRound() + 1))
        {
            throw invalid("退回子调拨草稿或唯一明细事实无效");
        }
    }

    private static void requireDimensions(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetLot lot,
            InvTransferReceiptLocationCandidate location,
            InvTransferReceiptTargetBalance balance)
    {
        if (stock == null || !positive(stock.getStockId())
                || !Objects.equals(stock.getItemType(), source.itemType())
                || !Objects.equals(stock.getItemId(), source.itemId())
                || !Objects.equals(stock.getProductId(), source.productId())
                || !Objects.equals(stock.getShopDeptId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(stock.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || stock.getVersion() == null || stock.getVersion() < 0
                || lot == null
                || !Objects.equals(lot.getLotId(),
                        fact.getQuarantineLotId())
                || !Objects.equals(lot.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(lot.getItemType(), source.itemType())
                || !Objects.equals(lot.getItemId(), source.itemId())
                || !Objects.equals(lot.getProductId(), source.productId())
                || !"damaged".equals(lot.getReceiptDisposition())
                || !"quarantine".equals(lot.getQcStatus())
                || !"active".equals(lot.getLotStatus())
                || location == null
                || !Objects.equals(location.getLocationId(),
                        fact.getQuarantineLocationId())
                || !Objects.equals(location.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !"quarantine".equals(location.getLocationType())
                || !"0".equals(location.getStatus())
                || !"0".equals(location.getVirtualFlag())
                || balance == null || !positive(balance.getBalanceId())
                || !Objects.equals(balance.getBalanceId(),
                        fact.getQuarantineBalanceId())
                || !Objects.equals(balance.getWarehouseId(),
                        fact.getTargetWarehouseId())
                || !Objects.equals(balance.getItemType(), source.itemType())
                || !Objects.equals(balance.getItemId(), source.itemId())
                || !Objects.equals(balance.getProductId(),
                        source.productId())
                || !Objects.equals(balance.getLotId(),
                        fact.getQuarantineLotId())
                || !Objects.equals(balance.getLocationId(),
                        fact.getQuarantineLocationId())
                || balance.getVersion() == null || balance.getVersion() < 0)
        {
            throw invalid("退回隔离库存批次库位维度无效");
        }
    }

    private static QuantityTransition transition(BigDecimal current,
            BigDecimal available, BigDecimal locked, BigDecimal quarantine,
            BigDecimal quantity, String message)
    {
        BigDecimal currentBefore = normalized(current, 4);
        BigDecimal availableBefore = normalized(available, 4);
        BigDecimal lockedBefore = normalized(locked, 4);
        BigDecimal quarantineBefore = normalized(quarantine, 4);
        if (currentBefore.signum() < 0 || availableBefore.signum() < 0
                || lockedBefore.signum() < 0
                || quarantineBefore.compareTo(quantity) < 0
                || currentBefore.compareTo(availableBefore
                        .add(lockedBefore).add(quarantineBefore)) != 0)
        {
            throw invalid(message);
        }
        return new QuantityTransition(currentBefore, currentBefore,
                availableBefore, availableBefore, lockedBefore,
                lockedBefore.add(quantity).setScale(4),
                quarantineBefore,
                quarantineBefore.subtract(quantity).setScale(4));
    }

    private static List<Serial> serials(
            InvTransferReceiptDiscrepancyReturnFact fact,
            BigDecimal quantity,
            List<InvTransferReceiptDiscrepancyReturnSerialFact> candidates)
    {
        List<InvTransferReceiptDiscrepancyReturnSerialFact> values =
                candidates == null ? new ArrayList<>()
                        : new ArrayList<>(candidates);
        values.sort(Comparator
                .comparing(
                        InvTransferReceiptDiscrepancyReturnSerialFact
                                ::getReceiptSerialId,
                        Comparator.nullsFirst(Long::compareTo))
                .thenComparing(
                        InvTransferReceiptDiscrepancyReturnSerialFact
                                ::getSerialId,
                        Comparator.nullsFirst(Long::compareTo)));
        if ("lot".equals(fact.getTrackingPolicy()))
        {
            if (!values.isEmpty())
            {
                throw invalid("批次跟踪退回不得包含序列号");
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
            throw invalid("序列号退回数量必须为整数");
        }
        if (expected <= 0 || values.size() < expected)
        {
            throw invalid("序列号退回缺少足量隔离单件");
        }
        Set<Long> receiptSerialIds = new HashSet<>();
        Set<Long> serialIds = new HashSet<>();
        List<Serial> selected = new ArrayList<>(expected);
        for (InvTransferReceiptDiscrepancyReturnSerialFact value : values)
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
                    || !Objects.equals(value.getItemType(),
                            fact.getItemType())
                    || !Objects.equals(value.getItemId(), fact.getItemId())
                    || !Objects.equals(value.getSerialNoSnapshot(),
                            value.getCurrentSerialNo())
                    || !"damaged".equals(value.getDisposition())
                    || !Objects.equals(value.getCurrentWarehouseId(),
                            fact.getTargetWarehouseId())
                    || !Objects.equals(value.getCurrentBalanceId(),
                            fact.getQuarantineBalanceId())
                    || !Objects.equals(value.getCurrentLotId(),
                            fact.getQuarantineLotId())
                    || !Objects.equals(value.getCurrentLocationId(),
                            fact.getQuarantineLocationId())
                    || !"quarantine".equals(value.getReceiptStatusAfter())
                    || !"quarantine".equals(value.getCurrentStatus()))
            {
                throw invalid("序列号退回隔离单件事实无效");
            }
            if (selected.size() < expected)
            {
                selected.add(new Serial(value.getReceiptSerialId(),
                        value.getSerialId(), value.getSerialNoSnapshot(),
                        "quarantine", SERIAL_RESERVED_STATUS));
            }
        }
        return List.copyOf(selected);
    }

    private static Long initiatingDept(Source source)
    {
        return InvTransferTypes.STORE_RETURN.equals(
                source.parentTransferType())
                        ? source.originalSourceLocationDeptId()
                        : source.originalTargetLocationDeptId();
    }

    private static Long location(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId != 0
                ? warehouseId : deptId;
    }

    private static long increment(Long value)
    {
        if (value == null || value < 0 || value == Long.MAX_VALUE)
        {
            throw invalid("退回隔离库存版本无效");
        }
        return value + 1;
    }

    private static BigDecimal normalized(BigDecimal value, int scale)
    {
        if (value == null)
        {
            throw invalid("退回隔离库存数量或金额缺失");
        }
        try
        {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException invalid)
        {
            throw invalid("退回隔离库存数量或金额精度无效");
        }
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean same(Long left, Long right)
    {
        return Objects.equals(left, right);
    }

    private static boolean zero(BigDecimal value)
    {
        return value == null || value.compareTo(ZERO) == 0;
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean positive(BigDecimal value)
    {
        return value != null && value.compareTo(ZERO) > 0;
    }

    private static boolean warehouse(Long value)
    {
        return value == null || value >= 0;
    }

    private static ServiceException invalid(String message)
    {
        return new ServiceException(message);
    }

    public record PreparedChild(String transferType, Long fromDeptId,
            Long fromWarehouseId, Long toDeptId, Long toWarehouseId,
            String returnReasonCode, String returnReasonText,
            String sourceBusinessType, Long sourceBusinessId,
            String itemType, Long itemId, Long productId,
            BigDecimal quantity, BigDecimal referenceCostPrice,
            String remark)
    {
    }

    public record QuantityTransition(BigDecimal currentBefore,
            BigDecimal currentAfter, BigDecimal availableBefore,
            BigDecimal availableAfter, BigDecimal lockedBefore,
            BigDecimal lockedAfter, BigDecimal quarantineBefore,
            BigDecimal quarantineAfter)
    {
    }

    public record PreparedReservation(Source source,
            InvTransferReceiptDiscrepancyReturnFact fact,
            PreparedChild child, Long childTransferId, Long childDetailId,
            int reservationRound, BigDecimal disposedQuantityBefore,
            BigDecimal disposedQuantityAfter, Long stockId,
            Long stockVersionBefore, Long stockVersionAfter,
            QuantityTransition stock, BigDecimal stockCostPrice,
            BigDecimal stockTotalCost, Long balanceId,
            Long balanceVersionBefore, Long balanceVersionAfter,
            QuantityTransition balance, BigDecimal balanceCostPrice,
            BigDecimal balanceTotalCost, List<Serial> serials)
    {
        public PreparedReservation
        {
            serials = List.copyOf(serials);
        }
    }

    public record Serial(Long receiptSerialId, Long serialId,
            String serialNo, String statusBefore, String statusAfter)
    {
    }
}
