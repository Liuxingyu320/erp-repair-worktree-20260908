package com.erp.oa.domain;

import java.math.BigDecimal;
import com.erp.common.core.web.domain.BaseEntity;

public class OaFixedAssetQuotaLedger extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ledgerId;
    private Long repairId;
    private Long shopDeptId;
    private Integer quotaYear;
    private String movementType;
    private BigDecimal amount;

    public Long getLedgerId()
    {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId)
    {
        this.ledgerId = ledgerId;
    }

    public Long getRepairId()
    {
        return repairId;
    }

    public void setRepairId(Long repairId)
    {
        this.repairId = repairId;
    }

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

    public String getMovementType()
    {
        return movementType;
    }

    public void setMovementType(String movementType)
    {
        this.movementType = movementType;
    }

    public BigDecimal getAmount()
    {
        return amount;
    }

    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }
}
