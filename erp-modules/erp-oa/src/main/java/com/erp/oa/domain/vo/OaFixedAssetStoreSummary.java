package com.erp.oa.domain.vo;

/** Store aggregates are paged after all matching configuration rows are grouped. */
public class OaFixedAssetStoreSummary
{
    @tools.jackson.databind.annotation.JsonSerialize(using=tools.jackson.databind.ser.std.ToStringSerializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long shopDeptId;
    private String shopDeptName;
    private Long detailCount;
    private java.math.BigDecimal assetTotalQuantity;
    private java.math.BigDecimal assetTotalAmount;
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long value) { shopDeptId = value; }
    public String getShopDeptName() { return shopDeptName; }
    public void setShopDeptName(String value) { shopDeptName = value; }
    public Long getDetailCount() { return detailCount; }
    public void setDetailCount(Long value) { detailCount = value; }
    public java.math.BigDecimal getAssetTotalQuantity() { return assetTotalQuantity; }
    public void setAssetTotalQuantity(java.math.BigDecimal value) { assetTotalQuantity = value; }
    public java.math.BigDecimal getAssetTotalAmount() { return assetTotalAmount; }
    public void setAssetTotalAmount(java.math.BigDecimal value) { assetTotalAmount = value; }
}
