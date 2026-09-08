package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public class OaSignOnboardGenerateResult
{
    private Integer totalCount = 0;
    private Integer generatedCount = 0;
    private Integer reusedCount = 0;
    private Integer blockedCount = 0;
    private Integer failedCount = 0;
    private List<Item> items = new ArrayList<>();

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer value) { totalCount = value; }
    public Integer getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(Integer value) { generatedCount = value; }
    public Integer getReusedCount() { return reusedCount; }
    public void setReusedCount(Integer value) { reusedCount = value; }
    public Integer getBlockedCount() { return blockedCount; }
    public void setBlockedCount(Integer value) { blockedCount = value; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer value) { failedCount = value; }
    public List<Item> getItems() { return new ArrayList<>(items); }
    public void setItems(List<Item> value) { items = value == null ? new ArrayList<>() : new ArrayList<>(value); }

    public static class Item
    {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long rowId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long packageId;
        private String result;
        private String status;
        private String message;

        public Long getRowId() { return rowId; }
        public void setRowId(Long value) { rowId = value; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long value) { employeeId = value; }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long value) { taskId = value; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long value) { packageId = value; }
        public String getResult() { return result; }
        public void setResult(String value) { result = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getMessage() { return message; }
        public void setMessage(String value) { message = value; }
    }
}
