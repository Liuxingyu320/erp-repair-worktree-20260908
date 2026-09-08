package com.erp.inventory.domain;

import java.util.ArrayList;
import java.util.List;
import com.erp.common.core.web.domain.BaseEntity;

public class InvTransferApprovalInstance extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private Long transferId;
    private Long ruleId;
    private String ruleName;
    private String ruleSnapshot;
    private String status;
    private Integer currentNodeOrder;
    private String approvalMode;
    private Integer requiredCount;
    private String rejectAction;
    private String allowSelfApprove;
    private List<String> approvalWarnings = new ArrayList<>();

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(String ruleSnapshot) { this.ruleSnapshot = ruleSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentNodeOrder() { return currentNodeOrder; }
    public void setCurrentNodeOrder(Integer currentNodeOrder) { this.currentNodeOrder = currentNodeOrder; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
    public Integer getRequiredCount() { return requiredCount; }
    public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }
    public String getRejectAction() { return rejectAction; }
    public void setRejectAction(String rejectAction) { this.rejectAction = rejectAction; }
    public String getAllowSelfApprove() { return allowSelfApprove; }
    public void setAllowSelfApprove(String allowSelfApprove) { this.allowSelfApprove = allowSelfApprove; }
    public List<String> getApprovalWarnings() { return approvalWarnings; }
    public void setApprovalWarnings(List<String> approvalWarnings) {
        this.approvalWarnings = approvalWarnings == null ? new ArrayList<>() : new ArrayList<>(approvalWarnings);
    }
}
