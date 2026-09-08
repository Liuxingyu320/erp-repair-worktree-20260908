package com.erp.inventory.domain.vo;

import java.util.Date;

/** 调拨审批发起发件箱运维汇总。 */
public class InvTransferApprovalStartSummaryVo
{
    private long pendingCount;
    private long retryCount;
    private long submittingCount;
    private long remoteSucceededCount;
    private long failedCount;
    private long succeededCount;
    private Date oldestWaitingTime;
    private long oldestWaitingSeconds;

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long value) { pendingCount = value; }
    public long getRetryCount() { return retryCount; }
    public void setRetryCount(long value) { retryCount = value; }
    public long getSubmittingCount() { return submittingCount; }
    public void setSubmittingCount(long value) { submittingCount = value; }
    public long getRemoteSucceededCount() { return remoteSucceededCount; }
    public void setRemoteSucceededCount(long value) { remoteSucceededCount = value; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long value) { failedCount = value; }
    public long getSucceededCount() { return succeededCount; }
    public void setSucceededCount(long value) { succeededCount = value; }
    public Date getOldestWaitingTime() { return oldestWaitingTime; }
    public void setOldestWaitingTime(Date value) { oldestWaitingTime = value; }
    public long getOldestWaitingSeconds() { return oldestWaitingSeconds; }
    public void setOldestWaitingSeconds(long value) { oldestWaitingSeconds = value; }
}
