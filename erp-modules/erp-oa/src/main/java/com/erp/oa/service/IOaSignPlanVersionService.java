package com.erp.oa.service;

import com.erp.oa.domain.OaSignPlanVersion;

public interface IOaSignPlanVersionService
{
    OaSignPlanVersion publish(Long planId, Long selectedShopDeptId);

    OaSignPlanVersion disableForNewMatching(Long versionId, Long selectedShopDeptId);

    void deleteUnreferencedVersion(Long versionId, Long selectedShopDeptId);
}
