package com.erp.oa.domain.vo;

import java.util.Date;

/** 不暴露报销申请快照的审批发起运维视图。 */
public class OaReimbursementApprovalStartOutboxVo
{
    private Long outboxId;
    private Long reimbursementId;
    private Integer businessRound;
    private String idempotencyKey;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private Integer lastHttpStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long remoteInstanceId;
    private String remoteStatus;
    private Integer remoteBusinessRound;
    private Long version;
    private Integer manualReplayCount;
    private String manualReplayBy;
    private Date manualReplayTime;
    private Date createTime;
    private Date updateTime;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long value) { outboxId = value; }
    public Long getReimbursementId() { return reimbursementId; }
    public void setReimbursementId(Long value) { reimbursementId = value; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer value) { businessRound = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer value) { retryCount = value; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date value) { nextRetryTime = value; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer value) { lastHttpStatus = value; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String value) { lastErrorCode = value; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String value) { lastErrorMessage = value; }
    public Long getRemoteInstanceId() { return remoteInstanceId; }
    public void setRemoteInstanceId(Long value) { remoteInstanceId = value; }
    public String getRemoteStatus() { return remoteStatus; }
    public void setRemoteStatus(String value) { remoteStatus = value; }
    public Integer getRemoteBusinessRound() { return remoteBusinessRound; }
    public void setRemoteBusinessRound(Integer value) { remoteBusinessRound = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public Integer getManualReplayCount() { return manualReplayCount; }
    public void setManualReplayCount(Integer value) { manualReplayCount = value; }
    public String getManualReplayBy() { return manualReplayBy; }
    public void setManualReplayBy(String value) { manualReplayBy = value; }
    public Date getManualReplayTime() { return manualReplayTime; }
    public void setManualReplayTime(Date value) { manualReplayTime = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
}
