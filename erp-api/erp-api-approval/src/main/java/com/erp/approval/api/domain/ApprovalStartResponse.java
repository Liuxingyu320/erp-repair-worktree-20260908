package com.erp.approval.api.domain;

import java.io.Serializable;

/** Result of idempotently starting an approval round. */
public class ApprovalStartResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;

    private Integer businessRound;

    private String status;

    private Boolean created;

    public Long getInstanceId()
    {
        return instanceId;
    }

    public void setInstanceId(Long instanceId)
    {
        this.instanceId = instanceId;
    }

    public Integer getBusinessRound()
    {
        return businessRound;
    }

    public void setBusinessRound(Integer businessRound)
    {
        this.businessRound = businessRound;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Boolean getCreated()
    {
        return created;
    }

    public void setCreated(Boolean created)
    {
        this.created = created;
    }
}
