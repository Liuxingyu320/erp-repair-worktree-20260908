package com.erp.oa.domain.dto;

import jakarta.validation.constraints.Size;

public class OaReimbursementWithdrawRequest
{
    @Size(max = 500, message = "撤回原因不能超过500个字符")
    private String reason;
    private Long expectedApprovalInstanceId;
    private Integer expectedApprovalRound;
    public Long getExpectedApprovalInstanceId() { return expectedApprovalInstanceId; }
    public void setExpectedApprovalInstanceId(Long value) { expectedApprovalInstanceId = value; }
    public Integer getExpectedApprovalRound() { return expectedApprovalRound; }
    public void setExpectedApprovalRound(Integer value) { expectedApprovalRound = value; }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
