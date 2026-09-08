package com.erp.approval.api.domain;

import java.io.Serializable;
import java.util.Date;

public class LegacyApprovalTaskSummary implements Serializable
{
    private static final long serialVersionUID = 1L;
    private Long taskId;
    private Integer nodeOrder;
    private String nodeName;
    private String candidateNames;
    private String status;
    private String action;
    private Long operatorUserId;
    private String operatorName;
    private String comment;
    private Date actionTime;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public Integer getNodeOrder() { return nodeOrder; }
    public void setNodeOrder(Integer value) { nodeOrder = value; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String value) { nodeName = value; }
    public String getCandidateNames() { return candidateNames; }
    public void setCandidateNames(String value) { candidateNames = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long value) { operatorUserId = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { comment = value; }
    public Date getActionTime() { return actionTime; }
    public void setActionTime(Date value) { actionTime = value; }
}
