package com.erp.inventory.domain;

import java.math.BigDecimal;

public class InvDeliveryNoticeDetail
{
    private Long detailId;
    private Long noticeId;
    private Long salesDetailId;
    private Long warehouseId;
    private String warehouseName;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long productId;
    private String productName;
    private BigDecimal noticeQty;
    private BigDecimal deliveredQty;
    private BigDecimal deliveredCostAmount;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public Long getSalesDetailId() { return salesDetailId; }
    public void setSalesDetailId(Long salesDetailId) { this.salesDetailId = salesDetailId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
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
    public BigDecimal getNoticeQty() { return noticeQty; }
    public void setNoticeQty(BigDecimal noticeQty) { this.noticeQty = noticeQty; }
    public BigDecimal getDeliveredQty() { return deliveredQty; }
    public void setDeliveredQty(BigDecimal deliveredQty) { this.deliveredQty = deliveredQty; }
    public BigDecimal getDeliveredCostAmount() { return deliveredCostAmount; }
    public void setDeliveredCostAmount(BigDecimal deliveredCostAmount) { this.deliveredCostAmount = deliveredCostAmount; }
}
