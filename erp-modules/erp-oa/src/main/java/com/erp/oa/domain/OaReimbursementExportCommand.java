package com.erp.oa.domain;

/** Durable per-actor export command claim, committed together with its batch. */
public class OaReimbursementExportCommand
{
    private String requestId;
    private Long actorId;
    private String payloadHash;
    private Long batchId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId = value; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String value) { payloadHash = value; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
}
