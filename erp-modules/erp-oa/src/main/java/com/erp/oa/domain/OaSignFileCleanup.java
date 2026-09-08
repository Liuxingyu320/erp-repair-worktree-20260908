package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * Durable cleanup ledger for files left behind by a committed signing-task hard delete.
 */
public class OaSignFileCleanup extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long cleanupId;
    private Long taskId;
    private Long packageId;
    private String fileReferencesJson;
    private String status;
    private Integer retryCount = 0;
    private Date nextRetryTime;
    private String processingToken;
    private Date leaseExpiresTime;
    private String lastError;
    private Long version = 0L;
    private Date createdTime;
    private Date updatedTime;

    public Long getCleanupId() { return cleanupId; }
    public void setCleanupId(Long cleanupId) { this.cleanupId = cleanupId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getFileReferencesJson() { return fileReferencesJson; }
    public void setFileReferencesJson(String fileReferencesJson) { this.fileReferencesJson = fileReferencesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getProcessingToken() { return processingToken; }
    public void setProcessingToken(String processingToken) { this.processingToken = processingToken; }
    public Date getLeaseExpiresTime() { return leaseExpiresTime; }
    public void setLeaseExpiresTime(Date leaseExpiresTime) { this.leaseExpiresTime = leaseExpiresTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public Date getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Date updatedTime) { this.updatedTime = updatedTime; }
}
