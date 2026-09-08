package com.erp.oa.service.rule;

import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.oa.domain.vo.OaSignDraftDecision;

/** One independently testable rule for exactly one HR signing scenario. */
public interface OaSignScenarioRule
{
    boolean supports(String scenario);

    String dedupeKey(HrSignBusinessEvent event);

    OaSignDraftDecision decide(HrSignBusinessEvent event);
}
