package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;

/** 管理员批量硬删除签约任务汇总。 */
public class OaSignTaskBatchDeleteResult
{
    private int totalCount;
    private int deletedCount;
    private int failedCount;
    private List<OaSignTaskBatchDeleteItem> items = new ArrayList<>();

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getDeletedCount() { return deletedCount; }
    public void setDeletedCount(int deletedCount) { this.deletedCount = deletedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<OaSignTaskBatchDeleteItem> getItems() { return items; }
    public void setItems(List<OaSignTaskBatchDeleteItem> items)
    {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
