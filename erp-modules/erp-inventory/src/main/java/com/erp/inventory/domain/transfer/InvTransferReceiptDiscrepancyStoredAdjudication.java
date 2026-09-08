package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

/** Immutable adjudication header read for exact idempotent replay. */
public class InvTransferReceiptDiscrepancyStoredAdjudication
{
    private Long adjudicationId;
    private String requestId;
    private Long caseId;
    private Long caseVersionBefore;
    private Long caseVersionAfter;
    private String factFingerprint;
    private Long sourceConfirmationEventId;
    private Long targetConfirmationEventId;
    private String discrepancyType;
    private BigDecimal discrepancyQuantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal discrepancyAmount;
    private String decisionFingerprint;
    private String adjudicationNote;
    private String evidenceRefs;
    private String requiredPermission;
    private Long adjudicatorUserId;
    private String adjudicatorName;
    private String planStatus;
    private Date createTime;

    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersionBefore() { return caseVersionBefore; }
    public void setCaseVersionBefore(Long value) {
        caseVersionBefore = value;
    }
    public Long getCaseVersionAfter() { return caseVersionAfter; }
    public void setCaseVersionAfter(Long value) { caseVersionAfter = value; }
    public String getFactFingerprint() { return factFingerprint; }
    public void setFactFingerprint(String value) {
        factFingerprint = value;
    }
    public Long getSourceConfirmationEventId() {
        return sourceConfirmationEventId;
    }
    public void setSourceConfirmationEventId(Long value) {
        sourceConfirmationEventId = value;
    }
    public Long getTargetConfirmationEventId() {
        return targetConfirmationEventId;
    }
    public void setTargetConfirmationEventId(Long value) {
        targetConfirmationEventId = value;
    }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) {
        discrepancyType = value;
    }
    public BigDecimal getDiscrepancyQuantity() {
        return discrepancyQuantity;
    }
    public void setDiscrepancyQuantity(BigDecimal value) {
        discrepancyQuantity = value;
    }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getDiscrepancyAmount() { return discrepancyAmount; }
    public void setDiscrepancyAmount(BigDecimal value) {
        discrepancyAmount = value;
    }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
    }
    public String getAdjudicationNote() { return adjudicationNote; }
    public void setAdjudicationNote(String value) {
        adjudicationNote = value;
    }
    public String getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(String value) { evidenceRefs = value; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String value) {
        requiredPermission = value;
    }
    public Long getAdjudicatorUserId() { return adjudicatorUserId; }
    public void setAdjudicatorUserId(Long value) {
        adjudicatorUserId = value;
    }
    public String getAdjudicatorName() { return adjudicatorName; }
    public void setAdjudicatorName(String value) {
        adjudicatorName = value;
    }
    public String getPlanStatus() { return planStatus; }
    public void setPlanStatus(String value) { planStatus = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
