package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 唯一 HR 确认员工离职时提交的稳定业务字段。 */
public class HrOffboardingConfirmRequest
{
    @NotBlank @Size(max = 64) private String requestId;
    @NotNull private LocalDate lastWorkingDate;
    @NotNull private HrOffboardingType offboardingType;
    @NotBlank @Size(max = 500) private String reason;
    @NotNull private HrOffboardingCompletionStatus salarySettlementStatus;
    @NotNull private HrOffboardingCompletionStatus assetHandoverStatus;
    @NotNull private HrOffboardingNonCompeteDecision nonCompeteDecision;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2)
    private BigDecimal compensationAmount;
    @Size(max = 500) private String compensationNote;
    @Valid private HrOffboardingRiskConfirmation riskConfirmation;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDate getLastWorkingDate() { return lastWorkingDate; }
    public void setLastWorkingDate(LocalDate lastWorkingDate) { this.lastWorkingDate = lastWorkingDate; }
    public HrOffboardingType getOffboardingType() { return offboardingType; }
    public void setOffboardingType(HrOffboardingType offboardingType) { this.offboardingType = offboardingType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public HrOffboardingCompletionStatus getSalarySettlementStatus() { return salarySettlementStatus; }
    public void setSalarySettlementStatus(HrOffboardingCompletionStatus salarySettlementStatus)
    {
        this.salarySettlementStatus = salarySettlementStatus;
    }
    public HrOffboardingCompletionStatus getAssetHandoverStatus() { return assetHandoverStatus; }
    public void setAssetHandoverStatus(HrOffboardingCompletionStatus assetHandoverStatus)
    {
        this.assetHandoverStatus = assetHandoverStatus;
    }
    public HrOffboardingNonCompeteDecision getNonCompeteDecision() { return nonCompeteDecision; }
    public void setNonCompeteDecision(HrOffboardingNonCompeteDecision nonCompeteDecision)
    {
        this.nonCompeteDecision = nonCompeteDecision;
    }
    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal compensationAmount) { this.compensationAmount = compensationAmount; }
    public String getCompensationNote() { return compensationNote; }
    public void setCompensationNote(String compensationNote) { this.compensationNote = compensationNote; }
    public HrOffboardingRiskConfirmation getRiskConfirmation() { return riskConfirmation; }
    public void setRiskConfirmation(HrOffboardingRiskConfirmation riskConfirmation)
    {
        this.riskConfirmation = riskConfirmation;
    }
}
