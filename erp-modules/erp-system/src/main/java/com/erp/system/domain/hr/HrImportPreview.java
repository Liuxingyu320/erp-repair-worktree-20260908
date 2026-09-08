package com.erp.system.domain.hr;

import java.util.ArrayList;
import java.util.List;

/**
 * 人事导入预览结果。
 */
public class HrImportPreview
{
    private String importType;
    private int createCount;
    private int updateCount;
    private int failedCount;
    private List<HrImportPreviewRow> rows = new ArrayList<>();

    public String getImportType()
    {
        return importType;
    }

    public void setImportType(String importType)
    {
        this.importType = importType;
    }

    public int getCreateCount()
    {
        return createCount;
    }

    public void setCreateCount(int createCount)
    {
        this.createCount = createCount;
    }

    public int getUpdateCount()
    {
        return updateCount;
    }

    public void setUpdateCount(int updateCount)
    {
        this.updateCount = updateCount;
    }

    public int getFailedCount()
    {
        return failedCount;
    }

    public void setFailedCount(int failedCount)
    {
        this.failedCount = failedCount;
    }

    public List<HrImportPreviewRow> getRows()
    {
        return rows;
    }

    public void setRows(List<HrImportPreviewRow> rows)
    {
        this.rows = rows;
    }
}
