package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Immutable transition record in a signing task hash chain.
 */
public class OaSignTaskEvent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long eventId;
    private Long taskId;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorUserId;
    private String reasonCode;
    private String reasonDetail;
    private String requestId;
    private String ipAddress;
    private String userAgent;
    private String prevEventHash;
    private String eventHash;
    private Date createdTime;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getPrevEventHash() { return prevEventHash; }
    public void setPrevEventHash(String prevEventHash) { this.prevEventHash = prevEventHash; }
    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
}
