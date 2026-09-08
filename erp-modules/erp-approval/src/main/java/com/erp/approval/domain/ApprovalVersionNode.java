package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Ordered approver-resolution node captured by a rule version. */
public class ApprovalVersionNode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long nodeId;
    private Long versionId;
    private Integer nodeOrder;
    private String nodeCode;
    private String nodeName;
    private String strategyType;
    private String strategyCode;
    private String strategyConfig;
    private String approvalMode;
    private Integer requiredCount;
    private String missingPolicy;
    private String selfPolicy;
    private String returnAllowed;
    private String rejectAllowed;

    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Integer getNodeOrder() { return nodeOrder; }
    public void setNodeOrder(Integer nodeOrder) { this.nodeOrder = nodeOrder; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getStrategyType() { return strategyType; }
    public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
    public String getStrategyCode() { return strategyCode; }
    public void setStrategyCode(String strategyCode) { this.strategyCode = strategyCode; }
    public String getStrategyConfig() { return strategyConfig; }
    public void setStrategyConfig(String strategyConfig) { this.strategyConfig = strategyConfig; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
    public Integer getRequiredCount() { return requiredCount; }
    public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }
    public String getMissingPolicy() { return missingPolicy; }
    public void setMissingPolicy(String missingPolicy) { this.missingPolicy = missingPolicy; }
    public String getSelfPolicy() { return selfPolicy; }
    public void setSelfPolicy(String selfPolicy) { this.selfPolicy = selfPolicy; }
    public String getReturnAllowed() { return returnAllowed; }
    public void setReturnAllowed(String returnAllowed) { this.returnAllowed = returnAllowed; }
    public String getRejectAllowed() { return rejectAllowed; }
    public void setRejectAllowed(String rejectAllowed) { this.rejectAllowed = rejectAllowed; }
}
