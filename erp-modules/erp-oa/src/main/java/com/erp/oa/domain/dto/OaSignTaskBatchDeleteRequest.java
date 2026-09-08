package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 管理员批量硬删除未完成签约任务请求。 */
public class OaSignTaskBatchDeleteRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotEmpty(message = "请选择要删除的签约任务")
    @Size(max = 20, message = "单次最多删除20个签约任务")
    private List<@Valid OaSignTaskBatchDeleteAction> items = new ArrayList<>();

    @AssertTrue(message = "请确认删除后不可恢复")
    private boolean irreversibleConfirmed;

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public List<OaSignTaskBatchDeleteAction> getItems()
    {
        return items;
    }

    public void setItems(List<OaSignTaskBatchDeleteAction> items)
    {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public boolean isIrreversibleConfirmed()
    {
        return irreversibleConfirmed;
    }

    public void setIrreversibleConfirmed(boolean irreversibleConfirmed)
    {
        this.irreversibleConfirmed = irreversibleConfirmed;
    }
}
