package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

public class InvStock extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long stockId;

    private String itemType;

    private Long itemId;

    private Long productId;

    private String itemCode;

    private String itemName;

    @Excel(name = "商品编码")
    private String productCode;

    @Excel(name = "商品名称")
    private String productName;

    private Long categoryId;

    @Excel(name = "商品分类")
    private String categoryName;

    @Excel(name = "分类路径")
    private String categoryFullPath;

    private String itemCategoryName;

    private String itemCategoryFullPath;

    @Excel(name = "等级")
    private String grade;

    @Excel(name = "规格")
    private String spec;

    @Excel(name = "单位")
    private String unit;

    private String itemUnit;

    private String itemSpec;

    private String itemGrade;

    private String imageUrl;

    private String packageImageUrl;

    private String dryTeaImageUrl;

    private String teaSoupImageUrl;

    private String leafBottomImageUrl;

    private String extraImageUrl;

    private Long shopDeptId;

    @Excel(name = "库存组织")
    private String shopDeptName;

    private Long warehouseId;

    @Excel(name = "库存仓库")
    private String warehouseName;

    @Excel(name = "批次号")
    private String batchNo;

    @Excel(name = "效期", dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    @Excel(name = "序列号")
    private String serialNo;

    @Excel(name = "库位编码")
    private String locationCode;

    @Excel(name = "库位名称")
    private String locationName;

    @Excel(name = "当前库存")
    private BigDecimal currentQuantity;

    @Excel(name = "锁定库存")
    private BigDecimal lockedQuantity;

    @Excel(name = "可用库存")
    private BigDecimal availableQuantity;

    @Excel(name = "移动加权成本价")
    private BigDecimal costPrice;

    /** 商品或礼盒档案中维护的参考成本价，与库存移动加权成本分开 */
    private BigDecimal referenceCostPrice;

    @Excel(name = "库存总成本")
    private BigDecimal totalCost;

    private Long version;

    private BigDecimal safetyStockMin;

    @Excel(name = "最后入库时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastInTime;

    @Excel(name = "最后出库时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastOutTime;

    private String stockScope;

    private String stockStatus;

    private Long selectedWarehouseId;

    private Boolean ownOnly;

    private Boolean transferSource;

    public Long getStockId() { return stockId; }
    public void setStockId(Long stockId) { this.stockId = stockId; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
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
    public String getItemCategoryName() { return itemCategoryName; }
    public void setItemCategoryName(String itemCategoryName) { this.itemCategoryName = itemCategoryName; }
    public String getItemCategoryFullPath() { return itemCategoryFullPath; }
    public void setItemCategoryFullPath(String itemCategoryFullPath) { this.itemCategoryFullPath = itemCategoryFullPath; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getItemUnit() { return itemUnit; }
    public void setItemUnit(String itemUnit) { this.itemUnit = itemUnit; }
    public String getItemSpec() { return itemSpec; }
    public void setItemSpec(String itemSpec) { this.itemSpec = itemSpec; }
    public String getItemGrade() { return itemGrade; }
    public void setItemGrade(String itemGrade) { this.itemGrade = itemGrade; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPackageImageUrl() { return packageImageUrl; }
    public void setPackageImageUrl(String packageImageUrl) { this.packageImageUrl = packageImageUrl; }
    public String getDryTeaImageUrl() { return dryTeaImageUrl; }
    public void setDryTeaImageUrl(String dryTeaImageUrl) { this.dryTeaImageUrl = dryTeaImageUrl; }
    public String getTeaSoupImageUrl() { return teaSoupImageUrl; }
    public void setTeaSoupImageUrl(String teaSoupImageUrl) { this.teaSoupImageUrl = teaSoupImageUrl; }
    public String getLeafBottomImageUrl() { return leafBottomImageUrl; }
    public void setLeafBottomImageUrl(String leafBottomImageUrl) { this.leafBottomImageUrl = leafBottomImageUrl; }
    public String getExtraImageUrl() { return extraImageUrl; }
    public void setExtraImageUrl(String extraImageUrl) { this.extraImageUrl = extraImageUrl; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String shopDeptName) { this.shopDeptName = shopDeptName; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal currentQuantity) { this.currentQuantity = currentQuantity; }
    public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public void setLockedQuantity(BigDecimal lockedQuantity) { this.lockedQuantity = lockedQuantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getReferenceCostPrice() { return referenceCostPrice; }
    public void setReferenceCostPrice(BigDecimal referenceCostPrice) { this.referenceCostPrice = referenceCostPrice; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public BigDecimal getSafetyStockMin() { return safetyStockMin; }
    public void setSafetyStockMin(BigDecimal safetyStockMin) { this.safetyStockMin = safetyStockMin; }
    public Date getLastInTime() { return lastInTime; }
    public void setLastInTime(Date lastInTime) { this.lastInTime = lastInTime; }
    public Date getLastOutTime() { return lastOutTime; }
    public void setLastOutTime(Date lastOutTime) { this.lastOutTime = lastOutTime; }
    public String getStockScope() { return stockScope; }
    public void setStockScope(String stockScope) { this.stockScope = stockScope; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public Long getSelectedWarehouseId() { return selectedWarehouseId; }
    public void setSelectedWarehouseId(Long selectedWarehouseId) { this.selectedWarehouseId = selectedWarehouseId; }
    public Boolean getOwnOnly() { return ownOnly; }
    public void setOwnOnly(Boolean ownOnly) { this.ownOnly = ownOnly; }
    public Boolean getTransferSource() { return transferSource; }
    public void setTransferSource(Boolean transferSource) { this.transferSource = transferSource; }
}
