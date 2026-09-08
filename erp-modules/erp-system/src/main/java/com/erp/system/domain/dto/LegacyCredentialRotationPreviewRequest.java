package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;

public class LegacyCredentialRotationPreviewRequest
{
    private List<Long> candidateUserIds = new ArrayList<>();
    private Integer limit;

    public List<Long> getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(List<Long> candidateUserIds) { this.candidateUserIds = candidateUserIds; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}

