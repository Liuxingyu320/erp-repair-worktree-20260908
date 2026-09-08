package com.erp.system.domain.maintenance;

import java.util.Date;

/** Quantity-only audit record. It must never contain an account name, password, or hash. */
public class LegacyCredentialMigrationAudit
{
    private Long auditId;
    private String batchId;
    private String phase;
    private String targetDatabase;
    private int totalUserCount;
    private int sharedMatchCount;
    private int activeSharedMatchCount;
    private int changeRequiredCount;
    private int temporaryCount;
    private Long operatorUserId;
    private String candidateDigest;
    private String status;
    private Date createdAt;

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getTargetDatabase() { return targetDatabase; }
    public void setTargetDatabase(String targetDatabase) { this.targetDatabase = targetDatabase; }
    public int getTotalUserCount() { return totalUserCount; }
    public void setTotalUserCount(int totalUserCount) { this.totalUserCount = totalUserCount; }
    public int getSharedMatchCount() { return sharedMatchCount; }
    public void setSharedMatchCount(int sharedMatchCount) { this.sharedMatchCount = sharedMatchCount; }
    public int getActiveSharedMatchCount() { return activeSharedMatchCount; }
    public void setActiveSharedMatchCount(int activeSharedMatchCount) { this.activeSharedMatchCount = activeSharedMatchCount; }
    public int getChangeRequiredCount() { return changeRequiredCount; }
    public void setChangeRequiredCount(int changeRequiredCount) { this.changeRequiredCount = changeRequiredCount; }
    public int getTemporaryCount() { return temporaryCount; }
    public void setTemporaryCount(int temporaryCount) { this.temporaryCount = temporaryCount; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getCandidateDigest() { return candidateDigest; }
    public void setCandidateDigest(String candidateDigest) { this.candidateDigest = candidateDigest; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}

