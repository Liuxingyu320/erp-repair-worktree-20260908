package com.erp.inventory.domain.transfer;

import java.math.BigDecimal;

public class InvLockedShipmentBalance
{
    private Long balanceId;
    private String itemType;
    private Long itemId;
    private Long productId;
    private Long warehouseId;
    private Long lotId;
    private Long locationId;
    private BigDecimal currentQuantity;
    private BigDecimal lockedQuantity;
    private BigDecimal availableQuantity;
    private BigDecimal costPrice;
    private BigDecimal totalCost;
    private Long version;

    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long value) { balanceId = value; }
    public String getItemType() { return itemType; }
    public void setItemType(String value) { itemType = value; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long value) { itemId = value; }
    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long value) { warehouseId = value; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long value) { lotId = value; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long value) { locationId = value; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal value) { currentQuantity = value; }
    public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public void setLockedQuantity(BigDecimal value) { lockedQuantity = value; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal value) { availableQuantity = value; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal value) { costPrice = value; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal value) { totalCost = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
