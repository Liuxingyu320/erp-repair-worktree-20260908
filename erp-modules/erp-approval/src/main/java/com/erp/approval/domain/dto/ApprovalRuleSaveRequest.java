package com.erp.approval.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Editable rule selector. Published definitions are edited through drafts. */
public class ApprovalRuleSaveRequest
{
    @NotNull
    private Long templateId;
    @NotBlank
    private String ruleCode;
    @NotBlank
    private String ruleName;
    @NotBlank
    private String scopeType;
    private Long scopeId;
    private String scopeName;
    private String businessSubtype;
    private Long expectedLockVersion;
    private String remark;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }
    public String getScopeName() { return scopeName; }
    public void setScopeName(String scopeName) { this.scopeName = scopeName; }
    public String getBusinessSubtype() { return businessSubtype; }
    public void setBusinessSubtype(String businessSubtype) { this.businessSubtype = businessSubtype; }
    public Long getExpectedLockVersion() { return expectedLockVersion; }
    public void setExpectedLockVersion(Long expectedLockVersion) { this.expectedLockVersion = expectedLockVersion; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
