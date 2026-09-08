package com.erp.oa.domain.vo;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** Preflight or execution result with one isolated outcome per onboarding row. */
public class OaSignOnboardCompanyWorkResult
{
    private Integer totalCount = 0;
    private Integer readyCount = 0;
    private Integer succeededCount = 0;
    private Integer blockedCount = 0;
    private Integer failedCount = 0;
    private List<Item> items = new ArrayList<>();

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer value) { totalCount = value; }
    public Integer getReadyCount() { return readyCount; }
    public void setReadyCount(Integer value) { readyCount = value; }
    public Integer getSucceededCount() { return succeededCount; }
    public void setSucceededCount(Integer value) { succeededCount = value; }
    public Integer getBlockedCount() { return blockedCount; }
    public void setBlockedCount(Integer value) { blockedCount = value; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer value) { failedCount = value; }
    public List<Item> getItems() { return new ArrayList<>(items); }
    public void setItems(List<Item> value)
    {
        items = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public static class Item
    {
        private String sourceType = "ONBOARD_IMPORT_ROW";
        @JsonSerialize(using = ToStringSerializer.class)
        private Long batchId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long rowId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String employeeName;
        private String result;
        private String status;
        private String message;
        private List<String> blockers = new ArrayList<>();
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long packageId;

        public String getSourceType() { return sourceType; }
        public void setSourceType(String value) { sourceType = value; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long value) { batchId = value; }
        public Long getRowId() { return rowId; }
        public void setRowId(Long value) { rowId = value; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long value) { employeeId = value; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String value) { employeeName = value; }
        public String getResult() { return result; }
        public void setResult(String value) { result = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getMessage() { return message; }
        public void setMessage(String value) { message = value; }
        public List<String> getBlockers() { return new ArrayList<>(blockers); }
        public void setBlockers(List<String> value)
        {
            blockers = value == null ? new ArrayList<>() : new ArrayList<>(value);
        }
        public Long getTaskId() { return taskId; }
        public void setTaskId(Long value) { taskId = value; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long value) { packageId = value; }
    }
}
