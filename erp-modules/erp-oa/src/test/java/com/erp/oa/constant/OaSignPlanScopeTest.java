package com.erp.oa.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OaSignPlanScopeTest
{
    @Test
    void globalPlanAppliesToRealOrganization()
    {
        assertThat(OaSignPlanScope.appliesTo(0L, 1171L)).isTrue();
    }

    @Test
    void globalPlanDoesNotApplyToReservedOrMissingOrganization()
    {
        assertThat(OaSignPlanScope.appliesTo(0L, 0L)).isFalse();
        assertThat(OaSignPlanScope.appliesTo(0L, null)).isFalse();
    }

    @Test
    void legacyPlanOnlyAppliesToItsRealOrganization()
    {
        assertThat(OaSignPlanScope.appliesTo(1171L, 1171L)).isTrue();
        assertThat(OaSignPlanScope.appliesTo(1171L, 2201L)).isFalse();
    }
}
