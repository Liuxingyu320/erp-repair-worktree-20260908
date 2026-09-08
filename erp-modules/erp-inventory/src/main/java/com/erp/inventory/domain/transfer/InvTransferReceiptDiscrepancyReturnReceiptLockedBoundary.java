package com.erp.inventory.domain.transfer;

import java.util.Objects;

/** Fixed-order locked facts paired with the dedicated P13 return plan. */
public record InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary(
        InvTransferShipmentReceiptLockedBoundary lockedFacts,
        InvTransferReceiptDiscrepancyReturnReceiptPolicy.Plan returnPlan)
{
    public InvTransferReceiptDiscrepancyReturnReceiptLockedBoundary
    {
        Objects.requireNonNull(lockedFacts, "lockedFacts");
        Objects.requireNonNull(returnPlan, "returnPlan");
    }
}
