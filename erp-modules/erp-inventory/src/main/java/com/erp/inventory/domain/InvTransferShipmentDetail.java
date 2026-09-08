package com.erp.inventory.domain;

import java.math.BigDecimal;

public class InvTransferShipmentDetail
{
    private Long shipmentDetailId;
    private Long shipmentId;
    private Long transferId;
    private Long transferDetailId;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long productId;
    private String productName;
    private BigDecimal plannedQuantity;
    private BigDecimal shippedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal costPrice;

    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long shipmentDetailId) { this.shipmentDetailId = shipmentDetailId; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long transferDetailId) { this.transferDetailId = transferDetailId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(BigDecimal plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public BigDecimal getShippedQuantity() { return shippedQuantity; }
    public void setShippedQuantity(BigDecimal shippedQuantity) { this.shippedQuantity = shippedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
}
