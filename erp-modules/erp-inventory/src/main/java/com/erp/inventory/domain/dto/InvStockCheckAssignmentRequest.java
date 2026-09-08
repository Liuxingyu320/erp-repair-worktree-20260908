package com.erp.inventory.domain.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public class InvStockCheckAssignmentRequest
{
    @NotNull(message = "请选择盘点人")
    private Long counterUserId;

    @NotNull(message = "请选择盘点截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deadline;

    public Long getCounterUserId()
    {
        return counterUserId;
    }

    public void setCounterUserId(Long counterUserId)
    {
        this.counterUserId = counterUserId;
    }

    public Date getDeadline()
    {
        return deadline;
    }

    public void setDeadline(Date deadline)
    {
        this.deadline = deadline;
    }
}
