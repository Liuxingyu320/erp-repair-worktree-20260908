package com.erp.oa.domain.vo;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class OaTodoItem
{
    private String todoKey;
    private String source;
    private String type;
    private String category;
    private Long businessId;
    private String businessNo;
    private String title;
    private String summary;
    private String status;
    private String priority;
    private Date createdTime;
    private Long waitingSeconds;
    private Long deptId;
    private String deptName;
    private String scopeMode;
    private String routeType;
    private Map<String, Object> routeParams = new LinkedHashMap<>();
    private String requiredPermission;

    public String getTodoKey() { return todoKey; }
    public void setTodoKey(String todoKey) { this.todoKey = todoKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public Long getWaitingSeconds() { return waitingSeconds; }
    public void setWaitingSeconds(Long waitingSeconds) { this.waitingSeconds = waitingSeconds; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getScopeMode() { return scopeMode; }
    public void setScopeMode(String scopeMode) { this.scopeMode = scopeMode; }
    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }
    public Map<String, Object> getRouteParams() { return routeParams; }
    public void setRouteParams(Map<String, Object> routeParams)
    {
        this.routeParams = routeParams == null ? new LinkedHashMap<>() : routeParams;
    }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
}
