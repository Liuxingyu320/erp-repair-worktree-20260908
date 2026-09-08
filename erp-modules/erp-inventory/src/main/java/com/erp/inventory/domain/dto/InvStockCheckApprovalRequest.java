package com.erp.inventory.domain.dto;

public class InvStockCheckApprovalRequest
{
    private Long instanceId;
    private String comment;

    public Long getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(Long instanceId)
    {
        this.instanceId = instanceId;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }
}
