package com.erp.inventory.constant;

/**
 * Manual cross-store transfer source-store confirmation states.
 */
public final class InvTransferSourceConfirmStatus
{
    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PARTIAL = "PARTIAL";
    public static final String REJECTED = "REJECTED";
    public static final String RESELECT_REQUIRED = "RESELECT_REQUIRED";

    private InvTransferSourceConfirmStatus()
    {
    }

    public static boolean allowsDelivery(String status)
    {
        return CONFIRMED.equals(status) || PARTIAL.equals(status);
    }
}
