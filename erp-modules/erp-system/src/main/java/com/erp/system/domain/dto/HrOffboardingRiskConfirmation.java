package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 同一个唯一 HR 对高风险离职提交的结构化二次确认。 */
public class HrOffboardingRiskConfirmation
{
    private boolean confirmed;
    @NotNull private Long employeeId;
    @NotBlank @Size(max = 64) private String employeeName;
    @NotNull private HrOffboardingType offboardingType;
    @NotNull private LocalDate lastWorkingDate;
    @NotNull private LocalDate operationDate;
    @NotNull private HrOffboardingCompletionStatus salarySettlementStatus;
    @NotNull private HrOffboardingCompletionStatus assetHandoverStatus;
    @NotNull private HrOffboardingNonCompeteDecision nonCompeteDecision;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2)
    private BigDecimal compensationAmount;
    @NotBlank @Size(max = 200) private String riskStatement;
    @NotBlank @Size(max = 500) private String reason;

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public HrOffboardingType getOffboardingType() { return offboardingType; }
    public void setOffboardingType(HrOffboardingType offboardingType) { this.offboardingType = offboardingType; }
    public LocalDate getLastWorkingDate() { return lastWorkingDate; }
    public void setLastWorkingDate(LocalDate lastWorkingDate) { this.lastWorkingDate = lastWorkingDate; }
    public LocalDate getOperationDate() { return operationDate; }
    public void setOperationDate(LocalDate operationDate) { this.operationDate = operationDate; }
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
    public String getRiskStatement() { return riskStatement; }
    public void setRiskStatement(String riskStatement) { this.riskStatement = riskStatement; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
