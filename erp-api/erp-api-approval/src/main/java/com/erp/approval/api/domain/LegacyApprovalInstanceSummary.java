package com.erp.approval.api.domain;

import java.io.Serializable;
import java.util.Date;

/** Normalized, read-only instance row from a legacy engine. */
public class LegacyApprovalInstanceSummary implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String engineMode = "LEGACY";
    private String businessCode;
    private Long legacyInstanceId;
    private String businessId;
    private String businessNo;
    private Integer businessRound;
    private String status;
    private Integer currentNodeOrder;
    private String currentNodeName;
    private Long applicantUserId;
    private String applicantName;
    private Long anchorDeptId;
    private String anchorDeptName;
    private Date startedTime;
    private Date finishedTime;

    public String getEngineMode() { return engineMode; }
    public void setEngineMode(String value) { engineMode = value; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String value) { businessCode = value; }
    public Long getLegacyInstanceId() { return legacyInstanceId; }
    public void setLegacyInstanceId(Long value) { legacyInstanceId = value; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String value) { businessId = value; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String value) { businessNo = value; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer value) { businessRound = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getCurrentNodeOrder() { return currentNodeOrder; }
    public void setCurrentNodeOrder(Integer value) { currentNodeOrder = value; }
    public String getCurrentNodeName() { return currentNodeName; }
    public void setCurrentNodeName(String value) { currentNodeName = value; }
    public Long getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Long value) { applicantUserId = value; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String value) { applicantName = value; }
    public Long getAnchorDeptId() { return anchorDeptId; }
    public void setAnchorDeptId(Long value) { anchorDeptId = value; }
    public String getAnchorDeptName() { return anchorDeptName; }
    public void setAnchorDeptName(String value) { anchorDeptName = value; }
    public Date getStartedTime() { return startedTime; }
    public void setStartedTime(Date value) { startedTime = value; }
    public Date getFinishedTime() { return finishedTime; }
    public void setFinishedTime(Date value) { finishedTime = value; }
}
