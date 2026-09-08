package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;

public class InvInboundRecord
{
    private Long inboundId;
    private Long purchaseOrderId;
    private Long purchaseDetailId;
    private String orderNo;
    private String itemType;
    private Long itemId;
    private Long productId;
    private Long shopDeptId;
    private Long warehouseId;
    private Long receiptBatchId;
    private Long receiptBatchDetailId;
    private Long qualityInspectionId;
    private BigDecimal quantity;
    private BigDecimal inspectedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal concessionQuantity;
    private String qcResult;
    private String qcUser;
    private Date qcTime;
    private String qcRemark;
    private String createBy;
    private Date createTime;
    private String remark;

    public Long getInboundId() { return inboundId; }
    public void setInboundId(Long inboundId) { this.inboundId = inboundId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public Long getPurchaseDetailId() { return purchaseDetailId; }
    public void setPurchaseDetailId(Long purchaseDetailId) { this.purchaseDetailId = purchaseDetailId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getReceiptBatchId() { return receiptBatchId; }
    public void setReceiptBatchId(Long receiptBatchId) { this.receiptBatchId = receiptBatchId; }
    public Long getReceiptBatchDetailId() { return receiptBatchDetailId; }
    public void setReceiptBatchDetailId(Long receiptBatchDetailId) { this.receiptBatchDetailId = receiptBatchDetailId; }
    public Long getQualityInspectionId() { return qualityInspectionId; }
    public void setQualityInspectionId(Long qualityInspectionId) { this.qualityInspectionId = qualityInspectionId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getInspectedQuantity() { return inspectedQuantity; }
    public void setInspectedQuantity(BigDecimal inspectedQuantity) { this.inspectedQuantity = inspectedQuantity; }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public BigDecimal getConcessionQuantity() { return concessionQuantity; }
    public void setConcessionQuantity(BigDecimal concessionQuantity) { this.concessionQuantity = concessionQuantity; }
    public String getQcResult() { return qcResult; }
    public void setQcResult(String qcResult) { this.qcResult = qcResult; }
    public String getQcUser() { return qcUser; }
    public void setQcUser(String qcUser) { this.qcUser = qcUser; }
    public Date getQcTime() { return qcTime; }
    public void setQcTime(Date qcTime) { this.qcTime = qcTime; }
    public String getQcRemark() { return qcRemark; }
    public void setQcRemark(String qcRemark) { this.qcRemark = qcRemark; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
