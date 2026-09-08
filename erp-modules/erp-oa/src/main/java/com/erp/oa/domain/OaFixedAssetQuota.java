package com.erp.oa.domain;

import java.math.BigDecimal;
import com.erp.common.core.web.domain.BaseEntity;

public class OaFixedAssetQuota extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long quotaId;
    private Long shopDeptId;
    private String shopDeptName;
    private Integer quotaYear;
    private BigDecimal annualRepairRatio;
    private BigDecimal assetTotalAmount;
    private BigDecimal annualQuotaAmount;
    private BigDecimal monthlyQuotaAmount;

    public Long getQuotaId()
    {
        return quotaId;
    }

    public void setQuotaId(Long quotaId)
    {
        this.quotaId = quotaId;
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

    public Integer getQuotaYear()
    {
        return quotaYear;
    }

    public void setQuotaYear(Integer quotaYear)
    {
        this.quotaYear = quotaYear;
    }

    public BigDecimal getAnnualRepairRatio()
    {
        return annualRepairRatio;
    }

    public void setAnnualRepairRatio(BigDecimal annualRepairRatio)
    {
        this.annualRepairRatio = annualRepairRatio;
    }

    public BigDecimal getAssetTotalAmount()
    {
        return assetTotalAmount;
    }

    public void setAssetTotalAmount(BigDecimal assetTotalAmount)
    {
        this.assetTotalAmount = assetTotalAmount;
    }

    public BigDecimal getAnnualQuotaAmount()
    {
        return annualQuotaAmount;
    }

    public void setAnnualQuotaAmount(BigDecimal annualQuotaAmount)
    {
        this.annualQuotaAmount = annualQuotaAmount;
    }

    public BigDecimal getMonthlyQuotaAmount()
    {
        return monthlyQuotaAmount;
    }

    public void setMonthlyQuotaAmount(BigDecimal monthlyQuotaAmount)
    {
        this.monthlyQuotaAmount = monthlyQuotaAmount;
    }
}
