package com.erp.system.domain.dto;

import java.util.ArrayList;
import java.util.List;

public class LegacyCredentialRotationExecuteRequest
{
    private String batchId;
    private String candidateDigest;
    private List<Long> candidateUserIds = new ArrayList<>();

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getCandidateDigest() { return candidateDigest; }
    public void setCandidateDigest(String candidateDigest) { this.candidateDigest = candidateDigest; }
    public List<Long> getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(List<Long> candidateUserIds) { this.candidateUserIds = candidateUserIds; }
}

