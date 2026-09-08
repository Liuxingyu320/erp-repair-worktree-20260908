package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;
import java.util.List;

/** Locked case and plan header for one future execution transaction. */
public class
        InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary
{
    private Long caseId;
    private Long caseVersionAtPlan;
    private Long caseVersion;
    private String caseStatus;
    private Long adjudicationId;
    private String decisionFingerprint;
    private String discrepancyType;
    private BigDecimal discrepancyQuantity;
    private BigDecimal sourceCostPrice;
    private BigDecimal discrepancyAmount;
    private String planStatus;

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long value) { caseId = value; }
    public Long getCaseVersionAtPlan() { return caseVersionAtPlan; }
    public void setCaseVersionAtPlan(Long value) {
        caseVersionAtPlan = value;
    }
    public Long getCaseVersion() { return caseVersion; }
    public void setCaseVersion(Long value) { caseVersion = value; }
    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String value) { caseStatus = value; }
    public Long getAdjudicationId() { return adjudicationId; }
    public void setAdjudicationId(Long value) { adjudicationId = value; }
    public String getDecisionFingerprint() { return decisionFingerprint; }
    public void setDecisionFingerprint(String value) {
        decisionFingerprint = value;
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
    public String getPlanStatus() { return planStatus; }
    public void setPlanStatus(String value) { planStatus = value; }

    public InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
            .Boundary toPolicyBoundary(
                    List<InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction>
                            actions)
    {
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .Boundary(caseId, caseVersionAtPlan, caseVersion,
                        caseStatus, adjudicationId, decisionFingerprint,
                        discrepancyType, discrepancyQuantity,
                        sourceCostPrice, discrepancyAmount, planStatus,
                        actions == null ? null : actions.stream()
                                .map(InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction
                                        ::toPolicySnapshot)
                                .toList());
    }
}
