package com.erp.approval.domain.dto;

import java.util.List;
import com.erp.approval.domain.ApprovalValidationIssue;
import com.erp.approval.domain.ApprovalValidationRun;

public record ApprovalValidationDetail(ApprovalValidationRun run,
        List<ApprovalValidationIssue> issues)
{
    public ApprovalValidationDetail
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
