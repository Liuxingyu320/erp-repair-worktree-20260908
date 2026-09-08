package com.erp.system.domain.vo;

public class SysSalaryUserOptionVo
{
    private Long userId;
    private Long deptId;
    private String userName;
    private String nickName;
    private String phonenumber;
    private String deptName;
    private String status;
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
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

