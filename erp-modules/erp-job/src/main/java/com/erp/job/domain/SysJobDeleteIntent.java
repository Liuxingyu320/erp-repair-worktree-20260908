package com.erp.job.domain;

public class SysJobDeleteIntent
{
    private String intentId;
    private String batchId;
    private Long jobId;
    private String jobGroup;
    private String revision;
    private String status;
    private Integer attempts;
    private String errorCode;
    public String getIntentId() { return intentId; }
    public void setIntentId(String value) { intentId = value; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String value) { batchId = value; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long value) { jobId = value; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String value) { jobGroup = value; }
    public String getRevision() { return revision; }
    public void setRevision(String value) { revision = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer value) { attempts = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
}
