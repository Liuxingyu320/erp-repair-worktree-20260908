package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

public class InvStockSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long stockItemCount;
    private Long shopCount;
    private Long warehouseCount;
    private Long warningCount;
    private Long lowStockCount;
    private Long zeroStockCount;
    private BigDecimal totalCurrentQuantity;
    private BigDecimal totalLockedQuantity;
    private BigDecimal totalAvailableQuantity;
    private BigDecimal totalCost;
    private BigDecimal myWarehouseQuantity;
    private BigDecimal otherShopQuantity;

    public Long getStockItemCount() { return stockItemCount; }
    public void setStockItemCount(Long stockItemCount) { this.stockItemCount = stockItemCount; }
    public Long getShopCount() { return shopCount; }
    public void setShopCount(Long shopCount) { this.shopCount = shopCount; }
    public Long getWarehouseCount() { return warehouseCount; }
    public void setWarehouseCount(Long warehouseCount) { this.warehouseCount = warehouseCount; }
    public Long getWarningCount() { return warningCount; }
    public void setWarningCount(Long warningCount) { this.warningCount = warningCount; }
    public Long getLowStockCount() { return lowStockCount; }
    public void setLowStockCount(Long lowStockCount) { this.lowStockCount = lowStockCount; }
    public Long getZeroStockCount() { return zeroStockCount; }
    public void setZeroStockCount(Long zeroStockCount) { this.zeroStockCount = zeroStockCount; }
    public BigDecimal getTotalCurrentQuantity() { return totalCurrentQuantity; }
    public void setTotalCurrentQuantity(BigDecimal totalCurrentQuantity) { this.totalCurrentQuantity = totalCurrentQuantity; }
    public BigDecimal getTotalLockedQuantity() { return totalLockedQuantity; }
    public void setTotalLockedQuantity(BigDecimal totalLockedQuantity) { this.totalLockedQuantity = totalLockedQuantity; }
    public BigDecimal getTotalAvailableQuantity() { return totalAvailableQuantity; }
    public void setTotalAvailableQuantity(BigDecimal totalAvailableQuantity) { this.totalAvailableQuantity = totalAvailableQuantity; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public BigDecimal getMyWarehouseQuantity() { return myWarehouseQuantity; }
    public void setMyWarehouseQuantity(BigDecimal myWarehouseQuantity) { this.myWarehouseQuantity = myWarehouseQuantity; }
    public BigDecimal getOtherShopQuantity() { return otherShopQuantity; }
    public void setOtherShopQuantity(BigDecimal otherShopQuantity) { this.otherShopQuantity = otherShopQuantity; }
}
