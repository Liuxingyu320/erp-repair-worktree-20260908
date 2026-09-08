package com.erp.approval.domain;

import com.erp.common.core.web.domain.BaseEntity;

/** Scope and subtype selector owned by one approval template. */
public class ApprovalRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ruleId;
    private Long templateId;
    private String ruleCode;
    private String ruleName;
    private String scopeType;
    private Long scopeId;
    private String scopeName;
    private String businessSubtype;
    private String ruleStatus;
    private Long currentVersionId;
    private Integer latestVersionNo;
    private Long lockVersion;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
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
    public String getRuleStatus() { return ruleStatus; }
    public void setRuleStatus(String ruleStatus) { this.ruleStatus = ruleStatus; }
    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }
    public Integer getLatestVersionNo() { return latestVersionNo; }
    public void setLatestVersionNo(Integer latestVersionNo) { this.latestVersionNo = latestVersionNo; }
    public Long getLockVersion() { return lockVersion; }
    public void setLockVersion(Long lockVersion) { this.lockVersion = lockVersion; }
}
