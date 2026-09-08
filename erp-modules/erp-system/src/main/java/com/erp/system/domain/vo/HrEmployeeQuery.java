package com.erp.system.domain.vo;

import java.time.LocalDate;
import com.erp.common.core.web.domain.BaseEntity;

/** Data-scoped HR employee-master filters. */
public class HrEmployeeQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;
    private Long userId;
    private String keyword;
    private Long deptId;
    private String employeeStatus;
    private String employeeCategory;
    private String completenessStatus;
    private String completenessMetric;
    private String accountConfigurationStatus;
    private String healthCertificateStatus;
    private LocalDate healthCertificateExpiresFrom;
    private LocalDate healthCertificateExpiresTo;
    private Boolean contractDue;
    private Boolean offboardAccountOnly;
    private java.time.LocalDate contractDueFrom;
    private java.time.LocalDate contractDueTo;
    private Integer maxRows;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getEmployeeStatus() { return employeeStatus; }
    public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public String getCompletenessStatus() { return completenessStatus; }
    public void setCompletenessStatus(String completenessStatus) { this.completenessStatus = completenessStatus; }
    public boolean hasCompletenessFilter(){return completenessStatus!=null&&!completenessStatus.isBlank();}
    public String getCompletenessMetric(){return completenessMetric;}
    public void setCompletenessMetric(String completenessMetric){this.completenessMetric=completenessMetric;}
    public String getAccountConfigurationStatus() { return accountConfigurationStatus; }
    public void setAccountConfigurationStatus(String accountConfigurationStatus) { this.accountConfigurationStatus = accountConfigurationStatus; }
    public String getHealthCertificateStatus() { return healthCertificateStatus; }
    public void setHealthCertificateStatus(String healthCertificateStatus) { this.healthCertificateStatus = healthCertificateStatus; }
    public LocalDate getHealthCertificateExpiresFrom() { return healthCertificateExpiresFrom; }
    public void setHealthCertificateExpiresFrom(LocalDate value) { healthCertificateExpiresFrom = value; }
    public LocalDate getHealthCertificateExpiresTo() { return healthCertificateExpiresTo; }
    public void setHealthCertificateExpiresTo(LocalDate value) { healthCertificateExpiresTo = value; }
    public Boolean getContractDue() { return contractDue; }
    public void setContractDue(Boolean contractDue) { this.contractDue=contractDue; }
    public Boolean getOffboardAccountOnly() { return offboardAccountOnly; }
    public void setOffboardAccountOnly(Boolean offboardAccountOnly) { this.offboardAccountOnly=offboardAccountOnly; }
    public java.time.LocalDate getContractDueFrom() { return contractDueFrom; }
    public void setContractDueFrom(java.time.LocalDate contractDueFrom) { this.contractDueFrom=contractDueFrom; }
    public java.time.LocalDate getContractDueTo() { return contractDueTo; }
    public void setContractDueTo(java.time.LocalDate contractDueTo) { this.contractDueTo=contractDueTo; }
    public Integer getMaxRows(){return maxRows;}
    public void setMaxRows(Integer maxRows){this.maxRows=maxRows;}
}
