package com.erp.system.support;

import com.erp.system.domain.vo.HrEmployeeQuery;

/**
 * Server-owned population contract for HR employee queries.
 * Archive access keeps departed employees for historical records, while active governance excludes them.
 */
public final class HrEmployeePopulationPolicy
{
    public static final long BUILT_IN_ADMIN_USER_ID = 1L;
    public static final String ACTIVE_GOVERNANCE_PARAM = "_hrActiveGovernanceOnly";

    private HrEmployeePopulationPolicy() { }

    public static HrEmployeeQuery applyArchiveAccess(HrEmployeeQuery query)
    {
        return apply(query,false);
    }

    public static HrEmployeeQuery applyActiveGovernance(HrEmployeeQuery query)
    {
        return apply(query,true);
    }

    private static HrEmployeeQuery apply(HrEmployeeQuery query,boolean activeGovernanceOnly)
    {
        HrEmployeeQuery safe=query==null?new HrEmployeeQuery():query;
        safe.getParams().put(ACTIVE_GOVERNANCE_PARAM,activeGovernanceOnly);
        return safe;
    }
}
