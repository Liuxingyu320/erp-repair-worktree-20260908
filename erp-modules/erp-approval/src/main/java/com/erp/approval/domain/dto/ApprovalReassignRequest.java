package com.erp.approval.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ApprovalReassignRequest
{
    @NotNull
    @Positive
    private Long fromCandidateId;
    @NotNull
    @Positive
    private Long toUserId;
    @NotBlank
    @Size(max = 500)
    private String reason;
    @NotBlank
    @Size(max = 128)
    private String requestId;
    public Long getFromCandidateId() { return fromCandidateId; }
    public void setFromCandidateId(Long value) { fromCandidateId = value; }
    public Long getToUserId() { return toUserId; }
    public void setToUserId(Long value) { toUserId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
}
