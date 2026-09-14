package com.erp.oa.service;

import com.erp.oa.domain.OaSignPlanVersion;

public interface IOaSignPlanVersionService
{
    OaSignPlanVersion publish(Long planId, Long selectedShopDeptId);

    com.erp.oa.domain.vo.OaSignPlanPublishPreview previewPublish(Long planId, Long selectedShopDeptId);

    com.erp.oa.domain.vo.OaSignPlanVersionPublishResult confirmPublish(Long planId,
            com.erp.oa.domain.dto.OaSignPlanPublishRequest request, Long selectedShopDeptId);

    OaSignPlanVersion disableForNewMatching(Long versionId, Long selectedShopDeptId);

    void deleteUnreferencedVersion(Long versionId, Long selectedShopDeptId);
}
