package com.erp.inventory.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class InvQualityCheckRequest
{
    @jakarta.validation.constraints.NotBlank(message = "质检操作标识不能为空，请刷新页面后重试")
    @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}", message = "质检操作标识无效")
    private String requestId;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

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
