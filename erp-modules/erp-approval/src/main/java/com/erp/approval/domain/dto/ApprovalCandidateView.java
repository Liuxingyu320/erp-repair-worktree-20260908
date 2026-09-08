package com.erp.approval.domain.dto;

public record ApprovalCandidateView(Long userId, String userName, Long deptId,
        String deptName, Integer postSort, String sourceType,
        String sourceCode, String reason)
{
}
