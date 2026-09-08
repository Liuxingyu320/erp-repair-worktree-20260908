package com.erp.inventory.domain;

import java.math.BigDecimal;
import java.util.List;
import com.erp.common.core.annotation.Excel;
import com.erp.common.core.web.domain.BaseEntity;

public class InvTransferApprovalRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ruleId;

    @Excel(name = "规则名称")
    private String ruleName;

    private String documentType;
    private String transferType;
    private String scopeType;
    private Long scopeId;
    private String conditionType;
    private String conditionOperator;
    private BigDecimal conditionValue;
    private String approvalMode;
    private Integer requiredCount;
    private String rejectAction;
    private String allowSelfApprove;
    private Integer priority;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    /** 乐观锁版本；编辑和删除必须携带详情接口返回的当前值。 */
    private Integer version;

    private List<InvTransferApprovalNode> nodes;

    /** 保存前已由操作者确认非阻断警告，仅用于请求，不落库。 */
    private Boolean warningAcknowledged;

    /** 保存前候选人校验使用的目标门店，仅用于请求，不落库。 */
    private Long validationTargetDeptId;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public String getConditionOperator() { return conditionOperator; }
    public void setConditionOperator(String conditionOperator) { this.conditionOperator = conditionOperator; }
    public BigDecimal getConditionValue() { return conditionValue; }
    public void setConditionValue(BigDecimal conditionValue) { this.conditionValue = conditionValue; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }
    public Integer getRequiredCount() { return requiredCount; }
    public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }
    public String getRejectAction() { return rejectAction; }
    public void setRejectAction(String rejectAction) { this.rejectAction = rejectAction; }
    public String getAllowSelfApprove() { return allowSelfApprove; }
    public void setAllowSelfApprove(String allowSelfApprove) { this.allowSelfApprove = allowSelfApprove; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<InvTransferApprovalNode> getNodes() { return nodes; }
    public void setNodes(List<InvTransferApprovalNode> nodes) { this.nodes = nodes; }
    public Boolean getWarningAcknowledged() { return warningAcknowledged; }
    public void setWarningAcknowledged(Boolean warningAcknowledged) { this.warningAcknowledged = warningAcknowledged; }
    public Long getValidationTargetDeptId() { return validationTargetDeptId; }
    public void setValidationTargetDeptId(Long validationTargetDeptId) { this.validationTargetDeptId = validationTargetDeptId; }
}
