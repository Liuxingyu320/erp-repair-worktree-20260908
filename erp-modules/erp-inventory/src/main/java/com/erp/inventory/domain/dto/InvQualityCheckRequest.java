package com.erp.inventory.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class InvQualityCheckRequest
{
    @NotNull(message = "收货批次不能为空")
    private Long receiptBatchId;

    @NotEmpty(message = "质检明细不能为空")
    @Valid
    private List<InvQualityCheckItem> items;

    public Long getReceiptBatchId() { return receiptBatchId; }
    public void setReceiptBatchId(Long receiptBatchId) { this.receiptBatchId = receiptBatchId; }
    public List<InvQualityCheckItem> getItems() { return items; }
    public void setItems(List<InvQualityCheckItem> items) { this.items = items; }
}
