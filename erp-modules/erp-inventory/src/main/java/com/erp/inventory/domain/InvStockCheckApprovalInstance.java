package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

public class InvStockCheckApprovalInstance extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private Long checkId;
    private Integer roundNo;
    private String status;
    private Long shopDeptId;
    private String checkNo;
    private Integer profitItemCount;
    private Integer lossItemCount;
    private BigDecimal totalAbsDiffQuantity;
    private String detailSnapshot;
    private String invalidReason;
    private String invalidDetailSnapshot;
    private String adjustmentResultSnapshot;
    private Long submittedUserId;
    private String submittedBy;
    private Date submittedTime;
    private Long finishedUserId;
    private String finishedBy;
    private Date finishedTime;
    private String finishComment;
    private List<InvStockCheckApprovalTask> tasks;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getCheckNo() { return checkNo; }
    public void setCheckNo(String checkNo) { this.checkNo = checkNo; }
    public Integer getProfitItemCount() { return profitItemCount; }
    public void setProfitItemCount(Integer profitItemCount) { this.profitItemCount = profitItemCount; }
    public Integer getLossItemCount() { return lossItemCount; }
    public void setLossItemCount(Integer lossItemCount) { this.lossItemCount = lossItemCount; }
    public BigDecimal getTotalAbsDiffQuantity() { return totalAbsDiffQuantity; }
    public void setTotalAbsDiffQuantity(BigDecimal totalAbsDiffQuantity) { this.totalAbsDiffQuantity = totalAbsDiffQuantity; }
    public String getDetailSnapshot() { return detailSnapshot; }
    public void setDetailSnapshot(String detailSnapshot) { this.detailSnapshot = detailSnapshot; }
    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
    public String getInvalidDetailSnapshot() { return invalidDetailSnapshot; }
    public void setInvalidDetailSnapshot(String invalidDetailSnapshot) { this.invalidDetailSnapshot = invalidDetailSnapshot; }
    public String getAdjustmentResultSnapshot() { return adjustmentResultSnapshot; }
    public void setAdjustmentResultSnapshot(String adjustmentResultSnapshot) { this.adjustmentResultSnapshot = adjustmentResultSnapshot; }
    public Long getSubmittedUserId() { return submittedUserId; }
    public void setSubmittedUserId(Long submittedUserId) { this.submittedUserId = submittedUserId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public Long getFinishedUserId() { return finishedUserId; }
    public void setFinishedUserId(Long finishedUserId) { this.finishedUserId = finishedUserId; }
    public String getFinishedBy() { return finishedBy; }
    public void setFinishedBy(String finishedBy) { this.finishedBy = finishedBy; }
    public Date getFinishedTime() { return finishedTime; }
    public void setFinishedTime(Date finishedTime) { this.finishedTime = finishedTime; }
    public String getFinishComment() { return finishComment; }
    public void setFinishComment(String finishComment) { this.finishComment = finishComment; }
    public List<InvStockCheckApprovalTask> getTasks() { return tasks; }
    public void setTasks(List<InvStockCheckApprovalTask> tasks) { this.tasks = tasks; }
}
