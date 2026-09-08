package com.erp.system.domain.vo;

/** Safe employee identity fields exposed by the department-leader selector. */
public class SysDeptLeaderOptionVo
{
    private Long userId;
    private String employeeName;
    private String employeeNo;
    private Long deptId;
    private String deptName;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId=userId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName=employeeName; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo=employeeNo; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId=deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName=deptName; }
}
