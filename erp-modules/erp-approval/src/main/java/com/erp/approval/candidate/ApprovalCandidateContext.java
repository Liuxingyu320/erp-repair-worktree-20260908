package com.erp.approval.candidate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.erp.approval.domain.ApprovalRule;
import com.erp.approval.domain.ApprovalRuleVersion;
import com.erp.approval.domain.ApprovalTemplate;
import com.erp.approval.domain.ApprovalVersionNode;

public record ApprovalCandidateContext(ApprovalTemplate template,
        ApprovalRule rule, ApprovalRuleVersion version, ApprovalVersionNode node,
        Long anchorDeptId, Long applicantId, String businessSubtype,
        Map<String, Object> variables)
{
    public ApprovalCandidateContext
    {
        variables = variables == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
