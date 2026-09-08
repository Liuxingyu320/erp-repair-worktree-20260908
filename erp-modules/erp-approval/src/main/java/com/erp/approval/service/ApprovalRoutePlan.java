package com.erp.approval.service;

import java.util.List;
import com.erp.approval.candidate.ResolvedApprovalCandidate;
import com.erp.approval.domain.ApprovalVersionNode;

public record ApprovalRoutePlan(List<PlannedNode> nodes,
        Integer skipThroughOrder, List<String> warnings, List<String> errors)
{
    public ApprovalRoutePlan
    {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() { return errors.isEmpty(); }

    public record PlannedNode(ApprovalVersionNode node,
            List<ResolvedApprovalCandidate> candidates, boolean skipped,
            boolean blocking, String reason)
    {
        public PlannedNode
        {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }
}
