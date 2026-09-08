package com.erp.oa.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** One version-bound task in an administrator hard-delete command. */
public class OaSignTaskBatchDeleteAction
{
    @NotNull(message = "任务编号不能为空")
    @Positive(message = "任务编号无效")
    private Long taskId;

    @NotNull(message = "任务版本不能为空")
    @PositiveOrZero(message = "任务版本无效")
    private Long expectedVersion;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
}
