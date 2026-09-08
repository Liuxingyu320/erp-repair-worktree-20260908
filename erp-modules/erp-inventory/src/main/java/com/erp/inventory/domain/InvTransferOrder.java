package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.inventory.domain.vo.InvTransferApprovalSummary;

public class InvTransferOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long transferId;

    @Excel(name = "调拨单号")
    private String orderNo;

    private Long purchaseId;

    @NotNull(message = "来源组织不能为空")
    private Long fromDeptId;

    @Excel(name = "发货方")
    private String fromDeptName;

    @NotNull(message = "目标组织不能为空")
    private Long toDeptId;

    @Excel(name = "目标门店")
    private String toDeptName;

    /** 当前可见调拨行对应的目标组织父级路径；不落库、不参与导出。 */
    private String toDeptHierarchy;

    @Excel(name = "状态", readConverterExp = "draft=草稿,submitted=审核中,approved=待发货,reserved=已锁库,partial_delivered=部分发货,delivered=待收货,partial_received=部分收货,discrepancy=待差异处理,received=已完成,closed=已关闭,cancelled=已取消,rejected=已驳回")
    private String status;

    private String statusGroup;

    private List<String> statuses;

    @Excel(name = "总数量")
    private BigDecimal totalQuantity;

    /** 列表只读投影中的明细行数；不参与调拨数量或持久化计算。 */
    @Excel(name = "明细行数")
    private Long detailLineCount;

    @Excel(name = "参考总价")
    private BigDecimal totalAmount;

    @Excel(name = "调拨类型", readConverterExp = "warehouse=门店要货,store_return=门店返仓,cross_store=异店调货")
    private String transferType;

    private String returnReasonCode;
    private String returnReasonText;
    private String attachmentNodeIds;

    @Excel(name = "收件人")
    @Size(max = 64, message = "收件人姓名不能超过64个字符")
    private String recipientName;

    @Excel(name = "联系电话")
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String recipientPhone;

    @Excel(name = "收货地址")
    @Size(max = 500, message = "收货地址不能超过500个字符")
    private String shippingAddress;

    private String sourceBusinessType;
    private Long sourceBusinessId;
    private String sourceConfirmStatus;
    private Long sourceConfirmedUserId;
    private String sourceConfirmedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sourceConfirmedTime;

    private String sourceConfirmRemark;
    private Long reselectionFromTransferId;
    private Long version;

    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Long toWarehouseId;
    private String toWarehouseName;
    private Long approvalInstanceId;
    private Integer approvalRound;
    private String approvalEngine;
    private String lastApprovalEventKey;

    @Excel(name = "提交时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submittedTime;

    @Excel(name = "审批时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date approvedTime;

    @Excel(name = "发货时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deliveredTime;

    @Excel(name = "收货时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date receivedTime;

    @Excel(name = "归档时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date archivedTime;

    @Excel(name = "关闭原因")
    private String closeReason;

    /** 查询用店铺筛选；不落库、不参与导出。 */
    private Long storeDeptId;

    /** 创建人身份快照；姓名只取登录用户昵称。 */
    private Long createdByUserId;
    private String createdByName;

    /** 最近提交人身份快照；姓名只取登录用户昵称。 */
    private Long submittedByUserId;
    private String submittedByName;

    private List<InvTransferDetail> details;
    private List<InvTransferShipment> shipments;
    private List<InvTransferDiscrepancy> discrepancies;
    private List<String> approvalWarnings = new ArrayList<>();
    private InvTransferApprovalSummary approvalSummary;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long purchaseId) { this.purchaseId = purchaseId; }
    public Long getFromDeptId() { return fromDeptId; }
    public void setFromDeptId(Long fromDeptId) { this.fromDeptId = fromDeptId; }
    public String getFromDeptName() { return fromDeptName; }
    public void setFromDeptName(String fromDeptName) { this.fromDeptName = fromDeptName; }
    public Long getToDeptId() { return toDeptId; }
    public void setToDeptId(Long toDeptId) { this.toDeptId = toDeptId; }
    public String getToDeptName() { return toDeptName; }
    public void setToDeptName(String toDeptName) { this.toDeptName = toDeptName; }
    public String getToDeptHierarchy() { return toDeptHierarchy; }
    public void setToDeptHierarchy(String toDeptHierarchy) { this.toDeptHierarchy = toDeptHierarchy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusGroup() { return statusGroup; }
    public void setStatusGroup(String statusGroup) { this.statusGroup = statusGroup; }
    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    public Long getDetailLineCount() { return detailLineCount; }
    public void setDetailLineCount(Long value) { detailLineCount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public String getReturnReasonCode() { return returnReasonCode; }
    public void setReturnReasonCode(String value) { returnReasonCode = value; }
    public String getReturnReasonText() { return returnReasonText; }
    public void setReturnReasonText(String value) { returnReasonText = value; }
    public String getAttachmentNodeIds() { return attachmentNodeIds; }
    public void setAttachmentNodeIds(String value) { attachmentNodeIds = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String value) { recipientPhone = value; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String value) { shippingAddress = value; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String value) { sourceBusinessType = value; }
    public Long getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(Long value) { sourceBusinessId = value; }
    public String getSourceConfirmStatus() { return sourceConfirmStatus; }
    public void setSourceConfirmStatus(String value) { sourceConfirmStatus = value; }
    public Long getSourceConfirmedUserId() { return sourceConfirmedUserId; }
    public void setSourceConfirmedUserId(Long value) { sourceConfirmedUserId = value; }
    public String getSourceConfirmedBy() { return sourceConfirmedBy; }
    public void setSourceConfirmedBy(String value) { sourceConfirmedBy = value; }
    public Date getSourceConfirmedTime() { return sourceConfirmedTime; }
    public void setSourceConfirmedTime(Date value) { sourceConfirmedTime = value; }
    public String getSourceConfirmRemark() { return sourceConfirmRemark; }
    public void setSourceConfirmRemark(String value) { sourceConfirmRemark = value; }
    public Long getReselectionFromTransferId() { return reselectionFromTransferId; }
    public void setReselectionFromTransferId(Long value) { reselectionFromTransferId = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public Long getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(Long fromWarehouseId) { this.fromWarehouseId = fromWarehouseId; }
    public String getFromWarehouseName() { return fromWarehouseName; }
    public void setFromWarehouseName(String value) { fromWarehouseName = value; }
    public Long getToWarehouseId() { return toWarehouseId; }
    public void setToWarehouseId(Long toWarehouseId) { this.toWarehouseId = toWarehouseId; }
    public String getToWarehouseName() { return toWarehouseName; }
    public void setToWarehouseName(String value) { toWarehouseName = value; }
    public Long getApprovalInstanceId() { return approvalInstanceId; }
    public void setApprovalInstanceId(Long approvalInstanceId) { this.approvalInstanceId = approvalInstanceId; }
    public Integer getApprovalRound() { return approvalRound; }
    public void setApprovalRound(Integer approvalRound) { this.approvalRound = approvalRound; }
    public String getApprovalEngine() { return approvalEngine; }
    public void setApprovalEngine(String approvalEngine) { this.approvalEngine = approvalEngine; }
    public String getLastApprovalEventKey() { return lastApprovalEventKey; }
    public void setLastApprovalEventKey(String lastApprovalEventKey) { this.lastApprovalEventKey = lastApprovalEventKey; }
    public Date getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(Date submittedTime) { this.submittedTime = submittedTime; }
    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date approvedTime) { this.approvedTime = approvedTime; }
    public Date getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(Date deliveredTime) { this.deliveredTime = deliveredTime; }
    public Date getReceivedTime() { return receivedTime; }
    public void setReceivedTime(Date receivedTime) { this.receivedTime = receivedTime; }
    public Date getArchivedTime() { return archivedTime; }
    public void setArchivedTime(Date archivedTime) { this.archivedTime = archivedTime; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public Long getStoreDeptId() { return storeDeptId; }
    public void setStoreDeptId(Long storeDeptId) { this.storeDeptId = storeDeptId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Long submittedByUserId) { this.submittedByUserId = submittedByUserId; }
    public String getSubmittedByName() { return submittedByName; }
    public void setSubmittedByName(String submittedByName) { this.submittedByName = submittedByName; }
    public List<InvTransferDetail> getDetails() { return details; }
    public void setDetails(List<InvTransferDetail> details) { this.details = details; }
    public List<InvTransferShipment> getShipments() { return shipments; }
    public void setShipments(List<InvTransferShipment> shipments) { this.shipments = shipments; }
    public List<InvTransferDiscrepancy> getDiscrepancies() { return discrepancies; }
    public void setDiscrepancies(List<InvTransferDiscrepancy> value) { discrepancies = value; }
    public List<String> getApprovalWarnings() { return approvalWarnings; }
    public void setApprovalWarnings(List<String> approvalWarnings) {
        this.approvalWarnings = approvalWarnings == null ? new ArrayList<>() : new ArrayList<>(approvalWarnings);
    }
    public InvTransferApprovalSummary getApprovalSummary() { return approvalSummary; }
    public void setApprovalSummary(InvTransferApprovalSummary approvalSummary) { this.approvalSummary = approvalSummary; }
}
