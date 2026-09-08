package com.erp.system.service.support;

/** The retained token is memory-only and is never written to the outbox or logs. */
public class UserSecurityStateChangedEvent
{
    private final String eventId;
    private final Long userId;
    private final String reasonCode;
    private final String retainedToken;

    public UserSecurityStateChangedEvent(String eventId, Long userId, String reasonCode,
            String retainedToken)
    {
        this.eventId = eventId;
        this.userId = userId;
        this.reasonCode = reasonCode;
        this.retainedToken = retainedToken;
    }

    public String getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public String getReasonCode() { return reasonCode; }
    public String getRetainedToken() { return retainedToken; }
}

