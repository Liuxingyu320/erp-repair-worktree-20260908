package com.erp.approval.candidate;

public record ResolvedApprovalCandidate(Long userId, String userName,
        Long deptId, String deptName, Integer postSort, String sourceType,
        String sourceCode, String reason)
{
}
