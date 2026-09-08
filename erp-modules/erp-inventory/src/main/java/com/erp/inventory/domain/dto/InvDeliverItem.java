package com.erp.inventory.domain.dto;

import java.math.BigDecimal;

public class InvDeliverItem
{
    private Long detailId;
    private BigDecimal deliverQuantity;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public BigDecimal getDeliverQuantity() { return deliverQuantity; }
    public void setDeliverQuantity(BigDecimal deliverQuantity) { this.deliverQuantity = deliverQuantity; }
}
