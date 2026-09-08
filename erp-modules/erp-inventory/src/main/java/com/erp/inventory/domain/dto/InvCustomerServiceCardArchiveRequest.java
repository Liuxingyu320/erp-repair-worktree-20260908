package com.erp.inventory.domain.dto;

public class InvCustomerServiceCardArchiveRequest
{
    private Long version;
    private String reason;
    private String requestKey;
    private String sourceClient;

    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getSourceClient() { return sourceClient; }
    public void setSourceClient(String value) { sourceClient = value; }
}
