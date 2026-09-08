package com.erp.inventory.domain.vo;

import java.math.BigDecimal;

public class InvStockCheckSnapshotChange
{
    private Long detailId;
    private Long productId;
    private String productName;
    private BigDecimal bookQuantity;
    private BigDecimal currentQuantity;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getBookQuantity() { return bookQuantity; }
    public void setBookQuantity(BigDecimal bookQuantity) { this.bookQuantity = bookQuantity; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
}
