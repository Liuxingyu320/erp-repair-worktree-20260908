package com.erp.inventory.domain.vo;

import com.erp.common.core.web.domain.BaseEntity;

/** 调拨审批发起运维查询；组织数据范围由服务层注入。 */
public class InvTransferApprovalStartOutboxQuery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;
    private String status;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long value) { outboxId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
