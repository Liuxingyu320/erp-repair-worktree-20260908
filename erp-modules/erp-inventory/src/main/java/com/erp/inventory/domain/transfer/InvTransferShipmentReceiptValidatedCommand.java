package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable, canonically ordered receipt command validated against a plan. */
public record InvTransferShipmentReceiptValidatedCommand(
        String requestFingerprint,
        String receiptPlanVersion,
        Instant arrivedTime,
        boolean finalizeShipment,
        List<Allocation> allocations,
        BigDecimal acceptedQuantity,
        BigDecimal damagedQuantity,
        BigDecimal shortageQuantity,
        BigDecimal remainingQuantityAfter,
        String remark)
{
    public record Allocation(
            Long shipmentAllocationId,
            Long acceptedLocationId,
            BigDecimal acceptedQuantity,
            Long quarantineLocationId,
            BigDecimal damagedQuantity,
            BigDecimal shortageQuantity,
            List<Long> acceptedSerialIds,
            List<Long> damagedSerialIds,
            List<Long> shortageSerialIds,
            String discrepancyNote,
            String attachmentRefs)
    {
    }
}
