package com.erp.approval.domain.vo;

import java.util.Date;

public class ApprovalTodoRow
{
    private Long taskId;
    private Long instanceId;
    private String businessCode;
    private String businessSource;
    private String businessId;
    private String businessSubtype;
    private String candidateSourceCode;
    private String nodeName;
    private String applicantName;
    private Long anchorDeptId;
    private String anchorDeptName;
    private String anchorDeptType;
    private Date createdTime;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessSource() { return businessSource; }
    public void setBusinessSource(String businessSource) { this.businessSource = businessSource; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getBusinessSubtype() { return businessSubtype; }
    public void setBusinessSubtype(String businessSubtype) { this.businessSubtype = businessSubtype; }
    public String getCandidateSourceCode() { return candidateSourceCode; }
    public void setCandidateSourceCode(String candidateSourceCode) { this.candidateSourceCode = candidateSourceCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public Long getAnchorDeptId() { return anchorDeptId; }
    public void setAnchorDeptId(Long anchorDeptId) { this.anchorDeptId = anchorDeptId; }
    public String getAnchorDeptName() { return anchorDeptName; }
    public void setAnchorDeptName(String anchorDeptName) { this.anchorDeptName = anchorDeptName; }
    public String getAnchorDeptType() { return anchorDeptType; }
    public void setAnchorDeptType(String anchorDeptType) { this.anchorDeptType = anchorDeptType; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
}
