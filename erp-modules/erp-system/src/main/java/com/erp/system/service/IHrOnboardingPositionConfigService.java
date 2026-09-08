package com.erp.system.service;

import java.util.List;
import java.util.Map;
import com.erp.system.domain.HrOnboardingPositionConfig;

public interface IHrOnboardingPositionConfigService
{
    List<HrOnboardingPositionConfig> list(HrOnboardingPositionConfig query);

    HrOnboardingPositionConfig get(Long configId);

    HrOnboardingPositionConfig resolveActive(Long postId, String employeeCategory);

    HrOnboardingPositionConfig create(HrOnboardingPositionConfig input, String operator);

    HrOnboardingPositionConfig update(Long configId, HrOnboardingPositionConfig input, String operator);

    void disable(Long configId, Integer version, String operator);

    Map<String, Object> options();

    Map<String, List<Map<String, Object>>> loadDictionaries(List<String> fieldKeys);
}
