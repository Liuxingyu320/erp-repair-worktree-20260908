package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import com.erp.common.core.exception.ServiceException;

/** Pure decimal rules for shipment, receipt and transfer conservation. */
public final class InvTransferQuantityPolicy
{
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private InvTransferQuantityPolicy()
    {
    }

    public static BigDecimal remainingToShip(BigDecimal approvedQuantity,
            BigDecimal shippedQuantity)
    {
        return remaining(approvedQuantity, shippedQuantity,
                "累计发货数量不能超过审批数量");
    }

    public static BigDecimal remainingToReceive(BigDecimal shippedQuantity,
            BigDecimal receivedQuantity)
    {
        return remaining(shippedQuantity, receivedQuantity,
                "累计收货数量不能超过累计发货数量");
    }

    public static void requireShipmentWithinApproved(
            BigDecimal approvedQuantity, BigDecimal shippedQuantity,
            BigDecimal requestedShipmentQuantity)
    {
        BigDecimal request = requireNonNegative(requestedShipmentQuantity,
                "发货数量不能小于0");
        if (request.compareTo(ZERO) == 0)
        {
            throw new ServiceException("发货数量必须大于0");
        }
        BigDecimal remaining = remainingToShip(approvedQuantity,
                shippedQuantity);
        if (request.compareTo(remaining) > 0)
        {
            throw new ServiceException("发货数量超过剩余待发数量");
        }
    }

    public static boolean isFullyAccounted(BigDecimal expectedQuantity,
            BigDecimal accountedQuantity, String overflowMessage)
    {
        return remaining(expectedQuantity, accountedQuantity,
                overflowMessage).compareTo(ZERO) == 0;
    }

    public static ReceiptBreakdown classifyReceipt(BigDecimal pendingQuantity,
            BigDecimal acceptedQuantity, BigDecimal rejectedQuantity,
            BigDecimal damagedQuantity)
    {
        BigDecimal pending = requireNonNegative(pendingQuantity,
                "待收数量不能小于0");
        if (pending.compareTo(ZERO) == 0)
        {
            throw new ServiceException("待收数量必须大于0");
        }
        BigDecimal accepted = requireNonNegative(acceptedQuantity,
                "验收入库数量不能小于0");
        BigDecimal rejected = requireNonNegative(rejectedQuantity,
                "拒收数量不能小于0");
        BigDecimal damaged = requireNonNegative(damagedQuantity,
                "残损数量不能小于0");
        BigDecimal classified = accepted.add(rejected).add(damaged);
        if (classified.compareTo(pending) > 0)
        {
            throw new ServiceException("收货分类数量超过待收数量");
        }
        return new ReceiptBreakdown(pending, accepted, rejected, damaged,
                pending.subtract(classified));
    }

    private static BigDecimal remaining(BigDecimal expectedQuantity,
            BigDecimal accountedQuantity, String overflowMessage)
    {
        BigDecimal expected = requireNonNegative(expectedQuantity,
                "计划数量不能小于0");
        BigDecimal accounted = requireNonNegative(accountedQuantity,
                "累计数量不能小于0");
        if (accounted.compareTo(expected) > 0)
        {
            throw new ServiceException(overflowMessage);
        }
        return expected.subtract(accounted);
    }

    private static BigDecimal requireNonNegative(BigDecimal value,
            String message)
    {
        BigDecimal normalized = value == null ? ZERO : value;
        if (normalized.compareTo(ZERO) < 0)
        {
            throw new ServiceException(message);
        }
        return normalized;
    }

    public record ReceiptBreakdown(
            BigDecimal pendingQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity,
            BigDecimal damagedQuantity,
            BigDecimal shortageQuantity)
    {
        public boolean hasDiscrepancy()
        {
            return shortageQuantity.compareTo(ZERO) > 0
                    || rejectedQuantity.compareTo(ZERO) > 0
                    || damagedQuantity.compareTo(ZERO) > 0;
        }

        public BigDecimal classifiedQuantity()
        {
            return acceptedQuantity.add(rejectedQuantity)
                    .add(damagedQuantity);
        }
    }

    /**
     * Explicit conservation snapshot used by tests and later persistence
     * adapters. Both equations must balance exactly as decimal quantities.
     */
    public record FulfillmentSnapshot(
            BigDecimal approvedQuantity,
            BigDecimal pendingShipmentQuantity,
            BigDecimal terminatedUnshippedQuantity,
            BigDecimal shippedQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal damagedQuantity,
            BigDecimal adjudicatedShortageQuantity,
            BigDecimal returnedQuantity,
            BigDecimal inTransitOrUnresolvedQuantity)
    {
        public FulfillmentSnapshot
        {
            approvedQuantity = requireNonNegative(approvedQuantity,
                    "审批数量不能小于0");
            pendingShipmentQuantity = requireNonNegative(
                    pendingShipmentQuantity, "待发数量不能小于0");
            terminatedUnshippedQuantity = requireNonNegative(
                    terminatedUnshippedQuantity, "未发终止数量不能小于0");
            shippedQuantity = requireNonNegative(shippedQuantity,
                    "累计发货数量不能小于0");
            acceptedQuantity = requireNonNegative(acceptedQuantity,
                    "合格收货数量不能小于0");
            damagedQuantity = requireNonNegative(damagedQuantity,
                    "残损数量不能小于0");
            adjudicatedShortageQuantity = requireNonNegative(
                    adjudicatedShortageQuantity, "已裁决短缺数量不能小于0");
            returnedQuantity = requireNonNegative(returnedQuantity,
                    "退回数量不能小于0");
            inTransitOrUnresolvedQuantity = requireNonNegative(
                    inTransitOrUnresolvedQuantity, "在途或未裁决数量不能小于0");

            BigDecimal approvalAccounted = pendingShipmentQuantity
                    .add(terminatedUnshippedQuantity).add(shippedQuantity);
            if (approvedQuantity.compareTo(approvalAccounted) != 0)
            {
                throw new ServiceException("审批数量守恒校验失败");
            }
            BigDecimal shipmentAccounted = acceptedQuantity
                    .add(damagedQuantity)
                    .add(adjudicatedShortageQuantity)
                    .add(returnedQuantity)
                    .add(inTransitOrUnresolvedQuantity);
            if (shippedQuantity.compareTo(shipmentAccounted) != 0)
            {
                throw new ServiceException("发货数量守恒校验失败");
            }
        }

        public boolean isComplete()
        {
            return pendingShipmentQuantity.compareTo(ZERO) == 0
                    && inTransitOrUnresolvedQuantity.compareTo(ZERO) == 0;
        }
    }
}
