package com.erp.oa.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 单个签约任务的硬删除结果。 */
public class OaSignTaskBatchDeleteItem
{
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    private String taskNo;
    private String result;
    private String code;
    private String message;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
