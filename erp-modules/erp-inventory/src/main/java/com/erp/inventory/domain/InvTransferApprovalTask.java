package com.erp.inventory.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

public class InvTransferApprovalTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long instanceId;
    private Long transferId;
    private Integer nodeOrder;
    private String nodeName;
    private Long postId;
    private String postCode;
    private String postName;
    private String candidateUserIds;
    private String candidateUserNames;
    private String status;
    private Long approverId;
    private String approverName;
    private Date approveTime;
    private String comment;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Integer getNodeOrder() { return nodeOrder; }
    public void setNodeOrder(Integer nodeOrder) { this.nodeOrder = nodeOrder; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(String candidateUserIds) { this.candidateUserIds = candidateUserIds; }
    public String getCandidateUserNames() { return candidateUserNames; }
    public void setCandidateUserNames(String candidateUserNames) { this.candidateUserNames = candidateUserNames; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getApproverName() { return approverName; }
    public void setApproverName(String approverName) { this.approverName = approverName; }
    public Date getApproveTime() { return approveTime; }
    public void setApproveTime(Date approveTime) { this.approveTime = approveTime; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
