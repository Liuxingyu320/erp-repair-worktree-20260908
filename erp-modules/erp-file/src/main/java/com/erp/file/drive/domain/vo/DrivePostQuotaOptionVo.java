package com.erp.file.drive.domain.vo;

/** 岗位额度策略可选项，同时给出受影响的有效人数。 */
public class DrivePostQuotaOptionVo
{
    private Long postId;
    private String postCode;
    private String postName;
    private Long userCount;
    private Boolean configured;

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public Long getUserCount() { return userCount; }
    public void setUserCount(Long userCount) { this.userCount = userCount; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
}
