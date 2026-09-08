package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Canonical expected damaged-return command validated against a P13 plan. */
public record InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand(
        String requestFingerprint,
        String returnReceiptPlanVersion,
        Instant arrivedTime,
        boolean finalizeShipment,
        List<Allocation> allocations,
        BigDecimal returnedQuantity,
        BigDecimal shortageQuantity,
        BigDecimal remainingQuantityAfter,
        String remark)
{
    public InvTransferReceiptDiscrepancyReturnReceiptValidatedCommand
    {
        allocations = List.copyOf(allocations);
    }

    public record Allocation(
            Long shipmentAllocationId,
            Long quarantineLocationId,
            BigDecimal returnedQuantity,
            BigDecimal shortageQuantity,
            List<Long> returnedSerialIds,
            List<Long> shortageSerialIds,
            String discrepancyNote,
            String attachmentRefs)
    {
        public Allocation
        {
            returnedSerialIds = List.copyOf(returnedSerialIds);
            shortageSerialIds = List.copyOf(shortageSerialIds);
        }
    }
}
