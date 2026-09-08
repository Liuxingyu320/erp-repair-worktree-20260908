package com.erp.inventory.domain.vo;

/**
 * Typed transfer header reconstructed from an immutable revision snapshot.
 */
public class InvTransferRevisionHeaderVo
{
    private Long transferId;
    private String orderNo;
    private Long purchaseId;
    private Long fromDeptId;
    private String fromDeptName;
    private Long fromWarehouseId;
    private Long toDeptId;
    private String toDeptName;
    private Long toWarehouseId;
    private String documentStatus;
    private String totalQuantity;
    private String totalAmount;
    private String transferType;
    private String returnReasonCode;
    private String returnReasonText;
    private String attachmentNodeIds;
    private String sourceBusinessType;
    private Long sourceBusinessId;
    private String remark;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String value) { orderNo = value; }
    public Long getPurchaseId() { return purchaseId; }
    public void setPurchaseId(Long value) { purchaseId = value; }
    public Long getFromDeptId() { return fromDeptId; }
    public void setFromDeptId(Long value) { fromDeptId = value; }
    public String getFromDeptName() { return fromDeptName; }
    public void setFromDeptName(String value) { fromDeptName = value; }
    public Long getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(Long value) { fromWarehouseId = value; }
    public Long getToDeptId() { return toDeptId; }
    public void setToDeptId(Long value) { toDeptId = value; }
    public String getToDeptName() { return toDeptName; }
    public void setToDeptName(String value) { toDeptName = value; }
    public Long getToWarehouseId() { return toWarehouseId; }
    public void setToWarehouseId(Long value) { toWarehouseId = value; }
    public String getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(String value) { documentStatus = value; }
    public String getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(String value) { totalQuantity = value; }
    public String getTotalAmount() { return totalAmount; }
    public void setTotalAmount(String value) { totalAmount = value; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String value) { transferType = value; }
    public String getReturnReasonCode() { return returnReasonCode; }
    public void setReturnReasonCode(String value) { returnReasonCode = value; }
    public String getReturnReasonText() { return returnReasonText; }
    public void setReturnReasonText(String value) { returnReasonText = value; }
    public String getAttachmentNodeIds() { return attachmentNodeIds; }
    public void setAttachmentNodeIds(String value) { attachmentNodeIds = value; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String value) { sourceBusinessType = value; }
    public Long getSourceBusinessId() { return sourceBusinessId; }
    public void setSourceBusinessId(Long value) { sourceBusinessId = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
