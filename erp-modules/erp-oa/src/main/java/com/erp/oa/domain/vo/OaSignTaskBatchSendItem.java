package com.erp.oa.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 单个签约任务的批量发送结果。 */
public class OaSignTaskBatchSendItem
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;
    private String result;
    private String taskStatus;
    private String message;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
