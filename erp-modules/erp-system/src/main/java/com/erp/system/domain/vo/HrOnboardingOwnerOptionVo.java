package com.erp.system.domain.vo;

import java.io.Serializable;

/** Minimal, non-sensitive owner-picker projection. */
public class HrOnboardingOwnerOptionVo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String label;
    private Long deptId;
    private String deptName;
    private String deptPath;
    private String employeeNo;
    private String phoneMasked;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getDeptPath() { return deptPath; }
    public void setDeptPath(String deptPath) { this.deptPath = deptPath; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getPhoneMasked() { return phoneMasked; }
    public void setPhoneMasked(String phoneMasked) { this.phoneMasked = phoneMasked; }
}
