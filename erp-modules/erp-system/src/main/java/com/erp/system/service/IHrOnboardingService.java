package com.erp.system.service;

import java.util.List;
import java.util.Map;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.HrOnboardingImportRow;
import com.erp.system.domain.vo.*;

public interface IHrOnboardingService
{
    List<HrOnboardingListVo> list(HrOnboardingQuery query);
    HrOnboardingDetailVo get(Long onboardingId);
    HrOnboardingDetailVo create(HrOnboardingCreateRequest input, String operator);
    HrOnboardingDetailVo update(Long onboardingId, HrOnboardingUpdateRequest input, String operator);
    HrOnboardingDetailVo markReady(Long onboardingId, Integer version, String operator);
    HrOnboardingDetailVo returnToDraft(Long onboardingId, Integer version, String operator);
    HrOnboardingDetailVo cancel(Long onboardingId, Integer version, String reason, String operator);
    HrOnboardingDetailVo restore(Long onboardingId, Integer version, String operator);
    HrOnboardingSummaryVo summary(HrOnboardingQuery query);
    Map<String, Object> formOptions();
    Map<String, Object> formOptions(boolean includeOwners, boolean includeSupervisors);
    List<HrOnboardingOwnerOptionVo> ownerOptions(HrOnboardingOwnerQuery query,
            int pageNum, int pageSize);
    HrOnboarding createImported(HrOnboardingImportRow row, String operator);
}
