package com.erp.oa.domain.vo;

import java.math.BigDecimal;

public class OaFixedAssetQuotaSummary
{
    private Long shopDeptId;
    private Integer quotaYear;
    private BigDecimal annualRepairRatio;
    private BigDecimal assetTotalAmount;
    private BigDecimal annualQuotaAmount;
    private BigDecimal monthlyQuotaAmount;
    private BigDecimal releasedQuotaAmount;
    private BigDecimal usedQuotaAmount;
    private BigDecimal availableQuotaAmount;
    private BigDecimal futureAdvanceAmount;

    public Long getShopDeptId()
    {
        return shopDeptId;
    }

    public void setShopDeptId(Long shopDeptId)
    {
        this.shopDeptId = shopDeptId;
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

    public BigDecimal getReleasedQuotaAmount()
    {
        return releasedQuotaAmount;
    }

    public void setReleasedQuotaAmount(BigDecimal releasedQuotaAmount)
    {
        this.releasedQuotaAmount = releasedQuotaAmount;
    }

    public BigDecimal getUsedQuotaAmount()
    {
        return usedQuotaAmount;
    }

    public void setUsedQuotaAmount(BigDecimal usedQuotaAmount)
    {
        this.usedQuotaAmount = usedQuotaAmount;
    }

    public BigDecimal getAvailableQuotaAmount()
    {
        return availableQuotaAmount;
    }

    public void setAvailableQuotaAmount(BigDecimal availableQuotaAmount)
    {
        this.availableQuotaAmount = availableQuotaAmount;
    }

    public BigDecimal getFutureAdvanceAmount()
    {
        return futureAdvanceAmount;
    }

    public void setFutureAdvanceAmount(BigDecimal futureAdvanceAmount)
    {
        this.futureAdvanceAmount = futureAdvanceAmount;
    }
}
