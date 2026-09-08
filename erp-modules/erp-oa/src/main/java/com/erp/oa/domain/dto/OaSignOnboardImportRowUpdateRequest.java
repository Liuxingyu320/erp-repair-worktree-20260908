package com.erp.oa.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.erp.common.core.exception.ServiceException;

/** Explicit HR-editable fields for one import row. */
public class OaSignOnboardImportRowUpdateRequest
{
    @NotNull(message = "行版本不能为空")
    @Positive(message = "行版本无效")
    private Long version;
    private String currentAddress;
    private String contractTypeCode;
    private String socialTypeCode;
    private String contractTermCode;
    private String employeePost;
    private String jobGradeCode;
    private String workLocation;
    private String cityLevel;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private LocalDate probationStartDate;
    private LocalDate probationEndDate;
    private BigDecimal salaryTotal;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private String servicePersonType;
    private String insuranceType;
    private String studentStatus;
    private String schoolName;
    private String retirementStatus;
    private String incomeStartYearMonth;
    private Boolean warningConfirmed;
    private String warningReason;
    private Boolean historicalSupplement;
    private String historicalReason;
    private Boolean noExternalContractConfirmed;
    /** HR confirmation for a low-confidence company match; never accepts company text. */
    private Long legalEntityId;
    /** Required only when the selected company has no unique recommended active contract seal. */
    private Long sealId;

    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored)
    {
        throw new ServiceException("不允许修改字段：" + field);
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public String getContractTypeCode() { return contractTypeCode; }
    public void setContractTypeCode(String contractTypeCode) { this.contractTypeCode = contractTypeCode; }
    public String getSocialTypeCode() { return socialTypeCode; }
    public void setSocialTypeCode(String socialTypeCode) { this.socialTypeCode = socialTypeCode; }
    public String getContractTermCode() { return contractTermCode; }
    public void setContractTermCode(String contractTermCode) { this.contractTermCode = contractTermCode; }
    public String getEmployeePost() { return employeePost; }
    public void setEmployeePost(String employeePost) { this.employeePost = employeePost; }
    public String getJobGradeCode() { return jobGradeCode; }
    public void setJobGradeCode(String jobGradeCode) { this.jobGradeCode = jobGradeCode; }
    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }
    public String getCityLevel() { return cityLevel; }
    public void setCityLevel(String cityLevel) { this.cityLevel = cityLevel; }
    public LocalDate getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(LocalDate contractStartDate) { this.contractStartDate = contractStartDate; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate contractEndDate) { this.contractEndDate = contractEndDate; }
    public LocalDate getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(LocalDate probationStartDate) { this.probationStartDate = probationStartDate; }
    public LocalDate getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(LocalDate probationEndDate) { this.probationEndDate = probationEndDate; }
    public BigDecimal getSalaryTotal() { return salaryTotal; }
    public void setSalaryTotal(BigDecimal salaryTotal) { this.salaryTotal = salaryTotal; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public BigDecimal getPostSalary() { return postSalary; }
    public void setPostSalary(BigDecimal postSalary) { this.postSalary = postSalary; }
    public BigDecimal getFieldAllowance() { return fieldAllowance; }
    public void setFieldAllowance(BigDecimal fieldAllowance) { this.fieldAllowance = fieldAllowance; }
    public BigDecimal getPerformanceSalary() { return performanceSalary; }
    public void setPerformanceSalary(BigDecimal performanceSalary) { this.performanceSalary = performanceSalary; }
    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }
    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }
    public String getStudentStatus() { return studentStatus; }
    public void setStudentStatus(String studentStatus) { this.studentStatus = studentStatus; }
    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getRetirementStatus() { return retirementStatus; }
    public void setRetirementStatus(String retirementStatus) { this.retirementStatus = retirementStatus; }
    public String getIncomeStartYearMonth() { return incomeStartYearMonth; }
    public void setIncomeStartYearMonth(String incomeStartYearMonth) { this.incomeStartYearMonth = incomeStartYearMonth; }
    public Boolean getWarningConfirmed() { return warningConfirmed; }
    public void setWarningConfirmed(Boolean warningConfirmed) { this.warningConfirmed = warningConfirmed; }
    public String getWarningReason() { return warningReason; }
    public void setWarningReason(String warningReason) { this.warningReason = warningReason; }
    public Boolean getHistoricalSupplement() { return historicalSupplement; }
    public void setHistoricalSupplement(Boolean historicalSupplement) { this.historicalSupplement = historicalSupplement; }
    public String getHistoricalReason() { return historicalReason; }
    public void setHistoricalReason(String historicalReason) { this.historicalReason = historicalReason; }
    public Boolean getNoExternalContractConfirmed() { return noExternalContractConfirmed; }
    public void setNoExternalContractConfirmed(Boolean value) { this.noExternalContractConfirmed = value; }
    public Long getLegalEntityId() { return legalEntityId; }
    public void setLegalEntityId(Long value) { legalEntityId = value; }
    public Long getSealId() { return sealId; }
    public void setSealId(Long value) { sealId = value; }
}
