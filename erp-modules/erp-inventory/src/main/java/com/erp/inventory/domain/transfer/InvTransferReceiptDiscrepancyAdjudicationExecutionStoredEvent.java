package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.Date;

/** Immutable execution event read for exact request replay. */
public class
        InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
{
    private Long eventId;
    private String requestId;
    private Long caseId;
    private Long caseVersionBefore;
    private Long caseVersionAfter;
    private String caseStatusBefore;
    private String caseStatusAfter;
    private Long adjudicationId;
    private String decisionFingerprint;
    private String planStatusBefore;
    private String planStatusAfter;
    private Long actionId;
    private Integer actionSequence;
    private String actionType;
    private String coverageKind;
    private String discrepancyType;
    private String command;
    private String effectKind;
    private String effectReference;
    private String actionStatusBefore;
    private String actionStatusAfter;
    private Long executionVersionBefore;
    private Long executionVersionAfter;
    private BigDecimal quantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal amount;
    private String responsibleParty;
    private String requiredPermission;
    private Long executorUserId;
    private String executorName;
    private String eventFingerprint;
    private Date createTime;

    public Long getEventId() { return eventId; }
    public void setEventId(Long value) { eventId = value; }
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
    public String getCaseStatusBefore() { return caseStatusBefore; }
    public void setCaseStatusBefore(String value) {
        caseStatusBefore = value;
    }
    public String getCaseStatusAfter() { return caseStatusAfter; }
    public void setCaseStatusAfter(String value) { caseStatusAfter = value; }
    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
    }
    public String getPlanStatusBefore() { return planStatusBefore; }
    public void setPlanStatusBefore(String value) {
        planStatusBefore = value;
    }
    public String getPlanStatusAfter() { return planStatusAfter; }
    public void setPlanStatusAfter(String value) {
        planStatusAfter = value;
    }
    public Long getActionId() { return actionId; }
    public void setActionId(Long value) { actionId = value; }
    public Integer getActionSequence() { return actionSequence; }
    public void setActionSequence(Integer value) { actionSequence = value; }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public String getCoverageKind() { return coverageKind; }
    public void setCoverageKind(String value) { coverageKind = value; }
    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String value) {
        discrepancyType = value;
    }
    public String getCommand() { return command; }
    public void setCommand(String value) { command = value; }
    public String getEffectKind() { return effectKind; }
    public void setEffectKind(String value) { effectKind = value; }
    public String getEffectReference() { return effectReference; }
    public void setEffectReference(String value) { effectReference = value; }
    public String getActionStatusBefore() { return actionStatusBefore; }
    public void setActionStatusBefore(String value) {
        actionStatusBefore = value;
    }
    public String getActionStatusAfter() { return actionStatusAfter; }
    public void setActionStatusAfter(String value) {
        actionStatusAfter = value;
    }
    public Long getExecutionVersionBefore() {
        return executionVersionBefore;
    }
    public void setExecutionVersionBefore(Long value) {
        executionVersionBefore = value;
    }
    public Long getExecutionVersionAfter() { return executionVersionAfter; }
    public void setExecutionVersionAfter(Long value) {
        executionVersionAfter = value;
    }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getSourceCostPrice() { return sourceCostPrice; }
    public void setSourceCostPrice(BigDecimal value) {
        sourceCostPrice = value;
    }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) {
        responsibleParty = value;
    }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String value) {
        requiredPermission = value;
    }
    public Long getExecutorUserId() { return executorUserId; }
    public void setExecutorUserId(Long value) { executorUserId = value; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String value) { executorName = value; }
    public String getEventFingerprint() { return eventFingerprint; }
    public void setEventFingerprint(String value) {
        eventFingerprint = value;
    }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }

    public InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
            .StoredEventSnapshot toPolicySnapshot()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .StoredEventSnapshot(eventId, requestId, caseId,
                        caseVersionBefore, caseVersionAfter,
                        caseStatusBefore, caseStatusAfter, adjudicationId,
                        decisionFingerprint, planStatusBefore,
                        planStatusAfter, actionId, actionSequence,
                        actionType, coverageKind, discrepancyType, command,
                        effectKind, effectReference, actionStatusBefore,
                        actionStatusAfter, executionVersionBefore,
                        executionVersionAfter, quantity, sourceCostPrice,
                        amount, responsibleParty, requiredPermission,
                        executorUserId, executorName, eventFingerprint,
                        createTime == null ? null : createTime.toInstant());
    }
}
