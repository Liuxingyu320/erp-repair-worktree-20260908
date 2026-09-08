package com.erp.system.domain.vo;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class LegacyCredentialRotationPreviewVo
{
    private final String batchId;
    private final String candidateDigest;
    private final Date expiresAt;
    private final List<LegacyCredentialRotationCandidateVo> candidates;

    public LegacyCredentialRotationPreviewVo(String batchId, String candidateDigest,
            Date expiresAt, List<LegacyCredentialRotationCandidateVo> candidates)
    {
        this.batchId = batchId;
        this.candidateDigest = candidateDigest;
        this.expiresAt = expiresAt == null ? null : new Date(expiresAt.getTime());
        this.candidates = Collections.unmodifiableList(candidates);
    }

    public String getBatchId() { return batchId; }
    public String getCandidateDigest() { return candidateDigest; }
    public Date getExpiresAt() { return expiresAt == null ? null : new Date(expiresAt.getTime()); }
    public List<LegacyCredentialRotationCandidateVo> getCandidates() { return candidates; }
}

