package com.erp.oa.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class OaFixedAssetRepairBatchItem
{
    @NotNull(message = "OE器皿不能为空")
    private Long oeItemId;

    @NotNull(message = "坏掉数量不能为空")
    @DecimalMin(value = "0.01", message = "坏掉数量必须大于0")
    private BigDecimal repairQuantity;

    public Long getOeItemId()
    {
        return oeItemId;
    }

    public void setOeItemId(Long oeItemId)
    {
        this.oeItemId = oeItemId;
    }

    public BigDecimal getRepairQuantity()
    {
        return repairQuantity;
    }

    public void setRepairQuantity(BigDecimal repairQuantity)
    {
        this.repairQuantity = repairQuantity;
    }
}
