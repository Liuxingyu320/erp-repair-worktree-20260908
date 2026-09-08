package com.erp.approval.api.domain;

import java.io.Serializable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Command used by an applicant to withdraw an untouched approval round. */
public class ApprovalWithdrawRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotNull
    @Positive
    private Long instanceId;

    @NotNull
    @Positive
    private Long applicantId;

    @Size(max = 500)
    private String reason;

    public Long getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(Long instanceId)
    {
        this.instanceId = instanceId;
    }

    public Long getApplicantId()
    {
        return applicantId;
    }

    public void setApplicantId(Long applicantId)
    {
        this.applicantId = applicantId;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
