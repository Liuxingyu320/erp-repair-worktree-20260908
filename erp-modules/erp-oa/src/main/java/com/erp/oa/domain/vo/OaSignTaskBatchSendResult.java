package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** 签约任务逐项批量发送汇总。 */
public class OaSignTaskBatchSendResult
{
    private int totalCount;
    private int sentCount;
    private int alreadySentCount;
    private int inProgressCount;
    private int blockedCount;
    private int failedCount;
    private List<OaSignTaskBatchSendItem> items = new ArrayList<>();

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getSentCount() { return sentCount; }
    public void setSentCount(int sentCount) { this.sentCount = sentCount; }
    public int getAlreadySentCount() { return alreadySentCount; }
    public void setAlreadySentCount(int alreadySentCount) { this.alreadySentCount = alreadySentCount; }
    public int getInProgressCount() { return inProgressCount; }
    public void setInProgressCount(int inProgressCount) { this.inProgressCount = inProgressCount; }
    public int getBlockedCount() { return blockedCount; }
    public void setBlockedCount(int blockedCount) { this.blockedCount = blockedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<OaSignTaskBatchSendItem> getItems() { return items; }
    public void setItems(List<OaSignTaskBatchSendItem> items)
    {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
