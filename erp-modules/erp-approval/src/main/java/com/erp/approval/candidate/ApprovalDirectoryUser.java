package com.erp.approval.candidate;

public class ApprovalDirectoryUser
{
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private Integer postSort;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Integer getPostSort() { return postSort; }
    public void setPostSort(Integer postSort) { this.postSort = postSort; }
}
