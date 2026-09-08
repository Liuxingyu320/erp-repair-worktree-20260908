package com.erp.system.domain.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 同一个HR对历史调岗补录提交的结构化风险确认。 */
public class HrTransferRiskConfirmation
{
    private boolean confirmed;

    @NotNull private Long employeeId;
    @NotBlank @Size(max = 64) private String employeeName;

    @NotNull @Positive private Long beforeDeptId;
    @NotBlank @Size(max = 100) private String beforeDeptName;
    @NotNull @Positive private Long beforePostId;
    @NotBlank @Size(max = 100) private String beforePostName;

    @NotNull @Positive private Long afterDeptId;
    @NotBlank @Size(max = 100) private String afterDeptName;
    @NotNull @Positive private Long afterPostId;
    @NotBlank @Size(max = 100) private String afterPostName;

    @NotNull private LocalDate effectiveDate;
    @NotNull private LocalDate operationDate;
    @NotBlank @Size(max = 200) private String riskStatement;
    @NotBlank @Size(max = 500) private String reason;

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public Long getBeforeDeptId() { return beforeDeptId; }
    public void setBeforeDeptId(Long beforeDeptId) { this.beforeDeptId = beforeDeptId; }
    public String getBeforeDeptName() { return beforeDeptName; }
    public void setBeforeDeptName(String beforeDeptName) { this.beforeDeptName = beforeDeptName; }
    public Long getBeforePostId() { return beforePostId; }
    public void setBeforePostId(Long beforePostId) { this.beforePostId = beforePostId; }
    public String getBeforePostName() { return beforePostName; }
    public void setBeforePostName(String beforePostName) { this.beforePostName = beforePostName; }
    public Long getAfterDeptId() { return afterDeptId; }
    public void setAfterDeptId(Long afterDeptId) { this.afterDeptId = afterDeptId; }
    public String getAfterDeptName() { return afterDeptName; }
    public void setAfterDeptName(String afterDeptName) { this.afterDeptName = afterDeptName; }
    public Long getAfterPostId() { return afterPostId; }
    public void setAfterPostId(Long afterPostId) { this.afterPostId = afterPostId; }
    public String getAfterPostName() { return afterPostName; }
    public void setAfterPostName(String afterPostName) { this.afterPostName = afterPostName; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public LocalDate getOperationDate() { return operationDate; }
    public void setOperationDate(LocalDate operationDate) { this.operationDate = operationDate; }
    public String getRiskStatement() { return riskStatement; }
    public void setRiskStatement(String riskStatement) { this.riskStatement = riskStatement; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
