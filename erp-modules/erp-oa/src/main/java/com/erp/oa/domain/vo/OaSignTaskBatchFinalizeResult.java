package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** Aggregate outcome for independently committed batch-finalize items. */
public class OaSignTaskBatchFinalizeResult
{
    private int totalCount;
    private int finalizedCount;
    private int alreadyFinalizedCount;
    private int blockedCount;
    private int failedCount;
    private List<OaSignTaskBatchFinalizeItem> items = new ArrayList<>();

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getFinalizedCount() { return finalizedCount; }
    public void setFinalizedCount(int finalizedCount) { this.finalizedCount = finalizedCount; }
    public int getAlreadyFinalizedCount() { return alreadyFinalizedCount; }
    public void setAlreadyFinalizedCount(int value) { alreadyFinalizedCount = value; }
    public int getBlockedCount() { return blockedCount; }
    public void setBlockedCount(int blockedCount) { this.blockedCount = blockedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<OaSignTaskBatchFinalizeItem> getItems() { return items; }
    public void setItems(List<OaSignTaskBatchFinalizeItem> values)
    {
        items = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
