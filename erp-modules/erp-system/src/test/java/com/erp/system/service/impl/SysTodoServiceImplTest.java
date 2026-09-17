package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.constant.SysTodoTypes;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.vo.SysTodoCandidateRow;
import com.erp.system.domain.vo.SysHealthCertificateTodoCandidate;
import com.erp.system.domain.vo.SysTodoPage;
import com.erp.system.mapper.SysTodoMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserShopService;
import com.erp.system.support.HrEmployeeCompletenessEvaluator;
import com.erp.system.support.HrEmployeeCompletenessSnapshot;

class SysTodoServiceImplTest
{
    private SysTodoMapper mapper;
    private ISysUserShopService shopService;
    private HrEmployeeCompletenessEvaluator evaluator;
    private ISysConfigService configService;
    private TestService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(SysTodoMapper.class);
        shopService = mock(ISysUserShopService.class);
        evaluator = mock(HrEmployeeCompletenessEvaluator.class);
        configService = mock(ISysConfigService.class);
        service = new TestService(mapper, shopService, evaluator, configService);
        when(evaluator.evaluate(any())).thenReturn(new HrEmployeeCompletenessSnapshot(
                98,58,59,8,67,List.of("bankAccount")));
        when(shopService.selectShopDeptIdsByUserId(7L)).thenReturn(List.of(10L, 20L));
        when(mapper.selectScopedTodoCandidates(any(), anySet(), anyList(), anyList(), any()))
                .thenReturn(new ArrayList<>());
        when(mapper.selectHealthCertificateTodoCandidates(any(), anySet(), anyList(), anyList(), any()))
                .thenReturn(new ArrayList<>());
    }

    @Test
    void enablesTypesOnlyWhenEveryReadAndActionPermissionIsPresent()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list"));
        assertThat(service.enabledTypes()).doesNotContain(SysTodoTypes.HR_PROFILE_INCOMPLETE);
        service.permissions.add("hr:employee:edit");
        assertThat(service.enabledTypes()).contains(SysTodoTypes.HR_PROFILE_INCOMPLETE,
                SysTodoTypes.HR_CONTRACT_DUE);

        service.permissions.addAll(Set.of("hr:onboarding:list", "hr:onboarding:confirm"));
        assertThat(service.enabledTypes()).contains(SysTodoTypes.HR_ONBOARDING_CONFIRM);
        assertThat(service.enabledTypes()).doesNotContain(SysTodoTypes.HR_OFFBOARD_ACCOUNT);
        service.permissions.add("system:user:edit");
        assertThat(service.enabledTypes()).contains(SysTodoTypes.HR_OFFBOARD_ACCOUNT);

        service.permissions.addAll(Set.of("hr:healthCertificate:list",
                "hr:healthCertificate:query", "hr:healthCertificate:review"));
        assertThat(service.enabledTypes()).contains(SysTodoTypes.HR_HEALTH_CERT_REVIEW);

        service.permissions.addAll(Set.of("hr:healthCertificate:self:edit",
                "hr:healthCertificate:self:submit"));
        assertThat(service.enabledTypes()).doesNotContain(SysTodoTypes.HR_HEALTH_CERT_RETURNED);
        service.intakeEnabled = true;
        assertThat(service.enabledTypes()).contains(SysTodoTypes.HR_HEALTH_CERT_RETURNED);
    }

    @Test
    void acceptsActionableAndNormalizesLegacyScopesBeforeBuildingTodos()
    {
        for (String supported : new String[] { null, "", " ",
                TodoConstants.SCOPE_ACTIONABLE,
                TodoConstants.SCOPE_CURRENT_ORG,
                TodoConstants.SCOPE_ALL_AUTHORIZED })
        {
            TodoQuery query = new TodoQuery();
            query.setScopeMode(supported);

            service.selectTodoPage(query, 10L);

            assertThat(query.getScopeMode()).isEqualTo(TodoConstants.SCOPE_ACTIONABLE);
        }

        TodoQuery invalid = new TodoQuery();
        invalid.setScopeMode("other_users");
        assertThatThrownBy(() -> service.selectTodoPage(invalid, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的待办组织范围");
        assertThatThrownBy(() -> service.selectSummary(invalid, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的待办组织范围");
    }

    @Test
    void groupsPendingHealthCertificateReviewsAndEscalatesOldestToUrgent()
    {
        service.permissions.addAll(Set.of("hr:healthCertificate:list",
                "hr:healthCertificate:query", "hr:healthCertificate:review"));
        when(configService.selectConfigByKey("todo.approval.urgent.hours")).thenReturn("24");
        SysHealthCertificateTodoCandidate old = healthCandidate(
                101L, 1L, 10L, "门店A", "PENDING_REVIEW");
        old.setCreateTime(new Date(service.now - 25L * 3600_000));
        SysHealthCertificateTodoCandidate recent = healthCandidate(
                102L, 2L, 10L, "门店A", "PENDING_REVIEW");
        recent.setCreateTime(new Date(service.now - 2L * 3600_000));
        when(mapper.selectHealthCertificateTodoCandidates(any(), anySet(), anyList(), anyList(), any()))
                .thenReturn(List.of(old, recent));

        TodoSummary summary = service.selectSummary(new TodoQuery(), 10L);

        assertThat(summary.getTotal()).isEqualTo(1);
        assertThat(summary.getApproval()).isEqualTo(1);
        assertThat(summary.getUrgent()).isEqualTo(1);
        assertThat(summary.getTypeCounts()).containsEntry(SysTodoTypes.HR_HEALTH_CERT_REVIEW, 1L);
        TodoItem item = summary.getRecent().get(0);
        assertThat(item.getTodoKey()).isEqualTo("system:HR_HEALTH_CERT_REVIEW:10:manage");
        assertThat(item.getPriority()).isEqualTo("urgent");
        assertThat(item.getRouteParams()).containsEntry("currentDeptId", "10")
                .containsEntry("reviewStatus", "PENDING_REVIEW")
                .containsEntry("healthCertificateView", "admin")
                .containsEntry("affectedCount", "2");
        verify(mapper).selectHealthCertificateTodoCandidates(any(), anySet(),
                eq(List.of(10L)), eq(List.of(10L, 20L)), eq(7L));
        verify(mapper, never()).selectScopedTodoCandidates(any(), anySet(), anyList(), anyList(), any());
    }

    @Test
    void exposesRejectedCertificateToOwnerOnlyWhenResubmissionIsEnabled()
    {
        service.permissions.addAll(Set.of("hr:healthCertificate:self:edit",
                "hr:healthCertificate:self:submit"));
        assertThat(service.enabledTypes()).doesNotContain(SysTodoTypes.HR_HEALTH_CERT_RETURNED);

        service.intakeEnabled = true;
        SysHealthCertificateTodoCandidate rejected = healthCandidate(
                201L, 7L, 10L, "门店A", "REJECTED");
        rejected.setCertificateNo("HC-201");
        rejected.setRejectionReason("附件不清晰");
        rejected.setUpdateTime(new Date(service.now - 3600_000));
        when(mapper.selectHealthCertificateTodoCandidates(any(), anySet(), anyList(), anyList(), any()))
                .thenReturn(List.of(rejected));
        TodoQuery query = new TodoQuery();
        query.setType(SysTodoTypes.HR_HEALTH_CERT_RETURNED);

        SysTodoPage page = service.selectTodoPage(query, null);

        assertThat(page.getTotal()).isEqualTo(1);
        TodoItem item = page.getRows().get(0);
        assertThat(item.getTodoKey()).isEqualTo("system:HR_HEALTH_CERT_RETURNED:201:resubmit");
        assertThat(item.getCategory()).isEqualTo("returned");
        assertThat(item.getSummary()).contains("附件不清晰");
        assertThat(item.getDeptId()).isNull();
        assertThat(item.getRouteParams()).containsEntry("certificateId", "201")
                .containsEntry("healthCertificateView", "mine")
                .containsEntry("reviewStatus", "REJECTED")
                .doesNotContainKeys("contextDeptId", "scopeMode");
    }

    @Test
    void countsActionRowsWhileKeepingAffectedCountsOnEachGroup()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        List<SysTodoCandidateRow> rows = List.of(
                candidate(1L, 10L, "门店A"), candidate(2L, 10L, "门店A"), candidate(3L, 20L, "门店B"));
        stubCandidates(rows);
        TodoSummary summary = service.selectSummary(new TodoQuery(), 10L);

        assertThat(summary.getTotal()).isEqualTo(1);
        assertThat(summary.getTypeCounts()).containsEntry(SysTodoTypes.HR_PROFILE_INCOMPLETE, 1L);
        assertThat(summary.getRecent()).hasSize(1);
        assertThat(summary.getRecent().get(0).getRouteParams()).containsEntry("count", "2");
        assertThat(summary.getRecent().get(0).getSummary()).isEqualTo("员工1、员工2，共 2 人待处理");
        verify(evaluator, org.mockito.Mockito.times(2)).evaluate(any());
        verify(mapper).selectScopedTodoCandidates(any(), anySet(),
                eq(List.of(10L)), eq(List.of(10L, 20L)), any());
    }

    @Test
    void formalEvaluatorExcludesCompleteCandidatesFromProfileTodos()
    {
        service.permissions.addAll(Set.of("hr:completeness:list","hr:employee:list","hr:employee:edit"));
        SysTodoCandidateRow incomplete=candidate(1L,10L,"门店A");
        SysTodoCandidateRow complete=candidate(2L,10L,"门店A");
        complete.setBankAccount("6222000000000000");
        stubCandidates(List.of(incomplete,complete));
        when(evaluator.evaluate(any())).thenAnswer(invocation->{
            SysUser user=invocation.getArgument(0);
            boolean missing=user.getProfile().getBankAccount()==null;
            return new HrEmployeeCompletenessSnapshot(missing?98:100,missing?58:59,
                    59,8,67,missing?List.of("bankAccount"):List.of());
        });

        TodoSummary summary=service.selectSummary(new TodoQuery(),10L);

        assertThat(summary.getTypeCounts()).containsEntry(SysTodoTypes.HR_PROFILE_INCOMPLETE,1L);
        verify(evaluator,org.mockito.Mockito.times(2)).evaluate(any());
    }

    @Test
    void appliesInclusiveContractBoundariesAndPaginatesGroupedRowsInMemory()
    {
        service.permissions.addAll(Set.of("hr:employee:list", "hr:employee:edit"));
        when(configService.selectConfigByKey("todo.contract.warning.days")).thenReturn("30");
        when(configService.selectConfigByKey("todo.contract.urgent.days")).thenReturn("7");
        SysTodoCandidateRow urgent = candidate(1L, 10L, "门店A");
        urgent.setContractEndDate(date(7));
        SysTodoCandidateRow warning = candidate(2L, 20L, "门店B");
        warning.setContractEndDate(date(30));
        stubCandidates(List.of(urgent, warning));
        TodoQuery query = new TodoQuery();
        query.setScopeMode("all_authorized");
        query.setPageNum(2);
        query.setPageSize(1);

        SysTodoPage page = service.selectTodoPage(query, null);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRows()).hasSize(1);
        assertThat(page.getRows().get(0).getPriority()).isEqualTo("important");
    }

    @Test
    void usesBusinessIdBeforeTypeAsTheStableProviderTieBreaker()
    {
        service.permissions.addAll(Set.of("hr:completeness:list",
                "hr:employee:list", "hr:employee:edit",
                "hr:onboarding:list", "hr:onboarding:confirm"));
        SysTodoCandidateRow profile = candidate(1L, 10L, "门店A");
        SysTodoCandidateRow onboarding = candidate(2L, 20L, "门店B");
        onboarding.setEmployeeStatus("待入职");
        onboarding.setOnboardingStatus("待确认");
        Date sameCreatedTime = new Date(service.now);
        profile.setCreateTime(sameCreatedTime);
        onboarding.setCreateTime(sameCreatedTime);
        stubCandidates(List.of(profile, onboarding));
        when(evaluator.evaluate(any())).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            boolean incomplete = Long.valueOf(1L).equals(
                    user.getProfile().getProfileId());
            return new HrEmployeeCompletenessSnapshot(
                    incomplete ? 98 : 100, incomplete ? 58 : 59,
                    59, 8, 67,
                    incomplete ? List.of("bankAccount") : List.of());
        });
        TodoQuery query = new TodoQuery();
        query.setScopeMode("all_authorized");
        query.setPageSize(10);

        SysTodoPage page = service.selectTodoPage(query, 10L);

        assertThat(page.getRows()).extracting(TodoItem::getBusinessId)
                .containsExactly(10L, 20L);
        assertThat(page.getRows()).extracting(TodoItem::getType)
                .containsExactly(SysTodoTypes.HR_PROFILE_INCOMPLETE,
                        SysTodoTypes.HR_ONBOARDING_CONFIRM);
    }

    @Test
    void ignoresAStaleSelectedDepartmentForAllAuthorizedScope()
    {
        service.permissions.addAll(Set.of("hr:completeness:list",
                "hr:employee:list", "hr:employee:edit"));
        TodoQuery query = new TodoQuery();
        query.setScopeMode("all_authorized");

        service.selectTodoPage(query, 999L);

        verify(shopService, never()).checkUserShopScope(any(), any(), anyBoolean());
        verify(mapper).selectScopedTodoCandidates(any(), anySet(),
                eq(List.of(10L, 20L)), eq(List.of(10L, 20L)), any());
    }

    @Test
    void searchesRawEmployeeFieldsAndGeneratedVisibleTitlesOnce()
    {
        service.permissions.addAll(Set.of("hr:completeness:list",
                "hr:employee:list", "hr:employee:edit"));
        SysTodoCandidateRow row = candidate(1L, 10L, "门店A");
        row.setEmployeeName("张三");
        row.setEmployeeNo("E1001");
        stubCandidates(List.of(row));

        TodoQuery byEmployee = new TodoQuery();
        byEmployee.setScopeMode("all_authorized");
        byEmployee.setKeyword("张三");
        TodoQuery byVisibleTitle = new TodoQuery();
        byVisibleTitle.setScopeMode("all_authorized");
        byVisibleTitle.setKeyword("员工资料待补全");

        assertThat(service.selectTodoPage(byEmployee, 10L).getTotal()).isEqualTo(1);
        assertThat(service.selectTodoPage(byVisibleTitle, 10L).getTotal()).isEqualTo(1);
    }

    @Test
    void healthCertificateDueUsesLargestConfiguredReminderThresholdAndExactLedgerFilter()
    {
        service.permissions.addAll(Set.of("hr:healthCertificate:list",
                "hr:healthCertificate:remind"));
        when(configService.selectConfigByKey("todo.health-certificate.warning-days"))
                .thenReturn("45,15,7");
        SysTodoCandidateRow row = candidate(1L, 10L, "门店A");
        row.setHealthCertificateId(81L);
        row.setHealthCertificateExpiresOn(date(40));
        stubCandidates(List.of(row));

        SysTodoPage page = service.selectTodoPage(new TodoQuery(), 10L);

        TodoItem item = item(page, SysTodoTypes.HR_HEALTH_CERT_DUE);
        assertThat(item.getPriority()).isEqualTo("important");
        assertThat(item.getRouteParams())
                .containsEntry("healthCertificateStatus", "EXPIRING_OR_EXPIRED")
                .containsEntry("healthCertificateView", "admin")
                .containsEntry("currentDeptId", "10");
    }

    @Test
    void filtersGroupedTodosByExactType()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        when(configService.selectConfigByKey("todo.contract.warning.days")).thenReturn("30");
        SysTodoCandidateRow row = candidate(1L, 10L, "门店A");
        row.setContractEndDate(date(5));
        stubCandidates(List.of(row));
        TodoQuery query = new TodoQuery();
        query.setType(SysTodoTypes.HR_CONTRACT_DUE);

        SysTodoPage page = service.selectTodoPage(query, 10L);

        assertThat(page.getRows()).extracting(TodoItem::getType)
                .containsExactly(SysTodoTypes.HR_CONTRACT_DUE);
    }

    @Test
    void emitsCanonicalBusinessQueueParametersForEveryHrType()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit",
                "hr:onboarding:list", "hr:onboarding:confirm", "system:user:edit"));
        when(configService.selectConfigByKey("todo.contract.warning.days")).thenReturn("30");

        SysTodoCandidateRow profile = candidate(1L, 10L, "门店A");
        SysTodoCandidateRow onboarding = candidate(2L, 10L, "门店A");
        onboarding.setEmployeeStatus("待入职");
        onboarding.setOnboardingStatus("待确认");
        SysTodoCandidateRow contract = candidate(3L, 10L, "门店A");
        contract.setContractEndDate(date(5));
        SysTodoCandidateRow offboard = candidate(4L, 10L, "门店A");
        offboard.setEmployeeStatus("离职");
        offboard.setLinkedAccountStatus("0");
        offboard.setLinkedAccountDelFlag("0");
        stubCandidates(List.of(profile, onboarding, contract, offboard));
        TodoQuery query = new TodoQuery();
        query.setPageSize(100);

        SysTodoPage page = service.selectTodoPage(query, 10L);

        assertThat(item(page, SysTodoTypes.HR_PROFILE_INCOMPLETE).getRouteParams())
                .containsEntry("deptId", "10")
                .containsEntry("completenessStatus", "INCOMPLETE");
        assertThat(item(page, SysTodoTypes.HR_ONBOARDING_CONFIRM).getRouteParams())
                .containsEntry("targetDeptId", "10")
                .containsEntry("status", "READY");
        assertThat(item(page, SysTodoTypes.HR_CONTRACT_DUE).getRouteParams())
                .containsEntry("deptId", "10")
                .containsEntry("contractDue", "true");
        assertThat(item(page, SysTodoTypes.HR_OFFBOARD_ACCOUNT).getRouteParams())
                .containsEntry("deptId", "10")
                .containsEntry("offboardAccountOnly", "true");
    }

    @Test
    void actionableScopeKeepsCurrentShopAndDoesNotExpandEmptyOrUnauthorizedSelection()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        SysTodoCandidateRow shopA = candidate(1L, 10L, "门店A");
        shopA.setEmployeeName("张三");
        SysTodoCandidateRow shopB = candidate(2L, 20L, "门店B");
        shopB.setEmployeeName("张三");
        stubCandidates(List.of(shopA, shopB));

        SysTodoPage currentShop = service.selectTodoPage(new TodoQuery(), 10L);
        assertThat(currentShop.getTotal()).isEqualTo(1);
        assertThat(currentShop.getRows().get(0).getDeptId()).isEqualTo(10L);
        verify(mapper).selectScopedTodoCandidates(any(), anySet(),
                eq(List.of(10L)), eq(List.of(10L, 20L)), any());

        SysTodoPage unauthorized = service.selectTodoPage(new TodoQuery(), 999L);
        assertThat(unauthorized.getTotal()).isZero();
        verify(mapper).selectScopedTodoCandidates(any(), anySet(),
                eq(List.of()), eq(List.of(10L, 20L)), any());

        SysTodoPage missingSelection = service.selectTodoPage(new TodoQuery(), null);
        assertThat(missingSelection.getTotal()).isZero();
    }

    @Test
    void emptyAuthorizationAndScopeReadFailureStayEmpty()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        when(shopService.selectShopDeptIdsByUserId(7L)).thenReturn(List.of());
        stubCandidates(List.of(candidate(1L, 10L, "门店A")));

        assertThat(service.selectTodoPage(new TodoQuery(), 10L).getTotal()).isZero();
        verify(mapper).selectScopedTodoCandidates(any(), anySet(),
                eq(List.of()), eq(List.of()), any());

        when(shopService.selectShopDeptIdsByUserId(7L)).thenThrow(new RuntimeException("scope failed"));
        assertThat(service.selectTodoPage(new TodoQuery(), 10L).getTotal()).isZero();
    }

    @Test
    void allAuthorizedKeepsSameNameAcrossShopsInSeparateGroups()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        SysTodoCandidateRow shopA = candidate(1L, 10L, "门店A");
        shopA.setEmployeeName("张三");
        SysTodoCandidateRow shopB = candidate(2L, 20L, "门店B");
        shopB.setEmployeeName("张三");
        stubCandidates(List.of(shopA, shopB));
        TodoQuery query = new TodoQuery();
        query.setScopeMode("all_authorized");

        SysTodoPage page = service.selectTodoPage(query, 999L);

        assertThat(page.getRows()).hasSize(2);
        assertThat(page.getRows()).extracting(TodoItem::getDeptId).containsExactly(10L, 20L);
        assertThat(page.getRows()).extracting(TodoItem::getSummary)
                .containsExactly("张三，共 1 人待处理", "张三，共 1 人待处理");
        assertThat(page.getRows()).extracting(item -> item.getRouteParams().get("deptId"))
                .containsExactly("10", "20");
    }

    @Test
    void summarizesThreeNamesExactlyAndAbbreviatesBeyondThree()
    {
        service.permissions.addAll(Set.of("hr:completeness:list", "hr:employee:list", "hr:employee:edit"));
        stubCandidates(List.of(
                named(1L, 10L, "甲"), named(2L, 10L, "乙"), named(3L, 10L, "丙")));
        assertThat(service.selectTodoPage(new TodoQuery(), 10L).getRows().get(0).getSummary())
                .isEqualTo("甲、乙、丙，共 3 人待处理");

        stubCandidates(List.of(
                named(1L, 10L, "甲"), named(2L, 10L, "乙"),
                named(3L, 10L, "丙"), named(4L, 10L, "丁")));
        assertThat(service.selectTodoPage(new TodoQuery(), 10L).getRows().get(0).getSummary())
                .isEqualTo("甲、乙、丙等，共 4 人待处理");
    }

    private TodoItem item(SysTodoPage page, String type)
    {
        return page.getRows().stream().filter(row -> type.equals(row.getType())).findFirst().orElseThrow();
    }

    private void assertPriorityResult(String priority, long expectedTotal,
            long expectedUrgent, long expectedImportant, long expectedNormal)
    {
        TodoQuery query = new TodoQuery();
        query.setPriority(priority);
        query.setPageSize(1);

        SysTodoPage page = service.selectTodoPage(query, null);
        TodoSummary summary = service.selectSummary(query, null);

        assertThat(page.getTotal()).isEqualTo(expectedTotal);
        assertThat(page.getRows()).hasSize(1)
                .allSatisfy(item -> assertThat(item.getPriority()).isEqualTo(priority));
        assertThat(summary.getTotal()).isEqualTo(expectedTotal);
        assertThat(summary.getUrgent()).isEqualTo(expectedUrgent);
        assertThat(summary.getImportant()).isEqualTo(expectedImportant);
        assertThat(summary.getNormal()).isEqualTo(expectedNormal);
        assertThat(summary.getRecent()).isNotEmpty()
                .allSatisfy(item -> assertThat(item.getPriority()).isEqualTo(priority));
    }

    @SuppressWarnings("unchecked")
    private void stubCandidates(List<SysTodoCandidateRow> rows)
    {
        when(mapper.selectScopedTodoCandidates(any(), anySet(), anyList(), anyList(), any()))
                .thenAnswer(invocation -> {
                    List<Long> current = invocation.getArgument(2);
                    List<Long> authorized = invocation.getArgument(3);
                    if (current == null || current.isEmpty()
                            || authorized == null || authorized.isEmpty())
                    {
                        return List.of();
                    }
                    return rows.stream()
                            .filter(row -> current.contains(row.getDeptId())
                                    && authorized.contains(row.getDeptId()))
                            .toList();
                });
    }

    private SysTodoCandidateRow named(Long profileId, Long deptId, String employeeName)
    {
        SysTodoCandidateRow row = candidate(profileId, deptId, "门店A");
        row.setEmployeeName(employeeName);
        return row;
    }

    private SysTodoCandidateRow candidate(Long profileId, Long deptId, String deptName)
    {
        SysTodoCandidateRow row = new SysTodoCandidateRow();
        row.setProfileId(profileId);
        row.setDeptId(deptId);
        row.setDeptName(deptName);
        row.setDeptType("STORE");
        row.setUserId(profileId);
        row.setEmployeeStatus("正式");
        row.setEmployeeName("员工"+profileId);
        row.setPhoneNumber("13800138000");
        row.setEmployeeEmail("employee"+profileId+"@example.com");
        row.setEmployeeSex("0");
        row.setPostNames("店员");
        row.setDeptLeader("主管");
        row.setDeptStatus("0");
        row.setCreateTime(new Date());
        return row;
    }

    private SysHealthCertificateTodoCandidate healthCandidate(Long certificateId,
            Long userId, Long deptId, String deptName, String status)
    {
        SysHealthCertificateTodoCandidate row = new SysHealthCertificateTodoCandidate();
        row.setCertificateId(certificateId);
        row.setUserId(userId);
        row.setDeptId(deptId);
        row.setDeptName(deptName);
        row.setDeptType("STORE");
        row.setEmployeeNo("E" + userId);
        row.setEmployeeName("员工" + userId);
        row.setReviewStatus(status);
        row.setCreateTime(new Date(service.now));
        return row;
    }

    private Date date(int plusDays)
    {
        return Date.from(service.today.plusDays(plusDays).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static class TestService extends SysTodoServiceImpl
    {
        private final Set<String> permissions = new LinkedHashSet<>();
        private final LocalDate today = LocalDate.of(2026, 7, 11);
        private final long now = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        private boolean intakeEnabled;

        TestService(SysTodoMapper mapper, ISysUserShopService shopService,
                HrEmployeeCompletenessEvaluator evaluator, ISysConfigService configService)
        {
            super(mapper, shopService, evaluator, configService);
        }

        @Override protected Long currentUserId() { return 7L; }
        @Override protected boolean currentUserIsAdmin() { return false; }
        @Override protected boolean hasPermission(String permission) { return permissions.contains(permission); }
        @Override protected LocalDate currentDate() { return today; }
        @Override protected long currentTimeMillis() { return now; }
        @Override protected boolean healthCertificateIntakeEnabled() { return intakeEnabled; }
        Set<String> enabledTypes() { return resolveEnabledTypes(); }
    }
}
