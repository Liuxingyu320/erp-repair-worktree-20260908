package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.system.domain.HrOnboarding;
import com.erp.system.domain.vo.HrEmployeeCompletenessVo;
import com.erp.system.domain.vo.HrEmployeeListVo;
import com.erp.system.domain.vo.HrEmployeeQuery;
import com.erp.system.domain.vo.HrOnboardingCompletionVo;
import com.erp.system.service.IHrEmployeeProfileService;
import com.github.pagehelper.Page;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HrCompletenessServiceTest
{
    @Test
    void enrichesOneServerPageWithOneBatchOnboardingQueryAndRealLinks()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        HrEmployeeQuery query=new HrEmployeeQuery();query.setAccountConfigurationStatus("MISSING");

        Page<HrEmployeeListVo> page=new Page<>(2,10);page.setTotal(21);
        HrEmployeeListVo employee=new HrEmployeeListVo();employee.setUserId(7L);employee.setEmployeeNo("E007");
        employee.setEmployeeName("安全员工");employee.setPhoneNumberMasked("138****0000");
        employee.setProfileCompletionPercent(82);employee.setAccountConfigurationStatus("MISSING");page.add(employee);
        employee.setProfileCompletedFieldCount(41);
        employee.setProfileApplicableFieldCount(59);
        employee.setProfileNotApplicableFieldCount(8);
        employee.setProfileTrackedFieldCount(67);
        employee.setMissingProfileFields(List.of("bankAccount"));
        employee.setAccountEnabled(true);
        when(employees.completenessEmployees(query)).thenReturn(page);

        HrOnboarding onboarding=new HrOnboarding();onboarding.setOnboardingId(19L);onboarding.setLinkedUserId(7L);
        onboarding.setTargetPostId(3L);onboarding.setEmployeeCategory("FULL_TIME");
        onboarding.setStatus(HrOnboarding.STATUS_CONFIRMED);
        employee.setAccountConfigurationRiskCodes(List.of("ROLE_CONFIGURATION_MISSING"));
        when(onboardingAccess.listScopedByLinkedUserIds(any(),org.mockito.ArgumentMatchers.eq(List.of(7L))))
                .thenReturn(List.of(onboarding));
        HrOnboardingCompletionVo completion=new HrOnboardingCompletionVo();completion.setPercentage(76);
        completion.setPostEntryDueDate(new Date(1783872000000L));completion.setPostEntryOverdue(true);
        completion.getMissingFields().add("bankName");
        completion.getGroupedMissingFields().put("合同社保",
                List.of(new HrOnboardingCompletionVo.MissingField("bankName","开户银行")));
        when(rules.evaluateReadyBatch(List.of(onboarding))).thenReturn(java.util.Map.of(19L,completion));

        List<HrEmployeeCompletenessVo> result=service.employees(query);

        assertThat(result).isInstanceOf(Page.class);
        assertThat(((Page<?>)result).getTotal()).isEqualTo(21);
        assertThat(result).hasSize(1);
        HrEmployeeCompletenessVo row=result.get(0);
        assertThat(row.getUserId()).isEqualTo(7L);
        assertThat(row.getPhoneNumberMasked()).isEqualTo("138****0000");
        assertThat(row.getProfileCompletedFieldCount()).isEqualTo(41);
        assertThat(row.getProfileApplicableFieldCount()).isEqualTo(59);
        assertThat(row.getProfileNotApplicableFieldCount()).isEqualTo(8);
        assertThat(row.getProfileTrackedFieldCount()).isEqualTo(67);
        assertThat(row.getMissingProfileFields()).containsExactly("bankAccount");
        assertThat(row.getAccountEnabled()).isTrue();
        assertThat(row.getOnboardingId()).isEqualTo(19L);
        assertThat(row.getOnboardingStatus()).isEqualTo("CONFIRMED");
        assertThat(row.getOnboardingCompletionPercent()).isEqualTo(76);
        assertThat(row.getPostEntryDueDate()).isEqualTo(completion.getPostEntryDueDate());
        assertThat(row.getPostEntryOverdue()).isTrue();
        assertThat(row.getMissingOnboardingFields()).extracting("key").containsExactly("bankName");
        assertThat(row.getAccountConfigurationRiskCodes()).containsExactly("ROLE_CONFIGURATION_MISSING");
        assertThat(row.getEmployeeDetailUrl()).isEqualTo("/hr/employee?userId=7");
        assertThat(row.getOnboardingDetailUrl()).isEqualTo("/hr/onboarding?onboardingId=19");
        assertThat(row.getPositionConfigurationUrl())
                .isEqualTo("/hr/position-config?postId=3&employeeCategory=FULL_TIME");
        verify(onboardingAccess,times(1)).listScopedByLinkedUserIds(any(),any());
    }

    @Test
    void employeesWithoutLinkedOnboardingRemainSafeAndDoNotInventLinks()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        HrEmployeeListVo employee=new HrEmployeeListVo();employee.setUserId(8L);employee.setEmployeeName("未关联员工");
        when(employees.completenessEmployees(any())).thenReturn(List.of(employee));
        when(onboardingAccess.listScopedByLinkedUserIds(any(),org.mockito.ArgumentMatchers.eq(List.of(8L))))
                .thenReturn(List.of());

        HrEmployeeCompletenessVo row=service.employees(new HrEmployeeQuery()).get(0);

        assertThat(row.getOnboardingId()).isNull();
        assertThat(row.getOnboardingDetailUrl()).isNull();
        assertThat(row.getPositionConfigurationUrl()).isNull();
        assertThat(row.getEmployeeDetailUrl()).isEqualTo("/hr/employee?userId=8");
        verify(rules,times(0)).evaluateReadyBatch(any());
    }

    @Test
    void slicesCompletenessFilteredRowsBeforeScopedBatchEnrichment()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        List<HrEmployeeListVo> filtered=new ArrayList<>();
        for(long id=1;id<=25;id++){HrEmployeeListVo row=new HrEmployeeListVo();row.setUserId(id);filtered.add(row);}
        when(employees.completenessEmployees(any())).thenReturn(filtered);
        when(onboardingAccess.listScopedByLinkedUserIds(any(),any())).thenReturn(List.of());
        MockHttpServletRequest request=new MockHttpServletRequest();request.setParameter("pageNum","2");
        request.setParameter("pageSize","10");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            List<HrEmployeeCompletenessVo> result=service.employees(new HrEmployeeQuery());
            assertThat(result).isInstanceOf(Page.class).hasSize(10);
            assertThat(((Page<?>)result).getTotal()).isEqualTo(25);
            assertThat(result).extracting(HrEmployeeCompletenessVo::getUserId)
                    .containsExactly(11L,12L,13L,14L,15L,16L,17L,18L,19L,20L);
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<Long>> ids=org.mockito.ArgumentCaptor.forClass(List.class);
            verify(onboardingAccess).listScopedByLinkedUserIds(any(),ids.capture());
            assertThat(ids.getValue()).containsExactly(11L,12L,13L,14L,15L,16L,17L,18L,19L,20L);
        }
        finally{RequestContextHolder.resetRequestAttributes();}
    }

    @Test
    void persistedAccountConfigurationStatusOverridesLegacyHeuristics()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        HrEmployeeListVo configuredEmployee=new HrEmployeeListVo();configuredEmployee.setUserId(1L);
        configuredEmployee.setAccountConfigurationStatus("COMPLETE");
        configuredEmployee.setAccountConfigurationRiskCodes(List.of());
        HrEmployeeListVo missingEmployee=new HrEmployeeListVo();missingEmployee.setUserId(2L);
        missingEmployee.setAccountConfigurationStatus("MISSING");
        missingEmployee.setAccountConfigurationRiskCodes(List.of("ACCOUNT_CONFIGURATION_MISSING"));
        when(employees.completenessEmployees(any())).thenReturn(List.of(configuredEmployee,missingEmployee));
        HrOnboarding configured=new HrOnboarding();configured.setOnboardingId(11L);configured.setLinkedUserId(1L);
        configured.setAccountConfigurationStatus("CONFIGURED");
        HrOnboarding missing=new HrOnboarding();missing.setOnboardingId(12L);missing.setLinkedUserId(2L);
        missing.setAccountConfigurationStatus("MISSING");
        when(onboardingAccess.listScopedByLinkedUserIds(any(),any())).thenReturn(List.of(configured,missing));
        when(rules.evaluateReadyBatch(any())).thenReturn(new LinkedHashMap<>());

        List<HrEmployeeCompletenessVo> result=service.employees(new HrEmployeeQuery());

        assertThat(result.get(0).getAccountConfigurationStatus()).isEqualTo("COMPLETE");
        assertThat(result.get(0).getAccountConfigurationRiskCodes()).isEmpty();
        assertThat(result.get(1).getAccountConfigurationStatus()).isEqualTo("MISSING");
        assertThat(result.get(1).getAccountConfigurationRiskCodes()).containsExactly("ACCOUNT_CONFIGURATION_MISSING");
    }

    @Test
    void scopedOlderOnboardingMetadataCannotOverwriteAuthoritativeEmployeeAccountState()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        HrEmployeeListVo complete=new HrEmployeeListVo();complete.setUserId(1L);
        complete.setAccountConfigurationStatus("COMPLETE");complete.setAccountConfigurationRiskCodes(List.of());
        HrEmployeeListVo missing=new HrEmployeeListVo();missing.setUserId(2L);
        missing.setAccountConfigurationStatus("MISSING");
        missing.setAccountConfigurationRiskCodes(List.of("ACCOUNT_CONFIGURATION_MISSING"));
        when(employees.completenessEmployees(any())).thenReturn(List.of(complete,missing));
        HrOnboarding olderMissing=new HrOnboarding();olderMissing.setOnboardingId(11L);olderMissing.setLinkedUserId(1L);
        olderMissing.setAccountConfigurationStatus("MISSING");
        HrOnboarding olderConfigured=new HrOnboarding();olderConfigured.setOnboardingId(12L);olderConfigured.setLinkedUserId(2L);
        olderConfigured.setAccountConfigurationStatus("CONFIGURED");
        when(onboardingAccess.listScopedByLinkedUserIds(any(),any())).thenReturn(List.of(olderMissing,olderConfigured));
        when(rules.evaluateReadyBatch(any())).thenReturn(new LinkedHashMap<>());

        List<HrEmployeeCompletenessVo> result=service.employees(new HrEmployeeQuery());

        assertThat(result.get(0).getAccountConfigurationStatus()).isEqualTo("COMPLETE");
        assertThat(result.get(0).getAccountConfigurationRiskCodes()).isEmpty();
        assertThat(result.get(1).getAccountConfigurationStatus()).isEqualTo("MISSING");
        assertThat(result.get(1).getAccountConfigurationRiskCodes()).containsExactly("ACCOUNT_CONFIGURATION_MISSING");
    }

    @Test
    void scopedOnboardingLookupUsesOneCallForTwoHundredPageRows()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,mock(HrOnboardingRuleService.class));
        Page<HrEmployeeListVo> page=employeePage(200);when(employees.completenessEmployees(any())).thenReturn(page);
        when(onboardingAccess.listScopedByLinkedUserIds(any(),any())).thenReturn(List.of());

        List<HrEmployeeCompletenessVo> result=service.employees(new HrEmployeeQuery());

        assertThat(result).hasSize(200);
        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<List<Long>> ids=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(onboardingAccess,times(1)).listScopedByLinkedUserIds(any(),ids.capture());
        assertThat(ids.getValue()).hasSize(200);
    }

    @Test
    void scopedOnboardingLookupChunksTwoHundredAndOnePageRows()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrOnboardingRuleService rules=mock(HrOnboardingRuleService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,rules);
        when(employees.completenessEmployees(any())).thenReturn(employeePage(201));
        when(onboardingAccess.listScopedByLinkedUserIds(any(),any())).thenAnswer(invocation -> {
            List<Long> batch=invocation.getArgument(1);
            HrOnboarding row=new HrOnboarding();row.setOnboardingId(batch.get(0));row.setLinkedUserId(batch.get(0));
            return List.of(row);
        });
        when(rules.evaluateReadyBatch(any())).thenReturn(java.util.Map.of());

        service.employees(new HrEmployeeQuery());

        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<List<Long>> ids=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(onboardingAccess,times(2)).listScopedByLinkedUserIds(any(),ids.capture());
        assertThat(ids.getAllValues()).extracting(List::size).containsExactly(200,1);
        @SuppressWarnings("unchecked") org.mockito.ArgumentCaptor<List<HrOnboarding>> linked=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rules,times(1)).evaluateReadyBatch(linked.capture());
        assertThat(linked.getValue()).hasSize(2);
    }

    @Test
    void hugeRequestedPageReturnsEmptyPageWithoutIntegerOverflow()
    {
        IHrEmployeeProfileService employees=mock(IHrEmployeeProfileService.class);
        HrOnboardingAccessService onboardingAccess=mock(HrOnboardingAccessService.class);
        HrCompletenessService service=new HrCompletenessService(employees,onboardingAccess,mock(HrOnboardingRuleService.class));
        List<HrEmployeeListVo> rows=new ArrayList<>();for(long id=1;id<=25;id++){HrEmployeeListVo row=new HrEmployeeListVo();row.setUserId(id);rows.add(row);}
        when(employees.completenessEmployees(any())).thenReturn(rows);
        MockHttpServletRequest request=new MockHttpServletRequest();request.setParameter("pageNum",String.valueOf(Integer.MAX_VALUE));
        request.setParameter("pageSize",String.valueOf(Integer.MAX_VALUE));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            List<HrEmployeeCompletenessVo> result=service.employees(new HrEmployeeQuery());
            assertThat(result).isInstanceOf(Page.class).isEmpty();assertThat(((Page<?>)result).getTotal()).isEqualTo(25);
            verify(onboardingAccess,times(0)).listScopedByLinkedUserIds(any(),any());
        }
        finally{RequestContextHolder.resetRequestAttributes();}
    }

    private Page<HrEmployeeListVo> employeePage(int size)
    {
        Page<HrEmployeeListVo> page=new Page<>(1,size);page.setTotal(size);
        for(long id=1;id<=size;id++){HrEmployeeListVo row=new HrEmployeeListVo();row.setUserId(id);page.add(row);}
        return page;
    }
}
