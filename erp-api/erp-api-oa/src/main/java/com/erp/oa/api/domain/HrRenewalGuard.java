package com.erp.oa.api.domain;

import java.io.Serializable;

/** 共享数据库中的员工续签任务权威门闩。 */
public class HrRenewalGuard implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long employeeId;
    private String scenario;
    private String status;
    private Long actionId;
    private Long taskId;
    private Long version;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
