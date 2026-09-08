package com.erp.approval.domain.dto;

/** Optional source version; defaults to the rule's current published version. */
public class ApprovalDraftCreateRequest
{
    private Long sourceVersionId;
    public Long getSourceVersionId() { return sourceVersionId; }
    public void setSourceVersionId(Long sourceVersionId) { this.sourceVersionId = sourceVersionId; }
}
