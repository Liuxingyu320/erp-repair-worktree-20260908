package com.erp.system.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 人事签约事件可靠发件箱 sys_hr_sign_event_outbox。
 */
public class SysHrSignEventOutbox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;
    private Long actionId;
    private Long eventVersion;
    private String payloadJson;
    private String status = "PENDING";
    private Integer retryCount = 0;
    private Date nextRetryTime;
    private Integer lastHttpStatus;
    private String lastError;
    private Long remoteTaskId;
    private Long version = 0L;
    private Date sentTime;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }
    public Long getEventVersion() { return eventVersion; }
    public void setEventVersion(Long eventVersion) { this.eventVersion = eventVersion; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer lastHttpStatus) { this.lastHttpStatus = lastHttpStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Long getRemoteTaskId() { return remoteTaskId; }
    public void setRemoteTaskId(Long remoteTaskId) { this.remoteTaskId = remoteTaskId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getSentTime() { return sentTime; }
    public void setSentTime(Date sentTime) { this.sentTime = sentTime; }
}
