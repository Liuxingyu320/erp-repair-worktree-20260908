package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.common.core.constant.UserConstants;
import org.junit.jupiter.api.Test;

class HrEmployeePopulationPolicyTest
{
    @Test
    void serviceOwnedPopulationModeOverwritesRequestParameters()
    {
        HrEmployeeQuery query=new HrEmployeeQuery();
        query.getParams().put(HrEmployeePopulationPolicy.ACTIVE_GOVERNANCE_PARAM,false);

        HrEmployeePopulationPolicy.applyActiveGovernance(query);
        assertThat(query.getParams()).containsEntry(HrEmployeePopulationPolicy.ACTIVE_GOVERNANCE_PARAM,true);

        HrEmployeePopulationPolicy.applyArchiveAccess(query);
        assertThat(query.getParams()).containsEntry(HrEmployeePopulationPolicy.ACTIVE_GOVERNANCE_PARAM,false);
        assertThat(HrEmployeePopulationPolicy.BUILT_IN_ADMIN_USER_ID).isEqualTo(1L);
        assertThat(UserConstants.isAdmin(HrEmployeePopulationPolicy.BUILT_IN_ADMIN_USER_ID)).isTrue();
    }
}
