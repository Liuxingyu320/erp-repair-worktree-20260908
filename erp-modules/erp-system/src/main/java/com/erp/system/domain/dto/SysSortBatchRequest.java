package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 菜单、部门等系统树的标准差量排序请求。 */
public class SysSortBatchRequest
{
    @Valid
    @NotEmpty(message = "排序项不能为空")
    @Size(max = 500, message = "单次最多保存500个排序项")
    private List<Item> items = new ArrayList<>();

    public List<Item> getItems()
    {
        return items;
    }

    public void setItems(List<Item> items)
    {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public static class Item
    {
        @NotNull(message = "对象ID不能为空")
        @Min(value = 1, message = "对象ID必须大于0")
        private Long id;

        @NotNull(message = "排序值不能为空")
        @Min(value = 0, message = "排序值不能小于0")
        @Max(value = 999999, message = "排序值不能大于999999")
        private Integer orderNum;

        public Item()
        {
        }

        public Item(Long id, Integer orderNum)
        {
            this.id = id;
            this.orderNum = orderNum;
        }

        public Long getId()
        {
            return id;
        }

        public void setId(Long id)
        {
            this.id = id;
        }

        public Integer getOrderNum()
        {
            return orderNum;
        }

        public void setOrderNum(Integer orderNum)
        {
            this.orderNum = orderNum;
        }
    }
}
