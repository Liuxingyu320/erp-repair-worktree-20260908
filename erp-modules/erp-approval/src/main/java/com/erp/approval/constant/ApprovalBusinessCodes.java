package com.erp.approval.constant;

import java.util.Set;

/** Stable business codes registered in the unified approval catalog. */
public final class ApprovalBusinessCodes
{
    public static final String OA_PURCHASE = "OA_PURCHASE";
    public static final String OA_REIMBURSEMENT = "OA_REIMBURSEMENT";
    public static final String INV_TRANSFER = "INV_TRANSFER";
    public static final String INV_STOCK_CHECK = "INV_STOCK_CHECK";
    public static final String HR_HEALTH_CERTIFICATE =
            "HR_HEALTH_CERTIFICATE";
    public static final String OA_ATTENDANCE_LEAVE =
            "OA_ATTENDANCE_LEAVE";
    public static final String OA_ATTENDANCE_CORRECTION =
            "OA_ATTENDANCE_CORRECTION";

    private static final Set<String> BUILT_IN = Set.of(
            OA_PURCHASE, OA_REIMBURSEMENT, INV_TRANSFER, INV_STOCK_CHECK,
            HR_HEALTH_CERTIFICATE, OA_ATTENDANCE_LEAVE,
            OA_ATTENDANCE_CORRECTION);

    private ApprovalBusinessCodes()
    {
    }

    public static boolean isBuiltIn(String businessCode)
    {
        return BUILT_IN.contains(businessCode);
    }
}
