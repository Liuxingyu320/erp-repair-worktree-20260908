package com.erp.approval.callback;

public record ApprovalBusinessCallbackResult(boolean success,
        boolean invalidated, String code, String message)
{
    public static ApprovalBusinessCallbackResult accepted()
    {
        return new ApprovalBusinessCallbackResult(true, false, "OK", null);
    }
}
