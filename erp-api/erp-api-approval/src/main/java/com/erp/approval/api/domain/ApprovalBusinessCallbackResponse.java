package com.erp.approval.api.domain;

import java.io.Serializable;

/** Business callback acknowledgement. Business invalidation is not retried. */
public class ApprovalBusinessCallbackResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Boolean accepted;
    private Boolean invalidated;
    private String code;
    private String message;

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }
    public Boolean getInvalidated() { return invalidated; }
    public void setInvalidated(Boolean invalidated) { this.invalidated = invalidated; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
