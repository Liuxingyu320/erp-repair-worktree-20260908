package com.erp.inventory.domain.transfer;

import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvStatusConstants;

/**
 * Central lifecycle decisions for inventory transfers.
 *
 * <p>The existing database status values remain the compatibility boundary,
 * while this policy keeps services from inventing transitions independently.
 * {@code received} is the legacy persisted value for a fully completed
 * transfer.</p>
 */
public final class InvTransferLifecyclePolicy
{
    private static final Set<String> DELIVERY_SOURCE_STATES = Set.of(
            InvStatusConstants.APPROVED,
            InvStatusConstants.RESERVED,
            InvStatusConstants.PARTIAL_DELIVERED);

    private static final Set<String> RECEIPT_SOURCE_STATES = Set.of(
            InvStatusConstants.PARTIAL_DELIVERED,
            InvStatusConstants.DELIVERED,
            InvStatusConstants.PARTIAL_RECEIVED);

    private InvTransferLifecyclePolicy()
    {
    }

    public static String afterDelivery(String currentStatus,
            boolean allApprovedQuantityShipped)
    {
        requireState(currentStatus, DELIVERY_SOURCE_STATES, "发货");
        return allApprovedQuantityShipped
                ? InvStatusConstants.DELIVERED
                : InvStatusConstants.PARTIAL_DELIVERED;
    }

    public static String afterReceipt(String currentStatus,
            boolean hasOpenDiscrepancy,
            boolean allApprovedQuantityReceived)
    {
        requireState(currentStatus, RECEIPT_SOURCE_STATES, "收货");
        if (hasOpenDiscrepancy)
        {
            return InvStatusConstants.DISCREPANCY;
        }
        return allApprovedQuantityReceived
                ? InvStatusConstants.RECEIVED
                : InvStatusConstants.PARTIAL_RECEIVED;
    }

    public static String afterDiscrepancyResolution(String currentStatus,
            int remainingOpenDiscrepancyCount,
            boolean requiresReshipment,
            boolean allApprovedQuantityReceived)
    {
        requireState(currentStatus, Set.of(InvStatusConstants.DISCREPANCY),
                "处理差异");
        if (remainingOpenDiscrepancyCount < 0)
        {
            throw new ServiceException("待处理差异数量不能小于0");
        }
        if (remainingOpenDiscrepancyCount > 0)
        {
            return InvStatusConstants.DISCREPANCY;
        }
        if (requiresReshipment)
        {
            return InvStatusConstants.PARTIAL_DELIVERED;
        }
        return allApprovedQuantityReceived
                ? InvStatusConstants.RECEIVED
                : InvStatusConstants.CLOSED;
    }

    public static void requireDeliveryAllowed(String currentStatus)
    {
        requireState(currentStatus, DELIVERY_SOURCE_STATES, "发货");
    }

    public static void requireReceiptAllowed(String currentStatus)
    {
        requireState(currentStatus, RECEIPT_SOURCE_STATES, "收货");
    }

    private static void requireState(String currentStatus,
            Set<String> allowedStates, String action)
    {
        if (!allowedStates.contains(currentStatus))
        {
            throw new ServiceException("当前状态不允许" + action
                    + "，当前状态: " + currentStatus);
        }
    }
}
