package com.erp.oa.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public class OaSignBatchPreviewRow
{
    private Boolean selected;
    private Boolean creatable;
    private String skipReason;
    private List<String> missingFields;
    private String packageMatchCode;
    private String packageMatchName;
    private Long employeeId;
    private String employeeNameSnapshot;
    private String employeePhoneSnapshot;
    private String employeeIdCardSnapshot;
    private String employeeAddressSnapshot;
    private Long deptIdSnapshot;
    private String deptNameSnapshot;
    private Long legalEntityIdSnapshot;
    private String legalEntityNameSnapshot;
    private String postNameSnapshot;
    private String postLevelSnapshot;
    private String scenario;
    private String employmentType;
    private String contractTermCodeSnapshot;
    private String socialType;
    private String servicePersonType;
    private String insuranceType;
    private String salaryVersion;
    private String entryDate;
    private String contractStartDate;
    private String contractEndDate;
    private String probationStartDate;
    private String probationEndDate;
    private BigDecimal baseSalary;
    private BigDecimal postSalary;
    private BigDecimal fieldAllowance;
    private BigDecimal performanceSalary;
    private BigDecimal salaryTotal;
    private List<OaSignBatchTemplateOption> templates;

    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }

    public Boolean getCreatable() { return creatable; }
    public void setCreatable(Boolean creatable) { this.creatable = creatable; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }

    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }

    public String getPackageMatchCode() { return packageMatchCode; }
    public void setPackageMatchCode(String packageMatchCode) { this.packageMatchCode = packageMatchCode; }

    public String getPackageMatchName() { return packageMatchName; }
    public void setPackageMatchName(String packageMatchName) { this.packageMatchName = packageMatchName; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getEmployeeNameSnapshot() { return employeeNameSnapshot; }
    public void setEmployeeNameSnapshot(String employeeNameSnapshot) { this.employeeNameSnapshot = employeeNameSnapshot; }

    public String getEmployeePhoneSnapshot() { return employeePhoneSnapshot; }
    public void setEmployeePhoneSnapshot(String employeePhoneSnapshot) { this.employeePhoneSnapshot = employeePhoneSnapshot; }

    public String getEmployeeIdCardSnapshot() { return employeeIdCardSnapshot; }
    public void setEmployeeIdCardSnapshot(String employeeIdCardSnapshot) { this.employeeIdCardSnapshot = employeeIdCardSnapshot; }

    public String getEmployeeAddressSnapshot() { return employeeAddressSnapshot; }
    public void setEmployeeAddressSnapshot(String employeeAddressSnapshot) { this.employeeAddressSnapshot = employeeAddressSnapshot; }

    public Long getDeptIdSnapshot() { return deptIdSnapshot; }
    public void setDeptIdSnapshot(Long deptIdSnapshot) { this.deptIdSnapshot = deptIdSnapshot; }

    public String getDeptNameSnapshot() { return deptNameSnapshot; }
    public void setDeptNameSnapshot(String deptNameSnapshot) { this.deptNameSnapshot = deptNameSnapshot; }

    public Long getLegalEntityIdSnapshot() { return legalEntityIdSnapshot; }
    public void setLegalEntityIdSnapshot(Long legalEntityIdSnapshot) { this.legalEntityIdSnapshot = legalEntityIdSnapshot; }

    public String getLegalEntityNameSnapshot() { return legalEntityNameSnapshot; }
    public void setLegalEntityNameSnapshot(String legalEntityNameSnapshot) { this.legalEntityNameSnapshot = legalEntityNameSnapshot; }

    public String getPostNameSnapshot() { return postNameSnapshot; }
    public void setPostNameSnapshot(String postNameSnapshot) { this.postNameSnapshot = postNameSnapshot; }

    public String getPostLevelSnapshot() { return postLevelSnapshot; }
    public void setPostLevelSnapshot(String postLevelSnapshot) { this.postLevelSnapshot = postLevelSnapshot; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getContractTermCodeSnapshot() { return contractTermCodeSnapshot; }
    public void setContractTermCodeSnapshot(String contractTermCodeSnapshot)
    {
        this.contractTermCodeSnapshot = contractTermCodeSnapshot;
    }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }

    public String getServicePersonType() { return servicePersonType; }
    public void setServicePersonType(String servicePersonType) { this.servicePersonType = servicePersonType; }

    public String getInsuranceType() { return insuranceType; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }

    public String getSalaryVersion() { return salaryVersion; }
    public void setSalaryVersion(String salaryVersion) { this.salaryVersion = salaryVersion; }

    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }

    public String getContractStartDate() { return contractStartDate; }
    public void setContractStartDate(String contractStartDate) { this.contractStartDate = contractStartDate; }

    public String getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(String contractEndDate) { this.contractEndDate = contractEndDate; }

    public String getProbationStartDate() { return probationStartDate; }
    public void setProbationStartDate(String probationStartDate) { this.probationStartDate = probationStartDate; }

    public String getProbationEndDate() { return probationEndDate; }
    public void setProbationEndDate(String probationEndDate) { this.probationEndDate = probationEndDate; }

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

    public List<OaSignBatchTemplateOption> getTemplates() { return templates; }
    public void setTemplates(List<OaSignBatchTemplateOption> templates) { this.templates = templates; }
}
