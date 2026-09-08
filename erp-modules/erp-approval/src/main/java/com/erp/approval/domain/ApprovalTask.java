package com.erp.approval.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** Executable node task for one approval instance. */
public class ApprovalTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long instanceId;
    private Long nodeId;
    private Integer nodeOrder;
    private String nodeCode;
    private String nodeName;
    private String taskStatus;
    private String approvalMode;
    private Integer requiredCount;
    private Integer completedCount;
    private Date activatedTime;
    private Date completedTime;
    private Long lockVersion;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Integer getNodeOrder() { return nodeOrder; }
    public void setNodeOrder(Integer nodeOrder) { this.nodeOrder = nodeOrder; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
    public Integer getRequiredCount() { return requiredCount; }
    public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public Date getActivatedTime() { return activatedTime; }
    public void setActivatedTime(Date activatedTime) { this.activatedTime = activatedTime; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
