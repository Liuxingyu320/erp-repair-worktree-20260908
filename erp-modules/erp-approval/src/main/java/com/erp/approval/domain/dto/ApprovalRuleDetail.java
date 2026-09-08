package com.erp.approval.domain.dto;

import java.util.ArrayList;
import java.util.List;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;

public class ApprovalRuleDetail
{
    private ApprovalTemplate template;
    private ApprovalRule rule;
    private List<ApprovalRuleVersion> versions = new ArrayList<>();

    public ApprovalTemplate getTemplate() { return template; }
    public void setTemplate(ApprovalTemplate template) { this.template = template; }
    public ApprovalRule getRule() { return rule; }
    public void setRule(ApprovalRule rule) { this.rule = rule; }
    public List<ApprovalRuleVersion> getVersions() { return versions; }
    public void setVersions(List<ApprovalRuleVersion> value) { versions = value == null ? new ArrayList<>() : value; }
}
