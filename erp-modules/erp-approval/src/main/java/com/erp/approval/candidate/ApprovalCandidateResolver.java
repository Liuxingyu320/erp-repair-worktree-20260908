package com.erp.approval.candidate;

import com.erp.approval.domain.ApprovalVersionNode;

/** Controlled extension point for responsibility, leader, fixed-user and business policies. */
public interface ApprovalCandidateResolver
{
    boolean supports(ApprovalVersionNode node);

    ApprovalCandidateResolution resolve(ApprovalCandidateContext context);
}
