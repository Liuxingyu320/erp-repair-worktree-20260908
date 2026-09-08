package com.erp.oa.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class OaFixedAssetConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;

    @NotNull(message = "店铺不能为空")
    private Long shopDeptId;

    @Excel(name = "店铺")
    private String shopDeptName;

    @NotNull(message = "OE器皿不能为空")
    private Long oeItemId;

    @Excel(name = "OE编码")
    private String oeItemCode;

    @Excel(name = "OE器皿")
    private String oeItemName;

    private String itemDescription;

    private String orderUnit;

    private String imageUrl;

    private String purchaseReferenceUrl;

    private String purchaseReferenceNote;

    @Excel(name = "数量")
    private BigDecimal assetQuantity;

    @Excel(name = "资产单价")
    private BigDecimal assetUnitPrice;

    @Excel(name = "资产金额")
    private BigDecimal assetAmount;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    private BigDecimal annualRepairRatio;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public Long getShopDeptId()
    {
        return shopDeptId;
    }

    public void setShopDeptId(Long shopDeptId)
    {
        this.shopDeptId = shopDeptId;
    }

    public String getShopDeptName()
    {
        return shopDeptName;
    }

    public void setShopDeptName(String shopDeptName)
    {
        this.shopDeptName = shopDeptName;
    }

    public Long getOeItemId()
    {
        return oeItemId;
    }

    public void setOeItemId(Long oeItemId)
    {
        this.oeItemId = oeItemId;
    }

    public String getOeItemCode()
    {
        return oeItemCode;
    }

    public void setOeItemCode(String oeItemCode)
    {
        this.oeItemCode = oeItemCode;
    }

    public String getOeItemName()
    {
        return oeItemName;
    }

    public void setOeItemName(String oeItemName)
    {
        this.oeItemName = oeItemName;
    }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public String getOrderUnit() { return orderUnit; }
    public void setOrderUnit(String orderUnit) { this.orderUnit = orderUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getPurchaseReferenceUrl() { return purchaseReferenceUrl; }
    public void setPurchaseReferenceUrl(String purchaseReferenceUrl) { this.purchaseReferenceUrl = purchaseReferenceUrl; }
    public String getPurchaseReferenceNote() { return purchaseReferenceNote; }
    public void setPurchaseReferenceNote(String purchaseReferenceNote) { this.purchaseReferenceNote = purchaseReferenceNote; }

    public BigDecimal getAssetQuantity()
    {
        return assetQuantity;
    }

    public void setAssetQuantity(BigDecimal assetQuantity)
    {
        this.assetQuantity = assetQuantity;
    }

    public BigDecimal getAssetUnitPrice()
    {
        return assetUnitPrice;
    }

    public void setAssetUnitPrice(BigDecimal assetUnitPrice)
    {
        this.assetUnitPrice = assetUnitPrice;
    }

    public BigDecimal getAssetAmount()
    {
        return assetAmount;
    }

    public void setAssetAmount(BigDecimal assetAmount)
    {
        this.assetAmount = assetAmount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public BigDecimal getAnnualRepairRatio()
    {
        return annualRepairRatio;
    }

    public void setAnnualRepairRatio(BigDecimal annualRepairRatio)
    {
        this.annualRepairRatio = annualRepairRatio;
    }
}
