package com.erp.approval.api.domain;

import java.io.Serializable;

/** Minimal immutable view returned to a business service. */
public class ApprovalInstanceSnapshot implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;

    private String businessCode;

    private String businessId;

    private Integer businessRound;

    private Long applicantUserId;

    private Long ruleVersionId;

    private String status;

    private String currentNodeName;

    public Long getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(Long instanceId)
    {
        this.instanceId = instanceId;
    }

    public String getBusinessCode()
    {
        return businessCode;
    }

    public void setBusinessCode(String businessCode)
    {
        this.businessCode = businessCode;
    }

    public String getBusinessId()
    {
        return businessId;
    }

    public void setBusinessId(String businessId)
    {
        this.businessId = businessId;
    }

    public Integer getBusinessRound()
    {
        return businessRound;
    }

    public void setBusinessRound(Integer businessRound)
    {
        this.businessRound = businessRound;
    }

    public Long getApplicantUserId()
    {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId)
    {
        this.applicantUserId = applicantUserId;
    }

    public Long getRuleVersionId()
    {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId)
    {
        this.ruleVersionId = ruleVersionId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getCurrentNodeName()
    {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName)
    {
        this.currentNodeName = currentNodeName;
    }
}
