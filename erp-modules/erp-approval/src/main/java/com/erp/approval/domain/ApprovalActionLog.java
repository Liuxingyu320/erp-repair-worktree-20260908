package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Append-only approval operation record. */
public class ApprovalActionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long actionId;
    private String actionKey;
    private Long instanceId;
    private Long taskId;
    private Long candidateId;
    private String actionType;
    private Long operatorUserId;
    private String operatorName;
    private String operatorSource;
    private String fromStatus;
    private String toStatus;
    private String actionReason;
    private String requestId;
    private String actionSnapshot;

    public Long getActionId() { return actionId; }
    public void setActionId(Long actionId) { this.actionId = actionId; }
    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorSource() { return operatorSource; }
    public void setOperatorSource(String operatorSource) { this.operatorSource = operatorSource; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getActionReason() { return actionReason; }
    public void setActionReason(String actionReason) { this.actionReason = actionReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getActionSnapshot() { return actionSnapshot; }
    public void setActionSnapshot(String actionSnapshot) { this.actionSnapshot = actionSnapshot; }
}
