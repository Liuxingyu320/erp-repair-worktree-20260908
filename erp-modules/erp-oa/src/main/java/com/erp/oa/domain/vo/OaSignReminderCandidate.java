package com.erp.oa.domain.vo;

import java.util.Date;

/**
 * One reminder occurrence derived from an immutable signing-plan policy.
 */
public class OaSignReminderCandidate
{
    private Long packageId;
    private Long taskId;
    private Long employeeId;
    private Long assignedHrUserId;
    private Long shopDeptId;
    private String packageStatus;
    private String reminderStage;
    private String activeDocumentVersion;
    private Date signDeadline;
    private Integer reminderBeforeHours;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getAssignedHrUserId() { return assignedHrUserId; }
    public void setAssignedHrUserId(Long assignedHrUserId) { this.assignedHrUserId = assignedHrUserId; }
    public Long getShopDeptId() { return shopDeptId; }
    public void setShopDeptId(Long shopDeptId) { this.shopDeptId = shopDeptId; }
    public String getPackageStatus() { return packageStatus; }
    public void setPackageStatus(String packageStatus) { this.packageStatus = packageStatus; }
    public String getReminderStage() { return reminderStage; }
    public void setReminderStage(String reminderStage) { this.reminderStage = reminderStage; }
    public String getActiveDocumentVersion() { return activeDocumentVersion; }
    public void setActiveDocumentVersion(String activeDocumentVersion) { this.activeDocumentVersion = activeDocumentVersion; }
    public Date getSignDeadline() { return signDeadline; }
    public void setSignDeadline(Date signDeadline) { this.signDeadline = signDeadline; }
    public Integer getReminderBeforeHours() { return reminderBeforeHours; }
    public void setReminderBeforeHours(Integer reminderBeforeHours) { this.reminderBeforeHours = reminderBeforeHours; }
}
