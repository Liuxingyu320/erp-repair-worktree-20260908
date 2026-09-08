package com.erp.approval.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ApprovalCallbackReplayRequest
{
    @NotBlank
    @Size(max = 500)
    private String reason;
    @NotBlank
    @Size(max = 128)
    private String requestId;
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
