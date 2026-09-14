package com.erp.inventory.domain;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class InvSalesReturnDetail
{
    private Long detailId;
    private Long returnId;
    private Long salesDetailId;
    private String itemType;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private BigDecimal returnableQuantity;
    private Long productId;
    private String productName;
    private String sku;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal returnedQuantity;
    /** Frozen cost actually restored by this detail; null marks unverified legacy facts. */
    private BigDecimal returnedCostAmount;

    @JsonIgnore
    public BigDecimal getReturnedCostAmount() { return returnedCostAmount; }
    public void setReturnedCostAmount(BigDecimal value) { returnedCostAmount = value; }

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }
    public Long getSalesDetailId() { return salesDetailId; }
    public void setSalesDetailId(Long salesDetailId) { this.salesDetailId = salesDetailId; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String value) { itemCode = value; }
    public String getItemName() { return itemName; }
    public void setItemName(String value) { itemName = value; }
    public BigDecimal getReturnableQuantity() { return returnableQuantity; }
    public void setReturnableQuantity(BigDecimal value) { returnableQuantity = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(BigDecimal returnedQuantity) { this.returnedQuantity = returnedQuantity; }
}
