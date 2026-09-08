package com.erp.system.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.exception.HrOnboardingValidationException;
import com.erp.system.mapper.SysUserMapper;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.ISysConfigService;

class HrEmployeeNoGeneratorTest
{
    private final ISysConfigService config = mock(ISysConfigService.class);
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SysUserProfileMapper profiles = mock(SysUserProfileMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneId.of("UTC"));

    @Test
    void usesConfiguredPrefixAndFiveDigitGlobalSequence()
    {
        when(config.selectConfigByKey("hr.employee.no.prefix")).thenReturn("HR");
        when(profiles.selectEmployeeNoSequenceForUpdate("GLOBAL")).thenReturn(0L);
        when(profiles.advanceEmployeeNoSequence("GLOBAL", 0L, 1L)).thenReturn(1);

        assertThat(generator().generate(42L, Date.from(Instant.parse("2027-01-02T00:00:00Z"))))
                .isEqualTo("HR00001");
    }

    @Test
    void consumesCollidingNumbersAndReturnsNextAvailableSequence()
    {
        when(config.selectConfigByKey("hr.employee.no.prefix")).thenReturn("E");
        when(profiles.selectEmployeeNoSequenceForUpdate("GLOBAL")).thenReturn(0L, 1L, 2L);
        when(profiles.advanceEmployeeNoSequence(eq("GLOBAL"), anyLong(), anyLong())).thenReturn(1);
        when(users.selectUserByUserName("E00001")).thenReturn(new SysUser());
        when(profiles.selectUserProfileByEmployeeNo("E00002")).thenReturn(new SysUserProfile());

        assertThat(generator().generate(42L, Date.from(Instant.parse("2026-07-11T00:00:00Z"))))
                .isEqualTo("E00003");
    }

    @Test
    void continuesAfterTheHierarchyResequenceWithoutRenumberingExistingEmployees()
    {
        when(config.selectConfigByKey("hr.employee.no.prefix")).thenReturn("E");
        when(profiles.selectEmployeeNoSequenceForUpdate("GLOBAL")).thenReturn(154L);
        when(profiles.advanceEmployeeNoSequence("GLOBAL", 154L, 155L)).thenReturn(1);

        assertThat(generator().generate(43L, new Date()))
                .isEqualTo("E00155");
    }

    @Test
    void reportsStableErrorWhenFiveDigitSequenceIsExhausted()
    {
        when(config.selectConfigByKey("hr.employee.no.prefix")).thenReturn("E");
        when(profiles.selectEmployeeNoSequenceForUpdate("GLOBAL")).thenReturn(99999L);

        assertThatThrownBy(() -> generator().generate(42L, new Date()))
                .isInstanceOf(HrOnboardingValidationException.class)
                .extracting("errorCode").isEqualTo("EMPLOYEE_NO_EXHAUSTED");
    }

    private HrEmployeeNoGenerator generator()
    {
        return new HrEmployeeNoGenerator(config, users, profiles, clock);
    }
}
