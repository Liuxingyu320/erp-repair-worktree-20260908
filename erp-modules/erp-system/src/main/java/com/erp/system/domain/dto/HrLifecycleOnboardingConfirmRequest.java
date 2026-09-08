package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** HR确认入职时提交并持久化的稳定业务字段。 */
public class HrLifecycleOnboardingConfirmRequest
{
    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64个字符")
    private String requestId;

    @NotNull(message = "入职日期不能为空")
    private LocalDate entryDate;

    @NotNull(message = "合同开始日期不能为空")
    private LocalDate contractStartDate;

    @NotNull(message = "合同结束日期不能为空")
    private LocalDate contractEndDate;

    private LocalDate probationStartDate;
    private LocalDate probationEndDate;

    @NotBlank(message = "合同类型代码不能为空")
    private String contractTypeCode;

    @NotBlank(message = "合同期限代码不能为空")
    private String contractTermCode;

    @NotBlank(message = "社保类型代码不能为空")
    private String socialTypeCode;

    @NotBlank(message = "职级代码不能为空")
    private String jobGradeCode;

    @NotNull(message = "法律主体ID不能为空")
    @Positive(message = "法律主体ID必须为正数")
    private Long legalEntityId;

    @NotBlank(message = "法律主体代码不能为空")
    private String legalEntityCode;

    @NotBlank(message = "法律主体名称不能为空")
    private String legalEntityName;

    @NotNull(message = "基本工资不能为空")
    @DecimalMin(value = "0.00", message = "基本工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "基本工资整数不能超过14位且小数不能超过2位")
    private BigDecimal baseSalary;

    @NotNull(message = "岗位工资不能为空")
    @DecimalMin(value = "0.00", message = "岗位工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "岗位工资整数不能超过14位且小数不能超过2位")
    private BigDecimal postSalary;

    @NotNull(message = "外勤补贴不能为空")
    @DecimalMin(value = "0.00", message = "外勤补贴不能为负数")
    @Digits(integer = 14, fraction = 2, message = "外勤补贴整数不能超过14位且小数不能超过2位")
    private BigDecimal fieldAllowance;

    @NotNull(message = "绩效工资不能为空")
    @DecimalMin(value = "0.00", message = "绩效工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "绩效工资整数不能超过14位且小数不能超过2位")
    private BigDecimal performanceSalary;

    @NotNull(message = "薪资合计不能为空")
    @DecimalMin(value = "0.01", message = "薪资合计必须大于0")
    @Digits(integer = 14, fraction = 2, message = "薪资合计整数不能超过14位且小数不能超过2位")
    private BigDecimal salaryTotal;

    @NotBlank(message = "薪资版本不能为空")
    private String salaryVersion;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate contractStartDate) { this.contractStartDate = contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate) { this.contractEndDate = contractEndDate; }
    public LocalDate getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(LocalDate probationStartDate) { this.probationStartDate = probationStartDate; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate probationEndDate) { this.probationEndDate = probationEndDate; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String contractTypeCode) { this.contractTypeCode = contractTypeCode; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String contractTermCode) { this.contractTermCode = contractTermCode; }
    public String getSocialTypeCode() { return socialTypeCode; }
    public void setSocialTypeCode(String socialTypeCode) { this.socialTypeCode = socialTypeCode; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String jobGradeCode) { this.jobGradeCode = jobGradeCode; }
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
}
