package com.erp.inventory.domain.vo;

import java.util.Date;

/**
 * Safe customer-service audit projection. Request keys, store snapshots and
 * raw customer/service content are deliberately not exposed.
 */
public class InvCustomerServiceAuditVo
{
    private Long logId;
    private Long customerId;
    private String customerName;
    private String changeType;
    private String changedFields;
    private String beforeSummary;
    private String afterSummary;
    private Long operatorUserId;
    private String operatorName;
    private String sourceClient;
    private Date createTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long value) { logId = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
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
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
