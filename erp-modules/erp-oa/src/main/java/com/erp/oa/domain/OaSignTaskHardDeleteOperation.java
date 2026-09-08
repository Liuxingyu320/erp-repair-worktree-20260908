package com.erp.oa.domain;

import java.util.Date;

/** Durable command ledger for one administrator batch hard-delete request. */
public class OaSignTaskHardDeleteOperation
{
    private Long operationId;
    private String requestId;
    private Long administratorUserId;
    private String payloadHash;
    private String status;
    private String claimToken;
    private Date leaseExpiresTime;
    private Integer totalCount;
    private Integer processedCount;
    private String resultJson;
    private Integer replayCount;
    private String lastError;
    private Long version;
    private Date createdTime;
    private Date updatedTime;
    private Date completedTime;

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getAdministratorUserId() { return administratorUserId; }
    public void setAdministratorUserId(Long administratorUserId) { this.administratorUserId = administratorUserId; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
    public Date getLeaseExpiresTime() { return leaseExpiresTime; }
    public void setLeaseExpiresTime(Date leaseExpiresTime) { this.leaseExpiresTime = leaseExpiresTime; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getProcessedCount() { return processedCount; }
    public void setProcessedCount(Integer processedCount) { this.processedCount = processedCount; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Integer getReplayCount() { return replayCount; }
    public void setReplayCount(Integer replayCount) { this.replayCount = replayCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public Date getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Date updatedTime) { this.updatedTime = updatedTime; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
}
