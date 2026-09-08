package com.erp.oa.domain;

import java.util.Date;

/** One HR-selected employee whitelist plus one immutable Excel file. */
public class OaSignOnboardImportBatch
{
    private Long batchId;
    private String batchNo;
    private Long shopDeptId;
    private String shopDeptName;
    private String selectedEmployeeIdsJson;
    private String selectionHash;
    private Integer selectedCount;
    private String originalFileName;
    private Long fileSize;
    private String fileSha256;
    private String sheetName;
    private String status;
    private Integer totalRowCount;
    private Integer matchedCount;
    private Integer excludedCount;
    private Integer errorCount;
    private Integer warningCount;
    private Integer generatedCount;
    private Long createdByUserId;
    private String createdByName;
    private Date expiresTime;
    private Long version;
    private Date createTime;
    private Date updateTime;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public String getSelectedEmployeeIdsJson() { return selectedEmployeeIdsJson; }
    public void setSelectedEmployeeIdsJson(String selectedEmployeeIdsJson) { this.selectedEmployeeIdsJson = selectedEmployeeIdsJson; }
    public String getSelectionHash() { return selectionHash; }
    public void setSelectionHash(String selectionHash) { this.selectionHash = selectionHash; }
    public Integer getSelectedCount() { return selectedCount; }
    public void setSelectedCount(Integer selectedCount) { this.selectedCount = selectedCount; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalRowCount() { return totalRowCount; }
    public void setTotalRowCount(Integer totalRowCount) { this.totalRowCount = totalRowCount; }
    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }
    public Integer getExcludedCount() { return excludedCount; }
    public void setExcludedCount(Integer excludedCount) { this.excludedCount = excludedCount; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
    public Integer getWarningCount() { return warningCount; }
    public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }
    public Integer getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(Integer generatedCount) { this.generatedCount = generatedCount; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Date getExpiresTime() { return expiresTime; }
    public void setExpiresTime(Date expiresTime) { this.expiresTime = expiresTime; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
