package com.erp.approval.service;

import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleVersion;

public record MatchedApprovalRule(ApprovalRule rule,
        ApprovalRuleVersion version, int precision)
{
}
