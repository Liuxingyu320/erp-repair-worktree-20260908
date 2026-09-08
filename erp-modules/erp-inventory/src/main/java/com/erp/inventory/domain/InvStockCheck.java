package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvStockCheck extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long checkId;

    @Excel(name = "盘点单号")
    private String checkNo;

    @Excel(name = "盘点日期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date checkDate;

    private Long shopDeptId;

    @Excel(name = "库存组织")
    private String shopDeptName;

    private Long warehouseId;

    @Excel(name = "盘点仓库")
    private String warehouseName;

    @Excel(name = "盘点范围", readConverterExp = "all=全仓,category=分类,selected=指定商品,sample=抽盘")
    private String checkScope;

    private Long categoryId;

    @Excel(name = "抽盘数量")
    private Integer sampleSize;

    @Excel(name = "盲盘", readConverterExp = "0=否,1=是")
    private String blindCheck;

    private Long counterUserId;

    @Excel(name = "盘点人")
    private String counterName;

    @Excel(name = "截止时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deadline;

    @Excel(name = "复盘阈值")
    private BigDecimal recountThreshold;

    @Excel(name = "盘点商品数")
    private Integer itemCount;

    @Excel(name = "盘盈项数")
    private Integer profitItemCount;

    @Excel(name = "盘亏项数")
    private Integer lossItemCount;

    @Excel(name = "差异数量合计")
    private BigDecimal totalDiffQuantity;

    @Excel(name = "状态", readConverterExp = "draft=草稿,checking=盘点中,pending_approval=待审批,returned=已退回,rejected=已拒绝,invalidated=已失效,completed=已完成,cancelled=已取消")
    private String status;

    private Long approvalInstanceId;
    private Integer approvalRound;
    private String approvalEngine;
    private String lastApprovalEventKey;
    private Long rowVersion;
    private Long submittedUserId;
    private String submittedBy;
    private Date submittedTime;
    private Long approvedUserId;
    private String approvedBy;
    private Date approvedTime;
    private String lastRejectReason;
    private Long lastRejectedUserId;
    private String lastRejectedBy;
    private Date lastRejectedTime;
    private String lastInvalidReason;
    private String lastInvalidDetailSnapshot;
    private Date lastInvalidatedTime;

    private List<InvStockCheckDetail> details;

    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public String getCheckNo() { return checkNo; }
    public void setCheckNo(String checkNo) { this.checkNo = checkNo; }
    public Date getCheckDate() { return checkDate; }
    public void setCheckDate(Date checkDate) { this.checkDate = checkDate; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getCheckScope() { return checkScope; }
    public void setCheckScope(String checkScope) { this.checkScope = checkScope; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public String getBlindCheck() { return blindCheck; }
    public void setBlindCheck(String blindCheck) { this.blindCheck = blindCheck; }
    public Long getCounterUserId() { return counterUserId; }
    public void setCounterUserId(Long counterUserId) { this.counterUserId = counterUserId; }
    public String getCounterName() { return counterName; }
    public void setCounterName(String counterName) { this.counterName = counterName; }
    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }
    public BigDecimal getRecountThreshold() { return recountThreshold; }
    public void setRecountThreshold(BigDecimal recountThreshold) { this.recountThreshold = recountThreshold; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getProfitItemCount() { return profitItemCount; }
    public void setProfitItemCount(Integer profitItemCount) { this.profitItemCount = profitItemCount; }
    public Integer getLossItemCount() { return lossItemCount; }
    public void setLossItemCount(Integer lossItemCount) { this.lossItemCount = lossItemCount; }
    public BigDecimal getTotalDiffQuantity() { return totalDiffQuantity; }
    public void setTotalDiffQuantity(BigDecimal totalDiffQuantity) { this.totalDiffQuantity = totalDiffQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer approvalRound) { this.approvalRound = approvalRound; }
    public String getApprovalEngine() { return approvalEngine; }
    public void setApprovalEngine(String approvalEngine) { this.approvalEngine = approvalEngine; }
    public String getLastApprovalEventKey() { return lastApprovalEventKey; }
    public void setLastApprovalEventKey(String lastApprovalEventKey) { this.lastApprovalEventKey = lastApprovalEventKey; }
    public Long getRowVersion() { return rowVersion; }
    public void setRowVersion(Long rowVersion) { this.rowVersion = rowVersion; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long submittedUserId) { this.submittedUserId = submittedUserId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public Long getApprovedUserId() { return approvedUserId; }
    public void setApprovedUserId(Long approvedUserId) { this.approvedUserId = approvedUserId; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date approvedTime) { this.approvedTime = approvedTime; }
    public String getLastRejectReason() { return lastRejectReason; }
    public void setLastRejectReason(String lastRejectReason) { this.lastRejectReason = lastRejectReason; }
    public Long getLastRejectedUserId() { return lastRejectedUserId; }
    public void setLastRejectedUserId(Long lastRejectedUserId) { this.lastRejectedUserId = lastRejectedUserId; }
    public String getLastRejectedBy() { return lastRejectedBy; }
    public void setLastRejectedBy(String lastRejectedBy) { this.lastRejectedBy = lastRejectedBy; }
    public Date getLastRejectedTime() { return lastRejectedTime; }
    public void setLastRejectedTime(Date lastRejectedTime) { this.lastRejectedTime = lastRejectedTime; }
    public String getLastInvalidReason() { return lastInvalidReason; }
    public void setLastInvalidReason(String lastInvalidReason) { this.lastInvalidReason = lastInvalidReason; }
    public String getLastInvalidDetailSnapshot() { return lastInvalidDetailSnapshot; }
    public void setLastInvalidDetailSnapshot(String lastInvalidDetailSnapshot) { this.lastInvalidDetailSnapshot = lastInvalidDetailSnapshot; }
    public Date getLastInvalidatedTime() { return lastInvalidatedTime; }
    public void setLastInvalidatedTime(Date lastInvalidatedTime) { this.lastInvalidatedTime = lastInvalidatedTime; }
    public List<InvStockCheckDetail> getDetails() { return details; }
    public void setDetails(List<InvStockCheckDetail> details) { this.details = details; }
}
