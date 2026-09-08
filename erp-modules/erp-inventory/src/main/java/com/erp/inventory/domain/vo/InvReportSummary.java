package com.erp.inventory.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;

public class InvReportSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long stockItemCount;
    private Long warningStockCount;
    private Long missingSafetyStockCount;
    private Long zeroStockCount;
    private BigDecimal totalCurrentQuantity;
    private BigDecimal totalAvailableQuantity;
    private BigDecimal totalStockCost;
    private Long purchaseOrderCount;
    private BigDecimal purchaseAmount;
    private Long salesOrderCount;
    private BigDecimal salesAmount;
    private BigDecimal salesCost;
    private BigDecimal grossMargin;

    public Long getStockItemCount() { return stockItemCount; }
    public void setStockItemCount(Long stockItemCount) { this.stockItemCount = stockItemCount; }
    public Long getWarningStockCount() { return warningStockCount; }
    public void setWarningStockCount(Long warningStockCount) { this.warningStockCount = warningStockCount; }
    public Long getMissingSafetyStockCount() { return missingSafetyStockCount; }
    public void setMissingSafetyStockCount(Long missingSafetyStockCount) { this.missingSafetyStockCount = missingSafetyStockCount; }
    public Long getZeroStockCount() { return zeroStockCount; }
    public void setZeroStockCount(Long zeroStockCount) { this.zeroStockCount = zeroStockCount; }
    public BigDecimal getTotalCurrentQuantity() { return totalCurrentQuantity; }
    public void setTotalCurrentQuantity(BigDecimal totalCurrentQuantity) { this.totalCurrentQuantity = totalCurrentQuantity; }
    public BigDecimal getTotalAvailableQuantity() { return totalAvailableQuantity; }
    public void setTotalAvailableQuantity(BigDecimal totalAvailableQuantity) { this.totalAvailableQuantity = totalAvailableQuantity; }
    public BigDecimal getTotalStockCost() { return totalStockCost; }
    public void setTotalStockCost(BigDecimal totalStockCost) { this.totalStockCost = totalStockCost; }
    public Long getPurchaseOrderCount() { return purchaseOrderCount; }
    public void setPurchaseOrderCount(Long purchaseOrderCount) { this.purchaseOrderCount = purchaseOrderCount; }
    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }
    public Long getSalesOrderCount() { return salesOrderCount; }
    public void setSalesOrderCount(Long salesOrderCount) { this.salesOrderCount = salesOrderCount; }
    public BigDecimal getSalesAmount() { return salesAmount; }
    public void setSalesAmount(BigDecimal salesAmount) { this.salesAmount = salesAmount; }
    public BigDecimal getSalesCost() { return salesCost; }
    public void setSalesCost(BigDecimal salesCost) { this.salesCost = salesCost; }
    public BigDecimal getGrossMargin() { return grossMargin; }
    public void setGrossMargin(BigDecimal grossMargin) { this.grossMargin = grossMargin; }
}
