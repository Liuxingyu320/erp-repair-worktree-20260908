package com.erp.approval.domain.dto;

import java.util.List;

public record ApprovalNodePreview(Long nodeId, Integer nodeOrder,
        String nodeCode, String nodeName, List<ApprovalCandidateView> candidates,
        boolean skipped, boolean blocking, String reason)
{
    public ApprovalNodePreview
    {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
