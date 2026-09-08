package com.erp.oa.domain.dto;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Preview request for selecting companies and contract seals for existing tasks. */
public class OaSignTaskBatchFinalizePreviewRequest
{
    @NotEmpty(message = "请选择要处理的签约任务")
    @Size(max = 20, message = "单次最多处理20个签约任务")
    private List<@NotNull(message = "任务编号不能为空") @Positive(message = "任务编号无效") Long> taskIds =
            new ArrayList<>();

    public List<Long> getTaskIds()
    {
        return taskIds;
    }

    public void setTaskIds(List<Long> taskIds)
    {
        this.taskIds = taskIds == null ? new ArrayList<>() : new ArrayList<>(taskIds);
    }
}
