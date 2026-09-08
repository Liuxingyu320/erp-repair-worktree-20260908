package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** One adjudication action read under the fixed execution lock order. */
public class
        InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction
{
    private Long actionId;
    private Long adjudicationId;
    private Long caseId;
    private Integer sequence;
    private String actionType;
    private String coverageKind;
    private BigDecimal quantity;
    private BigDecimal amount;
    private String responsibleParty;
    private String executionStatus;
    private Long executionVersion;
    private String effectReference;

    public Long getActionId() { return actionId; }
    public void setActionId(Long value) { actionId = value; }
    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer value) { sequence = value; }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public String getCoverageKind() { return coverageKind; }
    public void setCoverageKind(String value) { coverageKind = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getResponsibleParty() { return responsibleParty; }
    public void setResponsibleParty(String value) {
        responsibleParty = value;
    }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String value) {
        executionStatus = value;
    }
    public Long getExecutionVersion() { return executionVersion; }
    public void setExecutionVersion(Long value) {
        executionVersion = value;
    }
    public String getEffectReference() { return effectReference; }
    public void setEffectReference(String value) {
        effectReference = value;
    }

    public InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
            .ActionSnapshot toPolicySnapshot()
    {
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .ActionSnapshot(actionId, sequence, actionType,
                        coverageKind, quantity, amount, responsibleParty,
                        executionStatus, executionVersion,
                        effectReference);
    }
}
