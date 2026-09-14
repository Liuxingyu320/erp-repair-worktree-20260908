package com.erp.system.domain;

import java.util.Date;

public class SecuritySessionInvalidationOutbox
{
    private Long outboxId;
    private String eventId;
    private String eventType;
    private Long userId;
    private String reasonCode;
    private String retainedSessionDigest;
    private String status;
    private int attempts;
    private Date availableAt;
    private Date nextAttemptAt;
    private String lockOwner;
    private Date lockedAt;
    private String lastErrorCode;
    private Date createdAt;
    private Date processedAt;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getRetainedSessionDigest() { return retainedSessionDigest; }
    public void setRetainedSessionDigest(String value) { this.retainedSessionDigest = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Date getAvailableAt() { return availableAt; }
    public void setAvailableAt(Date availableAt) { this.availableAt = availableAt; }
    public Date getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Date nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLockOwner() { return lockOwner; }
    public void setLockOwner(String lockOwner) { this.lockOwner = lockOwner; }
    public Date getLockedAt() { return lockedAt; }
    public void setLockedAt(Date lockedAt) { this.lockedAt = lockedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getProcessedAt() { return processedAt; }
    public void setProcessedAt(Date processedAt) { this.processedAt = processedAt; }
}

