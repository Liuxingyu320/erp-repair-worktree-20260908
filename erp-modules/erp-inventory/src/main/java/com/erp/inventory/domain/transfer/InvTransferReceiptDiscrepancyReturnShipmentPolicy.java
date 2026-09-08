package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.ConsumptionPlan;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.QuantityTransition;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy.SerialTransition;

/** Pure deterministic planning boundary for an adjudication return shipment. */
public final class InvTransferReceiptDiscrepancyReturnShipmentPolicy
{
    private InvTransferReceiptDiscrepancyReturnShipmentPolicy()
    {
    }

    public static PreparedPlan prepare(Instant generatedAt,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            InvTransferReceiptTargetStock stock,
            InvTransferReceiptTargetBalance balance,
            List<InvTransferReceiptDiscrepancyReturnReservationSerialLifecycleFact>
                    serialFacts)
    {
        if (generatedAt == null || !generatedAt.equals(
                generatedAt.truncatedTo(ChronoUnit.SECONDS)))
        {
            throw new ServiceException("退回发货规划生成时间无效");
        }
        ConsumptionPlan consumption =
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .planAllRemainingConsumption(fact, stock, balance,
                                serialFacts);
        String planVersion = fingerprint(fact, consumption);
        return new PreparedPlan(generatedAt, planVersion, fact,
                consumption);
    }

    private static String fingerprint(
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            ConsumptionPlan consumption)
    {
        StringBuilder source = new StringBuilder(
                "adjudication-return-shipment-plan-v1");
        append(source, fact.getReservationId(),
                fact.getReservationRequestId(), fact.getActionId(),
                fact.getChildTransferId(), fact.getChildTransferDetailId(),
                fact.getReservationRound(), fact.getReceiptAllocationId(),
                fact.getShipmentAllocationId(), fact.getStockId(),
                fact.getBalanceId(), fact.getLotId(), fact.getLocationId(),
                fact.getSourceWarehouseId(), fact.getItemType(),
                fact.getItemId(), fact.getProductId(),
                fact.getTrackingPolicy(), fact.getDecisionFingerprint(),
                fact.getWorkflowId(), fact.getWorkflowType(),
                fact.getInventorySource(), fact.getWorkflowFingerprint(),
                fact.getChildStatus(), fact.getApprovalRound(),
                decimal(fact.getRequestedQuantity()),
                decimal(fact.getDeliveredQuantity()),
                decimal(fact.getReservedQuantity()),
                decimal(fact.getConsumedQuantity()),
                decimal(fact.getReleasedQuantity()),
                decimal(fact.getSourceCostPrice()),
                decimal(fact.getReservedAmount()), fact.getStatus(),
                fact.getVersion());
        append(source, decimal(consumption.quantity()),
                decimal(consumption.amount()),
                decimal(consumption.remainingAfter()),
                consumption.statusAfter(), consumption.versionAfter());
        appendTransition(source, "stock", consumption.stock());
        appendTransition(source, "balance", consumption.balance());
        for (SerialTransition serial : consumption.serials())
        {
            append(source, "serial", serial.bindingId(),
                    serial.receiptSerialId(), serial.serialId(),
                    serial.serialNo(), serial.versionBefore(),
                    serial.lifecycleBefore(), serial.physicalBefore());
        }
        return sha256(source.toString());
    }

    private static void appendTransition(StringBuilder source, String label,
            QuantityTransition value)
    {
        append(source, label, decimal(value.currentBefore()),
                decimal(value.availableBefore()),
                decimal(value.lockedBefore()),
                decimal(value.quarantineBefore()),
                decimal(value.totalCostBefore()), value.versionBefore());
    }

    private static void append(StringBuilder source, Object... values)
    {
        for (Object value : values)
        {
            String text = String.valueOf(value);
            source.append('|').append(text.length()).append(':').append(text);
        }
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

    public record PreparedPlan(Instant generatedAt, String planVersion,
            InvTransferReceiptDiscrepancyReturnReservationLifecycleFact fact,
            ConsumptionPlan consumption)
    {
    }
}
