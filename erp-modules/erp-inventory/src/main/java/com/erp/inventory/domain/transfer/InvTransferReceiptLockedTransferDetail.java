package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

/** Transfer-detail quantity fact locked before receipt progress is planned. */
public class InvTransferReceiptLockedTransferDetail
{
    private Long detailId;
    private Long transferId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private BigDecimal quantity;
    private BigDecimal deliveredQuantity;
    private BigDecimal receivedQuantity;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long value) { detailId = value; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long value) { transferId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal value) {
        deliveredQuantity = value;
    }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal value) {
        receivedQuantity = value;
    }
}
