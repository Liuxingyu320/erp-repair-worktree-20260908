package com.erp.oa.domain.dto;

import java.util.List;

public class OaSignBatchCreateDraftsResult
{
    private Integer createdCount;
    private Integer skippedCount;
    private List<Long> packageIds;
    private List<OaSignBatchPreviewRow> skippedRows;

    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }

    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }

    public List<Long> getPackageIds() { return packageIds; }
    public void setPackageIds(List<Long> packageIds) { this.packageIds = packageIds; }

    public List<OaSignBatchPreviewRow> getSkippedRows() { return skippedRows; }
    public void setSkippedRows(List<OaSignBatchPreviewRow> skippedRows) { this.skippedRows = skippedRows; }
}
