package com.erp.approval.api.constant;

/** Shared compile-time paths for approval service-to-service contracts. */
public final class ApprovalApiPaths
{
    public static final String INNER_BASE = "/inner/approval";
    public static final String VALIDATE_ACTION = "/validate-action";
    public static final String VALIDATE_ACTION_FULL =
            INNER_BASE + VALIDATE_ACTION;

    private ApprovalApiPaths()
    {
    }
}
