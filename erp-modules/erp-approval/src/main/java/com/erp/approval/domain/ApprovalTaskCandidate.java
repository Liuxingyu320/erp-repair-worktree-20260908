package com.erp.approval.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/** Immutable approver identity snapshot with mutable execution status. */
public class ApprovalTaskCandidate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long candidateId;
    private Long taskId;
    private Long instanceId;
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private String candidateSourceType;
    private String candidateSourceCode;
    private Integer candidateOrder;
    private String candidateStatus;
    private String candidateReason;
    private Date actedTime;
    private Long reassignedFromCandidateId;
    private Long lockVersion;

    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getCandidateSourceType() { return candidateSourceType; }
    public void setCandidateSourceType(String candidateSourceType) { this.candidateSourceType = candidateSourceType; }
    public String getCandidateSourceCode() { return candidateSourceCode; }
    public void setCandidateSourceCode(String candidateSourceCode) { this.candidateSourceCode = candidateSourceCode; }
    public Integer getCandidateOrder() { return candidateOrder; }
    public void setCandidateOrder(Integer candidateOrder) { this.candidateOrder = candidateOrder; }
    public String getCandidateStatus() { return candidateStatus; }
    public void setCandidateStatus(String candidateStatus) { this.candidateStatus = candidateStatus; }
    public String getCandidateReason() { return candidateReason; }
    public void setCandidateReason(String candidateReason) { this.candidateReason = candidateReason; }
    public Date getActedTime() { return actedTime; }
    public void setActedTime(Date actedTime) { this.actedTime = actedTime; }
    public Long getReassignedFromCandidateId() { return reassignedFromCandidateId; }
    public void setReassignedFromCandidateId(Long reassignedFromCandidateId) { this.reassignedFromCandidateId = reassignedFromCandidateId; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
