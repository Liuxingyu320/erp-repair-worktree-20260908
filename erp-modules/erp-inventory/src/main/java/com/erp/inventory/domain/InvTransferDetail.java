package com.erp.inventory.domain;

import java.math.BigDecimal;

public class InvTransferDetail
{
    private Long detailId;
    private Long transferId;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private Long productId;
    private String productName;
    private String productCode;
    private BigDecimal quantity;
    private BigDecimal deliveredQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal costPrice;
    private BigDecimal amount;
    private String unit;
    private String spec;
    private String grade;
    private Integer sortOrder;
    private String goodsCondition;
    private String conditionNote;
    private Long lotId;
    private Long sourceLocationId;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
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
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(BigDecimal deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(BigDecimal receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getGoodsCondition() { return goodsCondition; }
    public void setGoodsCondition(String value) { goodsCondition = value; }
    public String getConditionNote() { return conditionNote; }
    public void setConditionNote(String value) { conditionNote = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getSourceLocationId() { return sourceLocationId; }
    public void setSourceLocationId(Long value) { sourceLocationId = value; }
}
