package com.erp.system.domain;

import com.erp.common.core.web.domain.BaseEntity;

/**
 * Durable idempotency ledger for one recipient/channel/mobile-push event.
 */
public class SysUserPushDelivery extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long deliveryId;

    private Long userId;

    private String channel;

    private String businessKey;

    private String businessKeyHash;

    private String payloadHash;

    private String status;

    private Integer attemptCount;

    private String lastResult;

    private String lastError;

    private Long version;

    public Long getDeliveryId()
    {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId)
    {
        this.deliveryId = deliveryId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getBusinessKey()
    {
        return businessKey;
    }

    public void setBusinessKey(String businessKey)
    {
        this.businessKey = businessKey;
    }

    public String getBusinessKeyHash()
    {
        return businessKeyHash;
    }

    public void setBusinessKeyHash(String businessKeyHash)
    {
        this.businessKeyHash = businessKeyHash;
    }

    public String getPayloadHash()
    {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash)
    {
        this.payloadHash = payloadHash;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public String getLastResult()
    {
        return lastResult;
    }

    public void setLastResult(String lastResult)
    {
        this.lastResult = lastResult;
    }

    public String getLastError()
    {
        return lastError;
    }

    public void setLastError(String lastError)
    {
        this.lastError = lastError;
    }

    public Long getVersion()
    {
        return version;
    }

    public void setVersion(Long version)
    {
        this.version = version;
    }
}
