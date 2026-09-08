package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaSignOnboardImportBatchView
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long batchId;
    private String batchNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long shopDeptId;
    private String shopDeptName;
    private String originalFileName;
    private String fileSha256;
    private String sheetName;
    private String status;
    private Integer selectedCount;
    private Integer totalRowCount;
    private Integer matchedCount;
    private Integer excludedCount;
    private Integer errorCount;
    private Integer warningCount;
    private Integer generatedCount;
    private Long version;
    private Date expiresTime;
    private List<OaSignOnboardImportRowView> rows = new ArrayList<>();

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String value) { batchNo = value; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String value) { shopDeptName = value; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String value) { originalFileName = value; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String value) { fileSha256 = value; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String value) { sheetName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getSelectedCount() { return selectedCount; }
    public void setSelectedCount(Integer value) { selectedCount = value; }
    public Integer getTotalRowCount() { return totalRowCount; }
    public void setTotalRowCount(Integer value) { totalRowCount = value; }
    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer value) { matchedCount = value; }
    public Integer getExcludedCount() { return excludedCount; }
    public void setExcludedCount(Integer value) { excludedCount = value; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer value) { errorCount = value; }
    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer value) { warningCount = value; }
    public Integer getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(Integer value) { generatedCount = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public Date getExpiresTime() { return expiresTime; }
    public void setExpiresTime(Date value) { expiresTime = value; }
    public List<OaSignOnboardImportRowView> getRows() { return new ArrayList<>(rows); }
    public void setRows(List<OaSignOnboardImportRowView> value)
    {
        rows = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}
