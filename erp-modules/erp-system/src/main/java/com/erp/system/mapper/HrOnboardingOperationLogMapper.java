package com.erp.system.mapper;

import java.util.List;
import com.erp.system.domain.HrOnboardingOperationLog;

/**
 * HR入职单操作日志数据层。
 */
public interface HrOnboardingOperationLogMapper
{
    int insertOperationLog(HrOnboardingOperationLog operationLog);

    List<HrOnboardingOperationLog> selectByOnboardingId(Long onboardingId);
}
