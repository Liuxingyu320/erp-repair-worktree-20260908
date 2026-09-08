package com.erp.file.drive.domain;

/**
 * 解析个人有效额度所需的有效用户、组织和岗位行。
 */
public class DriveUserQuotaContext
{
    private Long userId;
    private String userName;
    private String nickName;
    private Long deptId;
    private String deptName;
    private Long postId;
    private String postName;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
}

