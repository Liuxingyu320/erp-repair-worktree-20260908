package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable fingerprint policy for one immutable V2 receipt discrepancy fact. */
public final class InvTransferReceiptDiscrepancyFacts
{
    private InvTransferReceiptDiscrepancyFacts()
    {
    }

    public static String fingerprint(String receiptPlanVersion,
            Long shipmentAllocationId, String discrepancyType,
            BigDecimal discrepancyQuantity, BigDecimal sourceCostPrice,
            BigDecimal discrepancyAmount, String discrepancyNote,
            String attachmentRefs)
    {
        String canonical = part(receiptPlanVersion)
                + part(shipmentAllocationId == null ? null
                        : Long.toString(shipmentAllocationId))
                + part(discrepancyType)
                + part(decimal(discrepancyQuantity))
                + part(decimal(sourceCostPrice))
                + part(decimal(discrepancyAmount))
                + part(discrepancyNote) + part(attachmentRefs);
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8));
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

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String part(String value)
    {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }
}
