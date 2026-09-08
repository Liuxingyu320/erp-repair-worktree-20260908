package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Shipment-detail fact locked before receipt allocation progress. */
public class InvTransferReceiptLockedDetail
{
    private Long shipmentDetailId;
    private Long shipmentId;
    private Long transferId;
    private Long transferDetailId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private BigDecimal shippedQuantity;
    private BigDecimal receivedQuantity;

    public Long getShipmentDetailId() { return shipmentDetailId; }
    public void setShipmentDetailId(Long value) { shipmentDetailId = value; }
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long value) { shipmentId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public Long getTransferDetailId() { return transferDetailId; }
    public void setTransferDetailId(Long value) { transferDetailId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public BigDecimal getShippedQuantity() { return shippedQuantity; }
    public void setShippedQuantity(BigDecimal value) {
        shippedQuantity = value;
    }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal value) {
        receivedQuantity = value;
    }
}
