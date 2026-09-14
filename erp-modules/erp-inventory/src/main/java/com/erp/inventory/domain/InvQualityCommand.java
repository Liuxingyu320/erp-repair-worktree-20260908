package com.erp.inventory.domain;

import java.util.Date;

public class InvQualityCommand
{
    private Long commandId;
    private String requestId;
    private String commandType;
    private String requestFingerprint;
    private Long actorUserId;
    private String actorUsername;
    private Long selectedDeptId;
    private String resourceKey;
    private String status;
    private String resultType;
    private String resultPayload;
    private Date completedTime;
    private Date createTime;

    public Long getCommandId() { return commandId; }
    public void setCommandId(Long value) { commandId = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String value) { commandType = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { requestFingerprint = value; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long value) { actorUserId = value; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String value) { actorUsername = value; }
    public Long getSelectedDeptId() { return selectedDeptId; }
    public void setSelectedDeptId(Long value) { selectedDeptId = value; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String value) { resourceKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getResultType() { return resultType; }
    public void setResultType(String value) { resultType = value; }
    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String value) { resultPayload = value; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date value) { completedTime = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
