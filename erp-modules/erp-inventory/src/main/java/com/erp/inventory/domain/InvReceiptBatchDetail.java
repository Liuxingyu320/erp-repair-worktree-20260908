package com.erp.inventory.domain;

import java.math.BigDecimal;

/** 收货批次中的采购明细行。 */
public class InvReceiptBatchDetail
{
    private Long batchDetailId;
    private Long batchId;
    private Long purchaseOrderId;
    private Long purchaseDetailId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private String itemCode;
    private String itemName;
    private String unit;
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQuantity;
    private BigDecimal inspectedQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal concessionQuantity;

    public Long getBatchDetailId() { return batchDetailId; }
    public void setBatchDetailId(Long batchDetailId) { this.batchDetailId = batchDetailId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public Long getPurchaseDetailId() { return purchaseDetailId; }
    public void setPurchaseDetailId(Long purchaseDetailId) { this.purchaseDetailId = purchaseDetailId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public BigDecimal getPendingQuantity() { return pendingQuantity; }
    public void setPendingQuantity(BigDecimal pendingQuantity) { this.pendingQuantity = pendingQuantity; }
    public BigDecimal getInspectedQuantity() { return inspectedQuantity; }
    public void setInspectedQuantity(BigDecimal inspectedQuantity) { this.inspectedQuantity = inspectedQuantity; }
    public BigDecimal getAcceptedQuantity() { return acceptedQuantity; }
    public void setAcceptedQuantity(BigDecimal acceptedQuantity) { this.acceptedQuantity = acceptedQuantity; }
    public BigDecimal getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(BigDecimal rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public BigDecimal getConcessionQuantity() { return concessionQuantity; }
    public void setConcessionQuantity(BigDecimal concessionQuantity) { this.concessionQuantity = concessionQuantity; }
}
