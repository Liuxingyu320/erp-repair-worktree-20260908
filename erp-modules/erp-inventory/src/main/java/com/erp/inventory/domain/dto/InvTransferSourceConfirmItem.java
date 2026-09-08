package com.erp.inventory.domain.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class InvTransferSourceConfirmItem
{
    @NotNull(message = "调拨明细不能为空")
    private Long detailId;

    @NotNull(message = "确认数量不能为空")
    @DecimalMin(value = "0", message = "确认数量不能小于0")
    private BigDecimal confirmedQuantity;

    public Long getDetailId()
    {
        return detailId;
    }

    public void setDetailId(Long detailId)
    {
        this.detailId = detailId;
    }

    public BigDecimal getConfirmedQuantity()
    {
        return confirmedQuantity;
    }

    public void setConfirmedQuantity(BigDecimal confirmedQuantity)
    {
        this.confirmedQuantity = confirmedQuantity;
    }
}
