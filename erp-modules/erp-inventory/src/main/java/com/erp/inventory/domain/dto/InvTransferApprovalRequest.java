package com.erp.inventory.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InvTransferApprovalRequest
{
    @NotNull(message = "调拨单ID不能为空")
    @Positive(message = "调拨单ID必须为正整数")
    private Long transferId;

    @Positive(message = "审批任务ID必须为正整数")
    private Long taskId;

    @NotNull(message = "审批动作不能为空")
    @Pattern(regexp = "approve|reject",
            message = "审批动作必须为approve或reject")
    private String action;

    @Size(max = 500, message = "审批意见不能超过500个字符")
    private String comment;

    public Long getTransferId()
    {
        return transferId;
    }

    public void setTransferId(Long transferId)
    {
        this.transferId = transferId;
    }

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }
}
