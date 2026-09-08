package com.erp.oa.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class OaReimbursementExportBatch implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long batchId;
    private String batchNo;
    private Integer reimbursementCount;
    private Integer itemCount;
    private Integer invoiceCount;
    private BigDecimal totalAmount;
    private String archiveName;

    @JsonIgnore
    private String archivePath;

    private Long archiveSize;
    private String archiveSha256;
    private Long createdByUserId;
    private String createdByName;
    private Date createTime;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long value) { batchId = value; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String value) { batchNo = value; }
    public Integer getReimbursementCount() { return reimbursementCount; }
    public void setReimbursementCount(Integer value) { reimbursementCount = value; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer value) { itemCount = value; }
    public Integer getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Integer value) { invoiceCount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { totalAmount = value; }
    public String getArchiveName() { return archiveName; }
    public void setArchiveName(String value) { archiveName = value; }
    public String getArchivePath() { return archivePath; }
    public void setArchivePath(String value) { archivePath = value; }
    public Long getArchiveSize() { return archiveSize; }
    public void setArchiveSize(Long value) { archiveSize = value; }
    public String getArchiveSha256() { return archiveSha256; }
    public void setArchiveSha256(String value) { archiveSha256 = value; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long value) { createdByUserId = value; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String value) { createdByName = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
