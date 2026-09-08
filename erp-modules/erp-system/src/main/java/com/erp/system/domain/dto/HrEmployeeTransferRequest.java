package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 有权人员确认员工调岗时提交的稳定业务字段。 */
public class HrEmployeeTransferRequest
{
    @NotBlank @Size(max = 64) private String requestId;
    @NotNull private LocalDate effectiveDate;

    @NotNull @Positive private Long targetDeptId;
    @NotBlank @Size(max = 100) private String targetDeptName;
    @NotNull @Positive private Long postId;
    @NotBlank @Size(max = 64) private String postCode;
    @NotBlank @Size(max = 50) private String postName;
    @NotBlank @Size(max = 64) private String jobGradeCode;
    @NotBlank @Size(max = 64) private String jobGradeName;

    @NotBlank @Size(max = 100) private String workLocation;
    @NotBlank @Size(max = 64) private String workCityLevel;
    private Long directSupervisorId;
    @Size(max = 100) private String directSupervisorName;

    @NotNull @Positive private Long legalEntityId;
    @NotBlank @Size(max = 64) private String legalEntityCode;
    @NotBlank @Size(max = 100) private String legalEntityName;

    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2) private BigDecimal baseSalary;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2) private BigDecimal postSalary;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2) private BigDecimal fieldAllowance;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2) private BigDecimal performanceSalary;
    @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) private BigDecimal salaryTotal;
    @NotBlank @Size(max = 32) private String salaryVersion;

    @Valid private HrTransferRiskConfirmation riskConfirmation;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public Long getTargetDeptId() { return targetDeptId; }
    public void setTargetDeptId(Long targetDeptId) { this.targetDeptId = targetDeptId; }
    public String getTargetDeptName() { return targetDeptName; }
    public void setTargetDeptName(String targetDeptName) { this.targetDeptName = targetDeptName; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String jobGradeCode) { this.jobGradeCode = jobGradeCode; }
    public String getJobGradeName() { return jobGradeName; }
    public void setJobGradeName(String jobGradeName) { this.jobGradeName = jobGradeName; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getWorkCityLevel() { return workCityLevel; }
    public void setWorkCityLevel(String workCityLevel) { this.workCityLevel = workCityLevel; }
    public Long getDirectSupervisorId() { return directSupervisorId; }
    public void setDirectSupervisorId(Long directSupervisorId) { this.directSupervisorId = directSupervisorId; }
    public String getDirectSupervisorName() { return directSupervisorName; }
    public void setDirectSupervisorName(String directSupervisorName) { this.directSupervisorName = directSupervisorName; }
    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long legalEntityId) { this.legalEntityId = legalEntityId; }
    public String getLegalEntityCode() { return legalEntityCode; }
    public void setLegalEntityCode(String legalEntityCode) { this.legalEntityCode = legalEntityCode; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal postSalary) { this.postSalary = postSalary; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal fieldAllowance) { this.fieldAllowance = fieldAllowance; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal performanceSalary) { this.performanceSalary = performanceSalary; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal salaryTotal) { this.salaryTotal = salaryTotal; }
    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }
    public HrTransferRiskConfirmation getRiskConfirmation() { return riskConfirmation; }
    public void setRiskConfirmation(HrTransferRiskConfirmation riskConfirmation) { this.riskConfirmation = riskConfirmation; }
}
