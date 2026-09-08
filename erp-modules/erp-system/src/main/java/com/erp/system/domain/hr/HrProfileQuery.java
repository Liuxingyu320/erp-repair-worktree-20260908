package com.erp.system.domain.hr;

import com.erp.common.core.web.domain.BaseEntity;

/**
 * 人事员工档案查询条件。
 */
public class HrProfileQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long profileId;
    private Long userId;
    private Long deptId;
    private String employeeName;
    private String phoneNumber;
    private String employeeNo;
    private String companyName;
    private String deptLevel1Name;
    private String storeName;
    private String positionNames;
    private String employeeStatus;
    private String employeeCategory;
    private String onboardingStatus;
    private String completenessStatus;
    private Boolean offboardAccountOnly;

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDeptLevel1Name() { return deptLevel1Name; }
    public void setDeptLevel1Name(String deptLevel1Name) { this.deptLevel1Name = deptLevel1Name; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getPositionNames() { return positionNames; }
    public void setPositionNames(String positionNames) { this.positionNames = positionNames; }
    public String getEmployeeStatus() { return employeeStatus; }
    public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
    public String getEmployeeCategory() { return employeeCategory; }
    public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
    public String getOnboardingStatus() { return onboardingStatus; }
    public void setOnboardingStatus(String onboardingStatus) { this.onboardingStatus = onboardingStatus; }
    public String getCompletenessStatus() { return completenessStatus; }
    public void setCompletenessStatus(String completenessStatus) { this.completenessStatus = completenessStatus; }
    public Boolean getOffboardAccountOnly() { return offboardAccountOnly; }
    public void setOffboardAccountOnly(Boolean offboardAccountOnly) { this.offboardAccountOnly = offboardAccountOnly; }
}
