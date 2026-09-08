package com.erp.oa.domain.dto;

import jakarta.validation.constraints.Size;

public class OaReimbursementWithdrawRequest
{
    @Size(max = 500, message = "撤回原因不能超过500个字符")
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
