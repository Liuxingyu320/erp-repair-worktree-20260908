package com.erp.inventory.domain.vo;

import com.erp.common.core.web.domain.BaseEntity;

public class InvCustomerServiceAuditQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long customerId;
    private String changeType;
    private String sourceClient;
    private String operatorName;
    private Long shopDeptId;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String value) { changeType = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
}
