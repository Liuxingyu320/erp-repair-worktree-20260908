package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Executes explicit company-and-seal decisions with per-item isolation. */
public class OaSignTaskBatchFinalizeRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @Valid
    @NotEmpty(message = "请选择要处理的签约任务")
    @Size(max = 20, message = "单次最多处理20个签约任务")
    private List<OaSignTaskBatchFinalizeAction> items = new ArrayList<>();

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<OaSignTaskBatchFinalizeAction> getItems() { return items; }
    public void setItems(List<OaSignTaskBatchFinalizeAction> items)
    {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
