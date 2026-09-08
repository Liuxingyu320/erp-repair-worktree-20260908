package com.erp.approval.domain.vo;

import java.util.List;

/**
 * Database-side visibility rule for one approval business.
 *
 * <p>Base candidates include organization leaders, posts, fixed users and
 * candidates whose source code is the base permission. Special candidates
 * (for example reimbursement finance) are visible only while the user still
 * holds that exact live permission.</p>
 */
public class ApprovalTodoAccessRule
{
    private final String businessCode;
    private final boolean basePermissionAllowed;
    private final List<String> specialCandidatePermissions;
    private final List<String> allowedSpecialCandidatePermissions;

    public ApprovalTodoAccessRule(String businessCode,
            boolean basePermissionAllowed,
            List<String> specialCandidatePermissions,
            List<String> allowedSpecialCandidatePermissions)
    {
        this.businessCode = businessCode;
        this.basePermissionAllowed = basePermissionAllowed;
        this.specialCandidatePermissions = List.copyOf(
                specialCandidatePermissions);
        this.allowedSpecialCandidatePermissions = List.copyOf(
                allowedSpecialCandidatePermissions);
    }

    public String getBusinessCode()
    {
        return businessCode;
    }

    public boolean isBasePermissionAllowed()
    {
        return basePermissionAllowed;
    }

    public List<String> getSpecialCandidatePermissions()
    {
        return specialCandidatePermissions;
    }

    public List<String> getAllowedSpecialCandidatePermissions()
    {
        return allowedSpecialCandidatePermissions;
    }
}
