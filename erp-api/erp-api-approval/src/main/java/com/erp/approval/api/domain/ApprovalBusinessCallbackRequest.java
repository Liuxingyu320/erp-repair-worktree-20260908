package com.erp.approval.api.domain;

import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Idempotent result event delivered from approval to a business service. */
public class ApprovalBusinessCallbackRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 128)
    private String eventKey;
    @NotNull
    @Positive
    private Long instanceId;
    @NotBlank
    @Size(max = 64)
    private String businessCode;
    @NotBlank
    @Size(max = 128)
    private String businessId;
    @NotNull
    @Positive
    private Integer businessRound;
    @NotBlank
    @Size(max = 32)
    private String action;
    @NotBlank
    private String payload;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer businessRound) { this.businessRound = businessRound; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
