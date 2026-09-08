package com.erp.oa.domain;

import com.erp.common.core.web.domain.BaseEntity;

public class OaSignEvent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long eventId;
    private Long packageId;
    private Long documentId;
    private String eventType;
    private Long operatorUserId;
    private String operatorName;
    /** Display-only nickname. It is not part of the immutable event hash. */
    private String operatorDisplayName;
    private String operatorRole;
    private String ipAddress;
    private String userAgent;
    private String eventPayload;
    private String documentHash;
    private String prevEventHash;
    private String eventHash;
    private String requestId;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getOperatorDisplayName() { return operatorDisplayName; }
    public void setOperatorDisplayName(String operatorDisplayName) { this.operatorDisplayName = operatorDisplayName; }

    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getEventPayload() { return eventPayload; }
    public void setEventPayload(String eventPayload) { this.eventPayload = eventPayload; }

    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }

    public String getPrevEventHash() { return prevEventHash; }
    public void setPrevEventHash(String prevEventHash) { this.prevEventHash = prevEventHash; }

    public String getEventHash() { return eventHash; }
    public void setEventHash(String eventHash) { this.eventHash = eventHash; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
