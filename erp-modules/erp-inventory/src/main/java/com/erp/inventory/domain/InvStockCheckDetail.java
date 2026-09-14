package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvStockCheckDetail
{
    private Long detailId;
    private Long checkId;
    private Long productId;
    private String itemType;
    private Long itemId;
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
    private boolean recountQtySpecified;
    private String recountBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date recountTime;
    private BigDecimal diffQty;
    private String diffType;
    private BigDecimal costPrice;
    private String snapshotVersion;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal previousActualQty;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal previousRecountQty;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal previousBookQty;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean needsSnapshotReview;

    public String getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(String value) { snapshotVersion = value; }
    public BigDecimal getPreviousActualQty() { return previousActualQty; }
    public void setPreviousActualQty(BigDecimal value) { previousActualQty = value; }
    public BigDecimal getPreviousRecountQty() { return previousRecountQty; }
    public void setPreviousRecountQty(BigDecimal value) { previousRecountQty = value; }
    public BigDecimal getPreviousBookQty() { return previousBookQty; }
    public void setPreviousBookQty(BigDecimal value) { previousBookQty = value; }
    public boolean isNeedsSnapshotReview() { return needsSnapshotReview; }
    public void setNeedsSnapshotReview(boolean value) { needsSnapshotReview = value; }

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public String getItemType() { return itemType == null || itemType.isBlank() ? "product" : itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId == null ? productId : itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
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
    public void setRecountQty(BigDecimal recountQty) { this.recountQty = recountQty; this.recountQtySpecified = true; }
    @JsonIgnore
    public boolean isRecountQtySpecified() { return recountQtySpecified; }
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
