package com.erp.system.domain.vo;

import java.util.Date;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Fixed user-management list contract. Operational account identity is returned in full to
 * callers that already passed the user-management permission and data-scope checks; unrelated
 * sensitive profile fields remain excluded.
 */
public class SysUserListVo
{
    /** Keep employee identifiers exact in JavaScript clients. */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Long deptId;
    private String userName;
    private String nickName;
    private String phonenumber;
    private String status;
    private Date createTime;
    private String postNames;
    private Integer roleCount;
    private Integer shopScopeCount;
    private String shopScopeNames;
    private String setupStatus;
    private DepartmentSummary dept;
    private ProfileSummary profile;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String phonenumber) { this.phonenumber = phonenumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getPostNames() { return postNames; }
    public void setPostNames(String postNames) { this.postNames = postNames; }
    public Integer getRoleCount() { return roleCount; }
    public void setRoleCount(Integer roleCount) { this.roleCount = roleCount; }
    public Integer getShopScopeCount() { return shopScopeCount; }
    public void setShopScopeCount(Integer shopScopeCount) { this.shopScopeCount = shopScopeCount; }
    public String getShopScopeNames() { return shopScopeNames; }
    public void setShopScopeNames(String shopScopeNames) { this.shopScopeNames = shopScopeNames; }
    public String getSetupStatus() { return setupStatus; }
    public void setSetupStatus(String setupStatus) { this.setupStatus = setupStatus; }
    public DepartmentSummary getDept() { return dept; }
    public void setDept(DepartmentSummary dept) { this.dept = dept; }
    public ProfileSummary getProfile() { return profile; }
    public void setProfile(ProfileSummary profile) { this.profile = profile; }

    public static class DepartmentSummary
    {
        private Long deptId;
        private String deptName;
        public Long getDeptId() { return deptId; }
        public void setDeptId(Long deptId) { this.deptId = deptId; }
        public String getDeptName() { return deptName; }
        public void setDeptName(String deptName) { this.deptName = deptName; }
    }

    /** Employment metadata only; no identity, address, health, emergency or bank fields. */
    public static class ProfileSummary
    {
        private String employeeNo;
        private String positionNo;
        private String companyName;
        private String storeName;
        private String positionNames;
        private String jobGrade;
        private String employeeStatus;
        private String employeeCategory;
        private Date entryDate;
        private String contractType;
        private String socialType;
        private String legalEntity;

        public String getEmployeeNo() { return employeeNo; }
        public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
        public String getPositionNo() { return positionNo; }
        public void setPositionNo(String positionNo) { this.positionNo = positionNo; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String companyName) { this.companyName = companyName; }
        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }
        public String getPositionNames() { return positionNames; }
        public void setPositionNames(String positionNames) { this.positionNames = positionNames; }
        public String getJobGrade() { return jobGrade; }
        public void setJobGrade(String jobGrade) { this.jobGrade = jobGrade; }
        public String getEmployeeStatus() { return employeeStatus; }
        public void setEmployeeStatus(String employeeStatus) { this.employeeStatus = employeeStatus; }
        public String getEmployeeCategory() { return employeeCategory; }
        public void setEmployeeCategory(String employeeCategory) { this.employeeCategory = employeeCategory; }
        public Date getEntryDate() { return entryDate; }
        public void setEntryDate(Date entryDate) { this.entryDate = entryDate; }
        public String getContractType() { return contractType; }
        public void setContractType(String contractType) { this.contractType = contractType; }
        public String getSocialType() { return socialType; }
        public void setSocialType(String socialType) { this.socialType = socialType; }
        public String getLegalEntity() { return legalEntity; }
        public void setLegalEntity(String legalEntity) { this.legalEntity = legalEntity; }
    }
}
