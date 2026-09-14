package com.erp.job.domain;

public class SysJobDeleteBatch
{
    private String batchId;
    private Long actorId;
    private String fingerprint;
    public String getBatchId() { return batchId; }
    public void setBatchId(String value) { batchId = value; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long value) { actorId = value; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
}
