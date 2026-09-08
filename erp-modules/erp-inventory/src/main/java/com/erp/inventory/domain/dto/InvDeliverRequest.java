package com.erp.inventory.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class InvDeliverRequest
{
    @NotEmpty(message = "发货明细不能为空")
    @Valid
    private List<InvDeliverItem> items;

    private Long warehouseId;

    public List<InvDeliverItem> getItems() { return items; }
    public void setItems(List<InvDeliverItem> items) { this.items = items; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
}
