package com.erp.oa.mapper;

import org.apache.ibatis.annotations.Param;
import com.erp.oa.domain.OaSignOnboardSendRequest;

/** Persistence boundary for HR onboarding-data-request send idempotency. */
public interface OaSignOnboardSendRequestMapper
{
    /**
     * Inserts a new claim or locks/touches the existing request row. The duplicate branch
     * deliberately mutates replay_count so a following read sees the current committed row
     * even under MySQL REPEATABLE READ.
     */
    int claim(OaSignOnboardSendRequest request);

    OaSignOnboardSendRequest selectByRequestId(@Param("requestId") String requestId);
}
