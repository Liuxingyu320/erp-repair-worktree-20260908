package com.erp.approval.domain.dto;

import java.util.List;

public record ApprovalRoutePreview(Long versionId, Long anchorDeptId,
        Integer skipThroughOrder, boolean valid, List<ApprovalNodePreview> nodes,
        List<String> warnings, List<String> errors)
{
    public ApprovalRoutePreview
    {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
