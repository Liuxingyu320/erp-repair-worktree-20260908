package com.erp.system.domain.vo;

import java.util.Date;

/** 健康证运维摘要，仅包含低基数计数和时间。 */
public class HrHealthCertificateOpsSummaryVo
{
    private long approvalStartFailedCount;
    public long getApprovalStartFailedCount() { return approvalStartFailedCount; }
    public void setApprovalStartFailedCount(long value) { approvalStartFailedCount = value; }
    private long legacyPendingCount;
    private long approvalPendingCount;
    private long approvalSubmittingCount;
    public long getLegacyPendingCount() { return legacyPendingCount; }
    public void setLegacyPendingCount(long value) { legacyPendingCount = value; }
    public long getApprovalPendingCount() { return approvalPendingCount; }
    public void setApprovalPendingCount(long value) { approvalPendingCount = value; }
    public long getApprovalSubmittingCount() { return approvalSubmittingCount; }
    public void setApprovalSubmittingCount(long value) { approvalSubmittingCount = value; }
    private long pendingReviewCount;
    private long validCount;
    private long expiringCount;
    private long expiredCount;
    private long reminderFailureCount;
    private Date oldestPendingTime;
    private long oldestPendingHours;

    public long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(long value) { pendingReviewCount = value; }
    public long getValidCount() { return validCount; }
    public void setValidCount(long value) { validCount = value; }
    public long getExpiringCount() { return expiringCount; }
    public void setExpiringCount(long value) { expiringCount = value; }
    public long getExpiredCount() { return expiredCount; }
    public void setExpiredCount(long value) { expiredCount = value; }
    public long getReminderFailureCount() { return reminderFailureCount; }
    public void setReminderFailureCount(long value) { reminderFailureCount = value; }
    public Date getOldestPendingTime() { return oldestPendingTime; }
    public void setOldestPendingTime(Date value) { oldestPendingTime = value; }
    public long getOldestPendingHours() { return oldestPendingHours; }
    public void setOldestPendingHours(long value) { oldestPendingHours = value; }
}
