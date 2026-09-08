package com.erp.approval.candidate;

import java.util.List;

public record ApprovalCandidateResolution(List<ResolvedApprovalCandidate> candidates,
        List<String> warnings)
{
    public ApprovalCandidateResolution
    {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ApprovalCandidateResolution of(
            List<ResolvedApprovalCandidate> candidates)
    {
        return new ApprovalCandidateResolution(candidates, List.of());
    }
}
