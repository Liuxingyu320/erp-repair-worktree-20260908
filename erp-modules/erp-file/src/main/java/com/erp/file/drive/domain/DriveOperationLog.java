package com.erp.file.drive.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 云盘操作审计持久化模型。
 */
public class DriveOperationLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long operationId;
    private Long spaceId;
    private Long nodeId;
    private String action;
    private Long operatorUserId;
    private Long operatorDeptId;
    private String operatorName;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private String beforeSummary;
    private String afterSummary;
    private String result;
    private String errorCode;
    private Date createTime;

    public Long getOperationId()
    {
        return operationId;
    }

    public void setOperationId(Long operationId)
    {
        this.operationId = operationId;
    }

    public Long getSpaceId()
    {
        return spaceId;
    }

    public void setSpaceId(Long spaceId)
    {
        this.spaceId = spaceId;
    }

    public Long getNodeId()
    {
        return nodeId;
    }

    public void setNodeId(Long nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public Long getOperatorUserId()
    {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId)
    {
        this.operatorUserId = operatorUserId;
    }

    public Long getOperatorDeptId()
    {
        return operatorDeptId;
    }

    public void setOperatorDeptId(Long operatorDeptId)
    {
        this.operatorDeptId = operatorDeptId;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public String getIpAddress()
    {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent()
    {
        return userAgent;
    }

    public void setUserAgent(String userAgent)
    {
        this.userAgent = userAgent;
    }

    public String getBeforeSummary()
    {
        return beforeSummary;
    }

    public void setBeforeSummary(String beforeSummary)
    {
        this.beforeSummary = beforeSummary;
    }

    public String getAfterSummary()
    {
        return afterSummary;
    }

    public void setAfterSummary(String afterSummary)
    {
        this.afterSummary = afterSummary;
    }

    public String getResult()
    {
        return result;
    }

    public void setResult(String result)
    {
        this.result = result;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
