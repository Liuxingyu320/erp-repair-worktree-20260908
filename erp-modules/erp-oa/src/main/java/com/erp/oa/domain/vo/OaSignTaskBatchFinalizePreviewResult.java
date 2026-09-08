package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** Summary of the non-mutating company-and-seal batch preview. */
public class OaSignTaskBatchFinalizePreviewResult
{
    private int totalCount;
    private int readyCount;
    private int blockedCount;
    private List<OaSignTaskBatchFinalizePreviewItem> items = new ArrayList<>();

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getReadyCount() { return readyCount; }
    public void setReadyCount(int readyCount) { this.readyCount = readyCount; }
    public int getBlockedCount() { return blockedCount; }
    public void setBlockedCount(int blockedCount) { this.blockedCount = blockedCount; }
    public List<OaSignTaskBatchFinalizePreviewItem> getItems() { return items; }
    public void setItems(List<OaSignTaskBatchFinalizePreviewItem> values)
    {
        items = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
