package com.erp.system.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** HR确认员工转正时提交的稳定业务字段。 */
public class HrRegularizationRequest
{
    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64个字符")
    private String requestId;

    /** Date-only entry: no client position or salary values are accepted. */
    private boolean preservePositionSalary;
    public boolean isPreservePositionSalary() { return preservePositionSalary; }
    public void setPreservePositionSalary(boolean value) { preservePositionSalary = value; }

    @NotNull(message = "实际转正日期不能为空")
    private LocalDate actualRegularizationDate;

    @Positive(message = "岗位ID必须为正数")
    private Long postId;

    @Size(max = 64, message = "岗位代码长度不能超过64个字符")
    private String postCode;

    @Size(max = 50, message = "岗位名称长度不能超过50个字符")
    private String postName;

    @Size(max = 64, message = "职级代码长度不能超过64个字符")
    private String jobGradeCode;

    @Size(max = 64, message = "职级名称长度不能超过64个字符")
    private String jobGradeName;

    @DecimalMin(value = "0.00", message = "基本工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "基本工资整数不能超过14位且小数不能超过2位")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.00", message = "岗位工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "岗位工资整数不能超过14位且小数不能超过2位")
    private BigDecimal postSalary;

    @DecimalMin(value = "0.00", message = "外勤补贴不能为负数")
    @Digits(integer = 14, fraction = 2, message = "外勤补贴整数不能超过14位且小数不能超过2位")
    private BigDecimal fieldAllowance;

    @DecimalMin(value = "0.00", message = "绩效工资不能为负数")
    @Digits(integer = 14, fraction = 2, message = "绩效工资整数不能超过14位且小数不能超过2位")
    private BigDecimal performanceSalary;

    @DecimalMin(value = "0.01", message = "薪资合计必须大于0")
    @Digits(integer = 14, fraction = 2, message = "薪资合计整数不能超过14位且小数不能超过2位")
    private BigDecimal salaryTotal;

    @Size(max = 32, message = "薪资版本长度不能超过32个字符")
    private String salaryVersion;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDate getActualRegularizationDate() { return actualRegularizationDate; }
    public void setActualRegularizationDate(LocalDate actualRegularizationDate)
    {
        this.actualRegularizationDate = actualRegularizationDate;
    }
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
