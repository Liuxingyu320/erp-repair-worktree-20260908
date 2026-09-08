package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.api.domain.SysUserProfile;
import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrOnboardingConflictVo;
import com.erp.system.domain.vo.HrOnboardingQuery;
import com.erp.system.service.ISysUserService;
import com.erp.system.support.HrSensitiveFieldMasker;

class HrOnboardingConflictServiceTest
{
    @Test
    void importConflictBatchUsesBoundedTwoQueryChunks()
    {
        HrOnboardingAccessService access=mock(HrOnboardingAccessService.class);
        when(access.listScopedUserConflictsBatch(any(),any())).thenReturn(Collections.emptyList());
        when(access.listScopedOnboardingConflictsBatch(any(),any())).thenReturn(Collections.emptyList());
        java.util.List<HrOnboarding> sources=new ArrayList<>();
        for(int i=0;i<201;i++)sources.add(onboarding(null,HrOnboarding.STATUS_DRAFT,"138"+i));

        java.util.Map<HrOnboarding,java.util.List<HrOnboardingConflictVo>> result=
                new HrOnboardingConflictService(access,new HrSensitiveFieldMasker(),mock(ISysUserService.class))
                        .findImportConflictsBatch(sources);

        assertThat(result).hasSize(201);
        @SuppressWarnings("unchecked") ArgumentCaptor<java.util.List<HrOnboarding>> chunks=ArgumentCaptor.forClass(java.util.List.class);
        verify(access,org.mockito.Mockito.times(2)).listScopedUserConflictsBatch(any(),chunks.capture());
        assertThat(chunks.getAllValues()).extracting(java.util.List::size).containsExactly(200,1);
        verify(access,org.mockito.Mockito.times(2)).listScopedOnboardingConflictsBatch(any(),any());
    }
    @Test
    void protectedAdministratorIsNeverEligibleForBindingInPreview()
    {
        HrOnboardingAccessService access = mock(HrOnboardingAccessService.class);
        ISysUserService userService = mock(ISysUserService.class);
        HrOnboarding source = onboarding(42L, HrOnboarding.STATUS_READY, "13800008000");
        when(access.findScoped(any())).thenReturn(source);
        SysUser administrator = new SysUser();
        administrator.setUserId(1L);
        administrator.setStatus("0");
        administrator.setPhonenumber(source.getPhoneNumber());
        doThrow(new ServiceException("不允许操作超级管理员用户"))
                .when(userService).checkUserAllowed(administrator);
        when(access.listScopedUserConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(administrator));
        when(access.listScopedOnboardingConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());

        HrOnboardingConflictVo conflict = new HrOnboardingConflictService(access,
                new HrSensitiveFieldMasker(), userService).preview(42L).get(0);

        assertThat(conflict.getEligibleForBind()).isFalse();
        assertThat(conflict.getAllowedDecisions()).isEmpty();
        assertThat(conflict.getBlocking()).isTrue();
        verify(userService).checkUserAllowed(administrator);
    }

    @Test
    void confirmedHistoricalOnboardingIsAWarningAndAllowsRehire()
    {
        HrOnboardingAccessService access = mock(HrOnboardingAccessService.class);
        ISysUserService userService = mock(ISysUserService.class);
        HrOnboarding source = onboarding(42L, HrOnboarding.STATUS_READY, "13800008000");
        when(access.findScoped(any())).thenReturn(source);
        when(access.listScopedUserConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());
        HrOnboarding historical = onboarding(41L, HrOnboarding.STATUS_CONFIRMED, source.getPhoneNumber());
        when(access.listScopedOnboardingConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(historical));

        HrOnboardingConflictVo conflict = new HrOnboardingConflictService(access,
                new HrSensitiveFieldMasker(), userService).preview(42L).get(0);

        assertThat(conflict.getBlocking()).isFalse();
        assertThat(conflict.getAllowedDecisions()).containsExactly("CREATE_NEW");
    }

    @Test
    void disabledDepartedCandidateBlocksDuplicatesAndAllowsExplicitRehire()
    {
        HrOnboardingConflictVo conflict = accountConflict("1", "离职");

        assertThat(conflict.getBlocking()).isTrue();
        assertThat(conflict.getEligibleForBind()).isFalse();
        assertThat(conflict.getEligibleForRehire()).isTrue();
        assertThat(conflict.getAllowedDecisions()).containsExactly("REHIRE_EXISTING");
    }

    @Test
    void reusedPhoneWithDifferentEmployeeIdentityCannotRehire()
    {
        HrOnboardingAccessService access = mock(HrOnboardingAccessService.class);
        HrOnboarding source = onboarding(42L, HrOnboarding.STATUS_READY, "13800008000");
        source.setIdNumber("110101199001010000");
        when(access.findScoped(any())).thenReturn(source);
        SysUser user = new SysUser();
        user.setUserId(88L);
        user.setNickName("另一位员工");
        user.setPhonenumber(source.getPhoneNumber());
        user.setStatus("1");
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeStatus("离职");
        profile.setIdNumber("110101198001010000");
        user.setProfile(profile);
        when(access.listScopedUserConflicts(any(), eq(source.getPhoneNumber()),
                eq(source.getIdNumber()), eq(null))).thenReturn(Collections.singletonList(user));
        when(access.listScopedOnboardingConflicts(any(), eq(source.getPhoneNumber()),
                eq(source.getIdNumber()), eq(null))).thenReturn(Collections.emptyList());

        HrOnboardingConflictVo conflict = new HrOnboardingConflictService(access,
                new HrSensitiveFieldMasker(), mock(ISysUserService.class)).preview(42L).get(0);

        assertThat(conflict.getBlocking()).isTrue();
        assertThat(conflict.getEligibleForRehire()).isFalse();
        assertThat(conflict.getAllowedDecisions()).isEmpty();
    }

    @Test
    void disabledAccountWithActiveEmployeeStillBlocksButCannotBind()
    {
        HrOnboardingConflictVo conflict = accountConflict("1", "试用");

        assertThat(conflict.getBlocking()).isTrue();
        assertThat(conflict.getEligibleForBind()).isFalse();
        assertThat(conflict.getAllowedDecisions()).isEmpty();
    }

    @Test
    void activeAccountWithDepartedEmployeeStillBlocksButCannotBind()
    {
        HrOnboardingConflictVo conflict = accountConflict("0", "离职");

        assertThat(conflict.getBlocking()).isTrue();
        assertThat(conflict.getEligibleForBind()).isFalse();
        assertThat(conflict.getAllowedDecisions()).isEmpty();
    }

    @Test
    void previewUsesScopedQueriesMasksPhonesAndClassifiesActiveAndCancelledMatches()
    {
        HrOnboardingAccessService access = mock(HrOnboardingAccessService.class);
        HrOnboarding source = onboarding(42L, HrOnboarding.STATUS_READY, "13800008000");
        source.setIdNumber("110101199001010000");
        when(access.findScoped(any())).thenReturn(source);

        SysUser user = new SysUser();
        user.setUserId(88L);
        user.setNickName("候选人");
        user.setPhonenumber("13800008000");
        user.setStatus("0");
        SysDept dept = new SysDept();
        dept.setDeptName("华东区");
        user.setDept(dept);
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeNo("E88");
        profile.setIdNumber("110101199001010000");
        user.setProfile(profile);
        when(access.listScopedUserConflicts(any(), eq(source.getPhoneNumber()), eq(source.getIdNumber()), eq(null)))
                .thenReturn(Collections.singletonList(user));

        HrOnboarding cancelled = onboarding(43L, HrOnboarding.STATUS_CANCELLED, "13800008000");
        when(access.listScopedOnboardingConflicts(any(), eq(source.getPhoneNumber()),
                eq(source.getIdNumber()), eq(null))).thenReturn(Arrays.asList(source, cancelled));

        java.util.List<HrOnboardingConflictVo> result =
                new HrOnboardingConflictService(access, new HrSensitiveFieldMasker(),
                        mock(ISysUserService.class)).preview(42L);

        assertThat(result).extracting(HrOnboardingConflictVo::getSourceType)
                .contains("EMPLOYEE_ACCOUNT", "ONBOARDING");
        assertThat(result).allSatisfy(item -> assertThat(item.getMaskedPhone()).isEqualTo("138****8000"));
        assertThat(result).filteredOn(item -> "EMPLOYEE_ACCOUNT".equals(item.getSourceType()))
                .allSatisfy(item -> {
                    assertThat(item.getCandidateUserId()).isEqualTo(88L);
                    assertThat(item.getEligibleForBind()).isTrue();
                    assertThat(item.getBlocking()).isTrue();
                    assertThat(item.getAllowedDecisions()).containsExactly("BIND_EXISTING");
                });
        assertThat(result).filteredOn(item -> Long.valueOf(43L).equals(item.getCandidateOnboardingId()))
                .allSatisfy(item -> {
                    assertThat(item.getBlocking()).isFalse();
                    assertThat(item.getEligibleForBind()).isFalse();
                    assertThat(item.getAllowedDecisions()).containsExactly("CREATE_NEW");
                });
        ArgumentCaptor<HrOnboardingQuery> scoped = ArgumentCaptor.forClass(HrOnboardingQuery.class);
        verify(access).findScoped(scoped.capture());
        assertThat(scoped.getValue().getOnboardingId()).isEqualTo(42L);
    }

    private HrOnboarding onboarding(Long id, String status, String phone)
    {
        HrOnboarding row = new HrOnboarding();
        row.setOnboardingId(id);
        row.setStatus(status);
        row.setPhoneNumber(phone);
        row.setEmployeeName("候选人");
        return row;
    }

    private HrOnboardingConflictVo accountConflict(String accountStatus, String employeeStatus)
    {
        HrOnboardingAccessService access = mock(HrOnboardingAccessService.class);
        HrOnboarding source = onboarding(42L, HrOnboarding.STATUS_READY, "13800008000");
        when(access.findScoped(any())).thenReturn(source);
        SysUser user = new SysUser();
        user.setUserId(88L);
        user.setNickName(source.getEmployeeName());
        user.setPhonenumber("13800008000");
        user.setStatus(accountStatus);
        SysUserProfile profile = new SysUserProfile();
        profile.setEmployeeStatus(employeeStatus);
        user.setProfile(profile);
        when(access.listScopedUserConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(user));
        when(access.listScopedOnboardingConflicts(any(), eq(source.getPhoneNumber()), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());
        return new HrOnboardingConflictService(access, new HrSensitiveFieldMasker(),
                mock(ISysUserService.class)).preview(42L).get(0);
    }
}
