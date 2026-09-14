package com.erp.oa.domain.vo;

import java.util.Date;
import java.util.List;

/** Business-facing preview. The source fingerprint is held on the server only. */
public record OaSignPlanPublishPreview(String previewToken, Long planId, String action,
        Long targetVersionId, Integer targetVersionNo, Long restoreVersionId,
        List<ActiveVersion> activeVersions, String message, Date expiresAt)
{
    public record ActiveVersion(Long versionId, Integer versionNo) {}
}
