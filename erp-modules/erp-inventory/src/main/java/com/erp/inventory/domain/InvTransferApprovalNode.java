package com.erp.inventory.domain;

import com.erp.common.core.web.domain.BaseEntity;

public class InvTransferApprovalNode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long nodeId;

    private Long ruleId;

    private Integer nodeOrder;
    private String nodeName;
    private String nodeRole;
    private Long postId;
    private String postCode;
    private String postName;
    private String approvalMode;
    private Integer requiredCount;
    private Integer resolvedPostSort;

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Integer getNodeOrder() { return nodeOrder; }
    public void setNodeOrder(Integer nodeOrder) { this.nodeOrder = nodeOrder; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getNodeRole() { return nodeRole; }
    public void setNodeRole(String nodeRole) { this.nodeRole = nodeRole; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getPostCode() { return postCode; }
    public void setPostCode(String postCode) { this.postCode = postCode; }
    public String getPostName() { return postName; }
    public void setPostName(String postName) { this.postName = postName; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
    public Integer getRequiredCount() { return requiredCount; }
    public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }
    public Integer getResolvedPostSort() { return resolvedPostSort; }
    public void setResolvedPostSort(Integer resolvedPostSort) { this.resolvedPostSort = resolvedPostSort; }
}
