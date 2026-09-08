package com.erp.inventory.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.annotation.Excel.Type;
import com.erp.common.core.web.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InvProduct extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long productId;

    @Excel(name = "商品名称")
    private String productName;

    @Excel(name = "商品编码", type = Type.EXPORT)
    private String productCode;

    private Long categoryId;

    @Excel(name = "分类名称")
    private String categoryName;

    @Excel(name = "分类路径")
    private String categoryFullPath;

    @Excel(name = "等级")
    private String grade;

    @Excel(name = "SKU")
    private String sku;

    @Excel(name = "规格型号")
    private String spec;

    @Excel(name = "尺寸")
    private String size;

    @Excel(name = "计量单位")
    private String unit;

    @Excel(name = "采购参考价")
    private BigDecimal purchasePrice;

    @Excel(name = "销售参考价")
    private BigDecimal salesPrice;

    @Excel(name = "售价250g")
    private BigDecimal salePrice250g;

    @Excel(name = "售价500g")
    private BigDecimal salePrice500g;

    @Excel(name = "参考成本价")
    private BigDecimal costPrice;

    @Excel(name = "安全库存下限")
    private BigDecimal safetyStockMin;

    @Excel(name = "安全库存上限")
    private BigDecimal safetyStockMax;

    private Boolean clearSalesPrice;

    private Boolean clearSalePrice250g;

    private Boolean clearSalePrice500g;

    private Boolean clearSafetyStockMin;

    private Boolean clearSafetyStockMax;

    @Excel(name = "供应商名称")
    private String supplierName;

    @Excel(name = "供应商电话")
    private String supplierPhone;

    @Excel(name = "供应商备注")
    private String supplierRemark;

    @Excel(name = "内部茶种名称")
    private String internalTeaName;

    @Excel(name = "产品描述")
    private String productDescription;

    @Excel(name = "条码")
    private String barcode;

    private String imageUrl;

    @Excel(name = "外包装图片链接")
    private String packageImageUrl;

    @Excel(name = "干茶图片链接")
    private String dryTeaImageUrl;

    @Excel(name = "茶汤图片链接")
    private String teaSoupImageUrl;

    @Excel(name = "叶底图片链接")
    private String leafBottomImageUrl;

    @Excel(name = "补充图片链接")
    private String extraImageUrl;

    private Long shopDeptId;

    @Excel(name = "所属店铺", type = Type.EXPORT)
    private String shopDeptName;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private String delFlag;

    private String keyword;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    @NotBlank(message = "商品名称不能为空")
    @Size(min = 0, max = 128, message = "商品名称不能超过128个字符")
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    @Size(min = 0, max = 64, message = "商品编码不能超过64个字符")
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryFullPath() { return categoryFullPath; }
    public void setCategoryFullPath(String categoryFullPath) { this.categoryFullPath = categoryFullPath; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getSalesPrice() { return salesPrice; }
    public void setSalesPrice(BigDecimal salesPrice) { this.salesPrice = salesPrice; }
    public BigDecimal getSalePrice250g() { return salePrice250g; }
    public void setSalePrice250g(BigDecimal salePrice250g) { this.salePrice250g = salePrice250g; }
    public BigDecimal getSalePrice500g() { return salePrice500g; }
    public void setSalePrice500g(BigDecimal salePrice500g) { this.salePrice500g = salePrice500g; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getSafetyStockMin() { return safetyStockMin; }
    public void setSafetyStockMin(BigDecimal safetyStockMin) { this.safetyStockMin = safetyStockMin; }
    public BigDecimal getSafetyStockMax() { return safetyStockMax; }
    public void setSafetyStockMax(BigDecimal safetyStockMax) { this.safetyStockMax = safetyStockMax; }
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Boolean getClearSalesPrice() { return clearSalesPrice; }
    public void setClearSalesPrice(Boolean clearSalesPrice) { this.clearSalesPrice = clearSalesPrice; }
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Boolean getClearSalePrice250g() { return clearSalePrice250g; }
    public void setClearSalePrice250g(Boolean clearSalePrice250g) { this.clearSalePrice250g = clearSalePrice250g; }
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Boolean getClearSalePrice500g() { return clearSalePrice500g; }
    public void setClearSalePrice500g(Boolean clearSalePrice500g) { this.clearSalePrice500g = clearSalePrice500g; }
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Boolean getClearSafetyStockMin() { return clearSafetyStockMin; }
    public void setClearSafetyStockMin(Boolean clearSafetyStockMin) { this.clearSafetyStockMin = clearSafetyStockMin; }
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public Boolean getClearSafetyStockMax() { return clearSafetyStockMax; }
    public void setClearSafetyStockMax(Boolean clearSafetyStockMax) { this.clearSafetyStockMax = clearSafetyStockMax; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierPhone() { return supplierPhone; }
    public void setSupplierPhone(String supplierPhone) { this.supplierPhone = supplierPhone; }
    public String getSupplierRemark() { return supplierRemark; }
    public void setSupplierRemark(String supplierRemark) { this.supplierRemark = supplierRemark; }
    public String getInternalTeaName() { return internalTeaName; }
    public void setInternalTeaName(String internalTeaName) { this.internalTeaName = internalTeaName; }
    public String getProductDescription() { return productDescription; }
    public void setProductDescription(String productDescription) { this.productDescription = productDescription; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
