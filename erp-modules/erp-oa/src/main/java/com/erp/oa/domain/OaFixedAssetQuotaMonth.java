package com.erp.oa.domain;

import java.math.BigDecimal;
import com.erp.common.core.web.domain.BaseEntity;

public class OaFixedAssetQuotaMonth extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long monthId;
    private Long shopDeptId;
    private String shopDeptName;
    private Integer quotaYear;
    private Integer quotaMonth;
    private BigDecimal annualRepairRatio;
    private BigDecimal assetTotalAmount;
    private BigDecimal monthlyQuotaAmount;

    public Long getMonthId()
    {
        return monthId;
    }

    public void setMonthId(Long monthId)
    {
        this.monthId = monthId;
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

    public Integer getQuotaMonth()
    {
        return quotaMonth;
    }

    public void setQuotaMonth(Integer quotaMonth)
    {
        this.quotaMonth = quotaMonth;
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

    public BigDecimal getMonthlyQuotaAmount()
    {
        return monthlyQuotaAmount;
    }

    public void setMonthlyQuotaAmount(BigDecimal monthlyQuotaAmount)
    {
        this.monthlyQuotaAmount = monthlyQuotaAmount;
    }
}
