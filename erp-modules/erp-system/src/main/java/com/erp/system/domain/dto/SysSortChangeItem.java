package com.erp.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/** One optimistic sort change shared by department and menu ordering. */
public class SysSortChangeItem
{
    public static final int MAX_ORDER_NUM = 999999;

    @NotNull(message = "排序对象ID不能为空")
    @Min(value = 1, message = "排序对象ID必须为正整数")
    private Long id;

    @NotNull(message = "原排序值不能为空")
    @Min(value = 0, message = "原排序值不能小于0")
    @Max(value = MAX_ORDER_NUM, message = "原排序值不能超过999999")
    private Integer expectedOrderNum;

    @NotNull(message = "新排序值不能为空")
    @Min(value = 0, message = "新排序值不能小于0")
    @Max(value = MAX_ORDER_NUM, message = "新排序值不能超过999999")
    private Integer newOrderNum;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Integer getExpectedOrderNum()
    {
        return expectedOrderNum;
    }

    public void setExpectedOrderNum(Integer expectedOrderNum)
    {
        this.expectedOrderNum = expectedOrderNum;
    }

    public Integer getNewOrderNum()
    {
        return newOrderNum;
    }

    public void setNewOrderNum(Integer newOrderNum)
    {
        this.newOrderNum = newOrderNum;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored)
    {
        throw new IllegalArgumentException("排序项不接受字段: " + field);
    }
}
