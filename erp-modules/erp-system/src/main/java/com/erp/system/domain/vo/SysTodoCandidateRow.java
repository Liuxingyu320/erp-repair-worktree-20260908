package com.erp.system.domain.vo;

import java.util.Date;
import com.erp.system.api.domain.SysUserProfile;

/** Lightweight HR profile row used to calculate system reminders. */
public class SysTodoCandidateRow extends SysUserProfile
{
    private static final long serialVersionUID = 1L;

    private String deptName;
    private String deptType;
    private String linkedAccountStatus;
    private String linkedAccountDelFlag;
    private String linkedAccountUserName;
    private String employeeEmail;
    private String employeeSex;
    private String employeeRemark;
    private String postNames;
    private String deptLeader;
    private String deptStatus;
    private String avatar;
    private Long healthCertificateId;
    private Date healthCertificateExpiresOn;

    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getDeptType() { return deptType; }
    public void setDeptType(String deptType) { this.deptType = deptType; }
    public String getLinkedAccountStatus() { return linkedAccountStatus; }
    public void setLinkedAccountStatus(String linkedAccountStatus) { this.linkedAccountStatus = linkedAccountStatus; }
    public String getLinkedAccountDelFlag() { return linkedAccountDelFlag; }
    public void setLinkedAccountDelFlag(String linkedAccountDelFlag) { this.linkedAccountDelFlag = linkedAccountDelFlag; }
    public String getLinkedAccountUserName() { return linkedAccountUserName; }
    public void setLinkedAccountUserName(String linkedAccountUserName) { this.linkedAccountUserName = linkedAccountUserName; }
    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
    public String getEmployeeSex() { return employeeSex; }
    public void setEmployeeSex(String employeeSex) { this.employeeSex = employeeSex; }
    public String getEmployeeRemark() { return employeeRemark; }
    public void setEmployeeRemark(String employeeRemark) { this.employeeRemark = employeeRemark; }
    public String getPostNames() { return postNames; }
    public void setPostNames(String postNames) { this.postNames = postNames; }
    public String getDeptLeader() { return deptLeader; }
    public void setDeptLeader(String deptLeader) { this.deptLeader = deptLeader; }
    public String getDeptStatus() { return deptStatus; }
    public void setDeptStatus(String deptStatus) { this.deptStatus = deptStatus; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getHealthCertificateId() { return healthCertificateId; }
    public void setHealthCertificateId(Long value) { healthCertificateId = value; }
    public Date getHealthCertificateExpiresOn() { return healthCertificateExpiresOn; }
    public void setHealthCertificateExpiresOn(Date value) { healthCertificateExpiresOn = value; }
}
