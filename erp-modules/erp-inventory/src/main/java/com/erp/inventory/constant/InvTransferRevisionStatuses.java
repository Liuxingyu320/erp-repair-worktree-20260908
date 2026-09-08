package com.erp.inventory.constant;

import java.util.Set;

/**
 * Immutable business-revision lifecycle for inventory transfers.
 */
public final class InvTransferRevisionStatuses
{
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String RETURNED = "RETURNED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";
    public static final String DELIVERED = "DELIVERED";

    private static final Set<String> ALL_STATUSES = Set.of(
            DRAFT, SUBMITTED, APPROVED, REJECTED, RETURNED, WITHDRAWN,
            CLOSED, CANCELLED, DELIVERED);

    private static final Set<String> APPROVAL_OUTCOMES = Set.of(
            APPROVED, REJECTED, RETURNED, WITHDRAWN, CLOSED);

    private InvTransferRevisionStatuses()
    {
    }

    public static boolean isApprovalOutcome(String status)
    {
        return status != null && APPROVAL_OUTCOMES.contains(status);
    }

    public static boolean isKnown(String status)
    {
        return status != null && ALL_STATUSES.contains(status);
    }
}
