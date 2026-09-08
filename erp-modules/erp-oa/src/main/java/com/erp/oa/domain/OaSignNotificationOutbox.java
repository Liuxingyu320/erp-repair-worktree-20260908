package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * One idempotent signing notification delivery for one recipient and channel.
 */
public class OaSignNotificationOutbox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;
    private String channel;
    private Long recipientUserId;
    private String businessKey;
    private String payloadJson;
    private String status;
    private Integer retryCount = 0;
    private Date nextRetryTime;
    private String lastResult;
    private String lastError;
    private Long version = 0L;
    private Date createdTime;
    private Date updatedTime;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Long getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Long recipientUserId) { this.recipientUserId = recipientUserId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public Date getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Date updatedTime) { this.updatedTime = updatedTime; }
}
