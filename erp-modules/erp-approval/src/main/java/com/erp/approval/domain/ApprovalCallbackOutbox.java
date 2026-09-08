package com.erp.approval.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** Reliable, idempotent business-result callback event. */
public class ApprovalCallbackOutbox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;
    private String eventKey;
    private Long instanceId;
    private String businessCode;
    private String businessSource;
    private String businessId;
    private Integer businessRound;
    private String callbackAction;
    private String callbackService;
    private String callbackCode;
    private String payload;
    private String callbackStatus;
    private Integer retryCount;
    private Integer maxRetryCount;
    private Date nextRetryTime;
    private String lockedBy;
    private Date lockUntil;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Date deliveredTime;
    private Long lockVersion;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long outboxId) { this.outboxId = outboxId; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessSource() { return businessSource; }
    public void setBusinessSource(String businessSource) { this.businessSource = businessSource; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer businessRound) { this.businessRound = businessRound; }
    public String getCallbackAction() { return callbackAction; }
    public void setCallbackAction(String callbackAction) { this.callbackAction = callbackAction; }
    public String getCallbackService() { return callbackService; }
    public void setCallbackService(String callbackService) { this.callbackService = callbackService; }
    public String getCallbackCode() { return callbackCode; }
    public void setCallbackCode(String callbackCode) { this.callbackCode = callbackCode; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public Date getLockUntil() { return lockUntil; }
    public void setLockUntil(Date lockUntil) { this.lockUntil = lockUntil; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Date getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(Date deliveredTime) { this.deliveredTime = deliveredTime; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
