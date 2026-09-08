package com.erp.oa.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** OA 采购统一审批发起可恢复发件箱。 */
public class OaPurchaseApprovalStartOutbox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long outboxId;
    private Long purchaseId;
    private Integer businessRound;
    private String idempotencyKey;
    private String requestJson;
    private String status;
    private Integer retryCount;
    private Date nextRetryTime;
    private Integer lastHttpStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long remoteInstanceId;
    private String remoteStatus;
    private Integer remoteBusinessRound;
    private Long purchaseVersion;
    private Long version;
    private Date remoteSucceededTime;
    private Date completedTime;
    private Integer manualReplayCount;
    private String manualReplayBy;
    private Date manualReplayTime;

    public Long getOutboxId() { return outboxId; }
    public void setOutboxId(Long value) { outboxId = value; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long value) { purchaseId = value; }
    public Integer getBusinessRound() { return businessRound; }
    public void setBusinessRound(Integer value) { businessRound = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String value) { requestJson = value; }
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
    public Long getPurchaseVersion() { return purchaseVersion; }
    public void setPurchaseVersion(Long value) { purchaseVersion = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public Date getRemoteSucceededTime() { return remoteSucceededTime; }
    public void setRemoteSucceededTime(Date value) { remoteSucceededTime = value; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date value) { completedTime = value; }
    public Integer getManualReplayCount() { return manualReplayCount; }
    public void setManualReplayCount(Integer value) { manualReplayCount = value; }
    public String getManualReplayBy() { return manualReplayBy; }
    public void setManualReplayBy(String value) { manualReplayBy = value; }
    public Date getManualReplayTime() { return manualReplayTime; }
    public void setManualReplayTime(Date value) { manualReplayTime = value; }
}
