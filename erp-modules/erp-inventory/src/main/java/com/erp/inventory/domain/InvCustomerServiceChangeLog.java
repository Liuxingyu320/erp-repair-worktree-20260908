package com.erp.inventory.domain;

import java.util.Date;

public class InvCustomerServiceChangeLog
{
    private Long logId;
    private Long customerId;
    private String requestKey;
    private String changeType;
    private String changedFields;
    private String beforeSummary;
    private String afterSummary;
    private Long operatorUserId;
    private String operatorName;
    private Long shopDeptId;
    private String sourceClient;
    private Date createTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long value) { logId = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String value) { changeType = value; }
    public String getChangedFields() { return changedFields; }
    public void setChangedFields(String value) { changedFields = value; }
    public String getBeforeSummary() { return beforeSummary; }
    public void setBeforeSummary(String value) { beforeSummary = value; }
    public String getAfterSummary() { return afterSummary; }
    public void setAfterSummary(String value) { afterSummary = value; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long value) { operatorUserId = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
