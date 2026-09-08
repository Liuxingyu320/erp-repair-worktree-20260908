package com.erp.inventory.domain.transfer;

import java.util.Date;

/** Locked append-only confirmation event used for idempotent replay. */
public class InvTransferReceiptDiscrepancyConfirmationStoredEvent
{
    private Long eventId;
    private String requestId;
    private Long caseId;
    private Long caseVersion;
    private String factFingerprint;
    private String partyRole;
    private Long partyDeptId;
    private String decision;
    private String note;
    private Long operatorUserId;
    private String operatorName;
    private Date createTime;

    public Long getEventId() { return eventId; }
    public void setEventId(Long value) { eventId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersion() { return caseVersion; }
    public void setCaseVersion(Long value) { caseVersion = value; }
    public String getFactFingerprint() { return factFingerprint; }
    public void setFactFingerprint(String value) {
        factFingerprint = value;
    }
    public String getPartyRole() { return partyRole; }
    public void setPartyRole(String value) { partyRole = value; }
    public Long getPartyDeptId() { return partyDeptId; }
    public void setPartyDeptId(Long value) { partyDeptId = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { decision = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long value) { operatorUserId = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
