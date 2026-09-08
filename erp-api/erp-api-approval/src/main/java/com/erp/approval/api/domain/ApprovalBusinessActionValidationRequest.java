package com.erp.approval.api.domain;

import java.io.Serializable;

/** Immutable approval context sent to the business owner before an action. */
public class ApprovalBusinessActionValidationRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private String businessCode;
    private String businessId;
    private Integer businessRound;
    private String action;
    private Long applicantUserId;
    private Long anchorDeptId;
    private String businessSnapshot;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer businessRound) { this.businessRound = businessRound; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long applicantUserId) { this.applicantUserId = applicantUserId; }
    public Long getAnchorDeptId() { return anchorDeptId; }
    public void setAnchorDeptId(Long anchorDeptId) { this.anchorDeptId = anchorDeptId; }
    public String getBusinessSnapshot() { return businessSnapshot; }
    public void setBusinessSnapshot(String businessSnapshot) { this.businessSnapshot = businessSnapshot; }
}
