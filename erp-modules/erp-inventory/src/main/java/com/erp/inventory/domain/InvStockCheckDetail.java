package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

public class InvStockCheckDetail
{
    private Long detailId;
    private Long checkId;
    private Long productId;
    private String productName;
    private String productCode;
    private Long categoryId;
    private String categoryName;
    private String categoryFullPath;
    private String unit;
    private String spec;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    private String recountRequired;
    private BigDecimal recountQty;
    private String recountBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date recountTime;
    private BigDecimal diffQty;
    private String diffType;
    private BigDecimal costPrice;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryFullPath() { return categoryFullPath; }
    public void setCategoryFullPath(String categoryFullPath) { this.categoryFullPath = categoryFullPath; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public BigDecimal getBookQty() { return bookQty; }
    public void setBookQty(BigDecimal bookQty) { this.bookQty = bookQty; }
    public BigDecimal getActualQty() { return actualQty; }
    public void setActualQty(BigDecimal actualQty) { this.actualQty = actualQty; }
    public String getRecountRequired() { return recountRequired; }
    public void setRecountRequired(String recountRequired) { this.recountRequired = recountRequired; }
    public BigDecimal getRecountQty() { return recountQty; }
    public void setRecountQty(BigDecimal recountQty) { this.recountQty = recountQty; }
    public String getRecountBy() { return recountBy; }
    public void setRecountBy(String recountBy) { this.recountBy = recountBy; }
    public Date getRecountTime() { return recountTime; }
    public void setRecountTime(Date recountTime) { this.recountTime = recountTime; }
    public BigDecimal getDiffQty() { return diffQty; }
    public void setDiffQty(BigDecimal diffQty) { this.diffQty = diffQty; }
    public String getDiffType() { return diffType; }
    public void setDiffType(String diffType) { this.diffType = diffType; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
}
