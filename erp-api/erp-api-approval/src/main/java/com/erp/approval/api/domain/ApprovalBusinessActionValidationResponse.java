package com.erp.approval.api.domain;

import java.io.Serializable;

/** Fail-closed business-owner decision for a pending approval action. */
public class ApprovalBusinessActionValidationResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Boolean accepted;
    private String code;
    private String message;

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
