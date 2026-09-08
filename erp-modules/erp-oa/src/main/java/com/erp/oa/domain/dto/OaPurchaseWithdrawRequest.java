package com.erp.oa.domain.dto;

/** 采购申请撤回命令。 */
public class OaPurchaseWithdrawRequest
{
    private String reason;

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
