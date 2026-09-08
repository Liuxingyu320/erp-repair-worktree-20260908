package com.erp.inventory.constant;

import java.util.Set;
import com.erp.common.core.exception.ServiceException;

/** 调拨收货差异的处理决定与状态。 */
public final class InvTransferDiscrepancyDecisions
{
    public static final String SHORTAGE = "SHORTAGE";
    public static final String REJECTED = "REJECTED";
    public static final String DAMAGED = "DAMAGED";

    public static final String OPEN = "OPEN";
    public static final String PENDING_QC = "PENDING_QC";
    public static final String RESOLVED = "RESOLVED";

    public static final String RESHIP = "RESHIP";
    public static final String RETURN_SOURCE = "RETURN_SOURCE";
    public static final String ACCEPT_ACTUAL = "ACCEPT_ACTUAL";
    public static final String QC_HOLD = "PENDING_QC";
    public static final String WRITE_OFF = "WRITE_OFF";

    private static final Set<String> SUPPORTED = Set.of(
            RESHIP, RETURN_SOURCE, ACCEPT_ACTUAL, QC_HOLD, WRITE_OFF);

    private static final Set<String> CATEGORIES = Set.of(
            SHORTAGE, REJECTED, DAMAGED);

    private InvTransferDiscrepancyDecisions() {}

    public static String requireSupported(String value)
    {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!SUPPORTED.contains(normalized))
        {
            throw new ServiceException("不支持的差异处理决定");
        }
        return normalized;
    }

    public static String requireCategory(String value)
    {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!CATEGORIES.contains(normalized))
        {
            throw new ServiceException("不支持的差异类别");
        }
        return normalized;
    }

    public static String requireAllowed(String category, String decision,
            boolean pendingQc, boolean hasExplicitEvidence)
    {
        String normalizedCategory = requireCategory(category);
        String normalizedDecision = requireSupported(decision);
        boolean allowed = switch (normalizedCategory)
        {
            case SHORTAGE -> !pendingQc && Set.of(
                    RESHIP, ACCEPT_ACTUAL, WRITE_OFF)
                    .contains(normalizedDecision);
            case REJECTED -> pendingQc
                    ? Set.of(RETURN_SOURCE, WRITE_OFF)
                            .contains(normalizedDecision)
                    : Set.of(RETURN_SOURCE, QC_HOLD, WRITE_OFF)
                            .contains(normalizedDecision);
            case DAMAGED -> pendingQc
                    ? Set.of(RETURN_SOURCE, WRITE_OFF)
                            .contains(normalizedDecision)
                    : QC_HOLD.equals(normalizedDecision)
                            || (hasExplicitEvidence && Set.of(
                                    RETURN_SOURCE, WRITE_OFF)
                                    .contains(normalizedDecision));
            default -> false;
        };
        if (!allowed)
        {
            throw new ServiceException("差异类别与处置决定不匹配");
        }
        return normalizedDecision;
    }
}
