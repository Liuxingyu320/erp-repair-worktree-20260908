package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** One immutable error or warning produced by a validation run. */
public class ApprovalValidationIssue extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long issueId;
    private Long runId;
    private Long templateId;
    private Long ruleId;
    private Long ruleVersionId;
    private String severity;
    private String issueCode;
    private String scopeType;
    private Long scopeId;
    private String scopeName;
    private String businessSubtype;
    private String nodeCode;
    private String nodeName;
    private String issueMessage;
    private String suggestion;
    private String issueSnapshot;

    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getIssueCode() { return issueCode; }
    public void setIssueCode(String issueCode) { this.issueCode = issueCode; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }
    public String getScopeName() { return scopeName; }
    public void setScopeName(String scopeName) { this.scopeName = scopeName; }
    public String getBusinessSubtype() { return businessSubtype; }
    public void setBusinessSubtype(String businessSubtype) { this.businessSubtype = businessSubtype; }
    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getIssueMessage() { return issueMessage; }
    public void setIssueMessage(String issueMessage) { this.issueMessage = issueMessage; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getIssueSnapshot() { return issueSnapshot; }
    public void setIssueSnapshot(String issueSnapshot) { this.issueSnapshot = issueSnapshot; }
}
