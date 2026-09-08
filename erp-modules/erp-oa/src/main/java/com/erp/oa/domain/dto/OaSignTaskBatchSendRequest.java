package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 任务中心逐项批量发送请求。 */
public class OaSignTaskBatchSendRequest
{
    @NotBlank(message = "请求编号不能为空")
    @Size(max = 64, message = "请求编号不能超过64个字符")
    private String requestId;

    @NotEmpty(message = "任务编号不能为空")
    @Size(max = 100, message = "单次最多发送100个任务")
    private List<@NotNull(message = "任务编号不能为空") @Positive(message = "任务编号无效") Long> taskIds =
            new ArrayList<>();

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public List<Long> getTaskIds()
    {
        return taskIds;
    }

    public void setTaskIds(List<Long> taskIds)
    {
        this.taskIds = taskIds == null ? new ArrayList<>() : new ArrayList<>(taskIds);
    }
}
