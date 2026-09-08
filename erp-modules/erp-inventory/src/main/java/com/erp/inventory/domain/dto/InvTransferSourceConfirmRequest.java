package com.erp.inventory.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class InvTransferSourceConfirmRequest
{
    @Valid
    @NotEmpty(message = "请逐行填写调出店确认数量")
    private List<InvTransferSourceConfirmItem> items = new ArrayList<>();

    @Size(max = 500, message = "确认说明不能超过500个字符")
    private String remark;

    public List<InvTransferSourceConfirmItem> getItems()
    {
        return items;
    }

    public void setItems(List<InvTransferSourceConfirmItem> items)
    {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
