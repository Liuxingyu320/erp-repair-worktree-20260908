package com.erp.approval.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ApprovalTaskActionRequest
{
    @NotBlank
    @Size(max = 128)
    private String requestId;
    private String reason;
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
