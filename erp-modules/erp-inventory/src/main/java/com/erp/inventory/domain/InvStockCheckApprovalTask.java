package com.erp.inventory.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

public class InvStockCheckApprovalTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long instanceId;
    private Long checkId;
    private Integer roundNo;
    private String candidateUserIds;
    private String candidateUserNames;
    private String status;
    private String action;
    private Long approverUserId;
    private String approverName;
    private String approvalComment;
    private Date approvalTime;
    private String selfApproved;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getCheckId() { return checkId; }
    public void setCheckId(Long checkId) { this.checkId = checkId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(String candidateUserIds) { this.candidateUserIds = candidateUserIds; }
    public String getCandidateUserNames() { return candidateUserNames; }
    public void setCandidateUserNames(String candidateUserNames) { this.candidateUserNames = candidateUserNames; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getApproverUserId() { return approverUserId; }
    public void setApproverUserId(Long approverUserId) { this.approverUserId = approverUserId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public String getApprovalComment() { return approvalComment; }
    public void setApprovalComment(String approvalComment) { this.approvalComment = approvalComment; }
    public Date getApprovalTime() { return approvalTime; }
    public void setApprovalTime(Date approvalTime) { this.approvalTime = approvalTime; }
    public String getSelfApproved() { return selfApproved; }
    public void setSelfApproved(String selfApproved) { this.selfApproved = selfApproved; }
}
