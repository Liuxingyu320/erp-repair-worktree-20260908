package com.erp.inventory.domain.vo;

import java.util.Date;

/** 当前组织的返仓、收货和差异低基数运维摘要。 */
public class InvTransferOpsSummaryVo
{
    private Long organizationId;
    private long pendingApprovalCount;
    private long pendingShipmentCount;
    private long pendingReceiptCount;
    private long storeReturnProcessingCount;
    private long returnPendingReceiveCount;
    private long openDiscrepancyCount;
    private long pendingQcCount;
    private long returnReceivedLast24h;
    private Date oldestReturnWaitingTime;
    private long oldestReturnWaitingSeconds;

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long value) { organizationId = value; }
    public long getPendingApprovalCount() { return pendingApprovalCount; }
    public void setPendingApprovalCount(long value) { pendingApprovalCount = value; }
    public long getPendingShipmentCount() { return pendingShipmentCount; }
    public void setPendingShipmentCount(long value) { pendingShipmentCount = value; }
    public long getPendingReceiptCount() { return pendingReceiptCount; }
    public void setPendingReceiptCount(long value) { pendingReceiptCount = value; }
    public long getStoreReturnProcessingCount() { return storeReturnProcessingCount; }
    public void setStoreReturnProcessingCount(long value) { storeReturnProcessingCount = value; }
    public long getReturnPendingReceiveCount() { return returnPendingReceiveCount; }
    public void setReturnPendingReceiveCount(long value) { returnPendingReceiveCount = value; }
    public long getOpenDiscrepancyCount() { return openDiscrepancyCount; }
    public void setOpenDiscrepancyCount(long value) { openDiscrepancyCount = value; }
    public long getPendingQcCount() { return pendingQcCount; }
    public void setPendingQcCount(long value) { pendingQcCount = value; }
    public long getReturnReceivedLast24h() { return returnReceivedLast24h; }
    public void setReturnReceivedLast24h(long value) { returnReceivedLast24h = value; }
    public Date getOldestReturnWaitingTime() { return oldestReturnWaitingTime; }
    public void setOldestReturnWaitingTime(Date value) { oldestReturnWaitingTime = value; }
    public long getOldestReturnWaitingSeconds() { return oldestReturnWaitingSeconds; }
    public void setOldestReturnWaitingSeconds(long value) { oldestReturnWaitingSeconds = value; }
}
