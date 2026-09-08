package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import com.erp.common.core.domain.R;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaTodoTypes;
import com.erp.oa.domain.vo.OaTodoCandidate;
import com.erp.oa.domain.vo.OaTodoCountRow;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaTodoMapper;
import com.erp.system.api.RemoteConfigService;

class OaTodoServiceImplTest
{
    private OaTodoMapper mapper;
    private OaDeptScopeMapper deptMapper;
    private TestService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(OaTodoMapper.class);
        deptMapper = mock(OaDeptScopeMapper.class);
        service = new TestService(mapper, deptMapper);
        when(deptMapper.selectUserAuthorizedOaDeptIds(7L)).thenReturn(List.of(10L, 20L));
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt())).thenReturn(new ArrayList<>());
        when(mapper.selectOaTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(mapper.selectRecentOaTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>());
    }

    @Test
    void missingOptionalSigningSchemaKeepsOtherOaTodoTypesAvailable()
    {
        service.availableTables.clear();
        service.availableTables.add("oa_labor_contract");

        assertThat(service.enabledTypes())
                .contains(OaTodoTypes.OA_LABOR_CONTRACT_SIGN)
                .doesNotContain(
                        OaTodoTypes.OA_SIGN_PACKAGE_SIGN,
                        OaTodoTypes.OA_SIGN_ONBOARD_DATA_REQUEST,
                        OaTodoTypes.OA_SIGN_NEEDS_DATA,
                        OaTodoTypes.OA_SIGN_COMPANY_FINALIZE,
                        OaTodoTypes.OA_SIGN_SEND_FAILED,
                        OaTodoTypes.OA_SIGN_REFUSED,
                        OaTodoTypes.OA_SIGN_EXPIRED);
    }

    @Test
    void configuredOperatorNeedsListAndExactActionPermissionForEachSigningType()
    {
        service.signHr = true;
        service.permissions.add("oa:signTask:list");

        assertThat(service.enabledTypes()).doesNotContain(
                OaTodoTypes.OA_SIGN_NEEDS_DATA, OaTodoTypes.OA_SIGN_COMPANY_FINALIZE,
                OaTodoTypes.OA_SIGN_SEND_FAILED, OaTodoTypes.OA_SIGN_REFUSED,
                OaTodoTypes.OA_SIGN_EXPIRED);

        service.permissions.add("oa:signTask:revalidate");
        service.permissions.add("oa:signTask:retry");
        assertThat(service.enabledTypes()).contains(
                OaTodoTypes.OA_SIGN_NEEDS_DATA, OaTodoTypes.OA_SIGN_SEND_FAILED)
                .doesNotContain(OaTodoTypes.OA_SIGN_REFUSED, OaTodoTypes.OA_SIGN_EXPIRED)
                .doesNotContain(OaTodoTypes.OA_SIGN_COMPANY_FINALIZE);

        service.permissions.add("oa:signTask:resolveRefusal");
        service.permissions.add("oa:signTask:resolveExpiry");
        assertThat(service.enabledTypes()).contains(
                OaTodoTypes.OA_SIGN_REFUSED, OaTodoTypes.OA_SIGN_EXPIRED);

        service.permissions.add("oa:signPackage:send");
        assertThat(service.enabledTypes()).contains(OaTodoTypes.OA_SIGN_COMPANY_FINALIZE);

        service.signHr = false;
        assertThat(service.enabledTypes()).doesNotContain(
                OaTodoTypes.OA_SIGN_NEEDS_DATA, OaTodoTypes.OA_SIGN_COMPANY_FINALIZE,
                OaTodoTypes.OA_SIGN_SEND_FAILED, OaTodoTypes.OA_SIGN_REFUSED,
                OaTodoTypes.OA_SIGN_EXPIRED);
    }

    @Test
    void passesCurrentAndAuthorizedScopesSeparately()
    {
        service.selectTodoList(new TodoQuery(), 10L);

        verify(mapper).selectOaTodoList(any(), anySet(), eq(List.of(10L)),
                eq(List.of(10L, 20L)), eq(7L), eq("operator"), eq(24));

        clearInvocations(mapper);
        service.selectTodoList(new TodoQuery(), 99L);
        verify(mapper).selectOaTodoList(any(), anySet(), eq(Collections.emptyList()),
                eq(List.of(10L, 20L)), eq(7L), eq("operator"), eq(24));
    }

    @Test
    void acceptsActionableAndNormalizesLegacyScopesBeforeCallingTheMapper()
    {
        for (String supported : new String[] { null, "", " ",
                TodoConstants.SCOPE_ACTIONABLE,
                TodoConstants.SCOPE_CURRENT_ORG,
                TodoConstants.SCOPE_ALL_AUTHORIZED })
        {
            TodoQuery query = new TodoQuery();
            query.setScopeMode(supported);

            service.selectTodoList(query, 10L);

            assertThat(query.getScopeMode()).isEqualTo(TodoConstants.SCOPE_ACTIONABLE);
        }

        TodoQuery invalid = new TodoQuery();
        invalid.setScopeMode("other_users");
        assertThatThrownBy(() -> service.selectTodoList(invalid, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的待办组织范围");
        assertThatThrownBy(() -> service.selectSummary(invalid, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的待办组织范围");
    }

    @Test
    void adminUsesAllActiveScope()
    {
        service.admin = true;
        service.userId = 1L;
        when(deptMapper.selectAllActiveOaDeptIds()).thenReturn(List.of(30L, 40L));
        service.selectTodoList(new TodoQuery(), null);

        verify(mapper).selectOaTodoList(any(), anySet(), eq(Collections.emptyList()),
                eq(List.of(30L, 40L)), eq(1L), eq("operator"), eq(24));
    }

    @Test
    void validatesAllFiltersBeforeScopeConfigPaginationOrMapper()
    {
        TodoQuery query = new TodoQuery();
        query.setCategory("invalid");
        RemoteConfigService remote = mock(RemoteConfigService.class);
        service.setRemoteConfigService(remote);

        assertThatThrownBy(() -> service.selectTodoList(query, 10L))
                .isInstanceOf(ServiceException.class);
        assertThat(service.pageStarted).isFalse();
        verifyNoInteractions(deptMapper, remote);
        verify(mapper, never()).selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectsInvalidPrioritiesBeforeScopeConfigPaginationOrMapper()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        service.setRemoteConfigService(remote);

        for (String invalid : List.of("high", "URGENT", "urgent' or 1=1 --"))
        {
            TodoQuery query = new TodoQuery();
            query.setPriority(invalid);

            assertThatThrownBy(() -> service.selectTodoList(query, 10L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("不支持的待办优先级");
            assertThatThrownBy(() -> service.selectSummary(query, 10L))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("不支持的待办优先级");
        }

        assertThat(service.pageStarted).isFalse();
        verifyNoInteractions(deptMapper, remote);
        verify(mapper, never()).selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt());
        verify(mapper, never()).selectOaTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt());
        verify(mapper, never()).selectRecentOaTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    void forwardsValidPriorityToListCountsAndRecentQueries()
    {
        TodoQuery query = new TodoQuery();
        query.setPriority(TodoConstants.PRIORITY_IMPORTANT);

        service.selectTodoList(query, 10L);
        service.selectSummary(query, 10L);

        verify(mapper).selectOaTodoList(eq(query), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24));
        verify(mapper).selectOaTodoCounts(eq(query), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24));
        verify(mapper).selectRecentOaTodos(eq(query), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(5));
    }

    @Test
    void nonOaSourceProducesEmptyEnabledSet()
    {
        TodoQuery query = new TodoQuery();
        query.setSource("inventory");

        service.selectTodoList(query, 10L);

        verify(mapper).selectOaTodoList(eq(query), eq(Collections.emptySet()), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24));
    }

    @Test
    void keepsPageTotalAndDecoratesCompleteRouteContext()
    {
        Page<TodoItem> page = new Page<>(2, 10);
        page.setTotal(42L);
        TodoItem item = item(OaTodoTypes.OA_SIGN_NEEDS_DATA, 101L,
                "resolve", 10L);
        page.add(item);
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt())).thenReturn(page);

        List<TodoItem> result = service.selectTodoList(new TodoQuery(), 10L);

        assertThat(result).isSameAs(page);
        assertThat(new PageInfo<>(result).getTotal()).isEqualTo(42L);
        assertThat(item.getTodoKey()).isEqualTo(
                "oa:OA_SIGN_NEEDS_DATA:101:resolve");
        assertThat(item.getRouteParams()).containsEntry("businessId", "101")
                .containsEntry("contextDeptId", "10")
                .containsEntry("contextDeptName", "门店A")
                .containsEntry("contextDeptType", "STORE")
                .containsEntry("scopeMode", "current_org");
    }

    @Test
    void personalRoutesHaveNoOrganizationContextAndDuplicateKeysFail()
    {
        TodoItem personal = item(OaTodoTypes.OA_LABOR_CONTRACT_SIGN, 201L, "sign", null);
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt())).thenReturn(new ArrayList<>(List.of(personal)));

        service.selectTodoList(new TodoQuery(), 10L);
        assertThat(personal.getRouteParams()).containsOnlyKeys("businessId");

        TodoItem duplicate = item(OaTodoTypes.OA_LABOR_CONTRACT_SIGN, 201L, "sign", null);
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt())).thenReturn(new ArrayList<>(List.of(personal, duplicate)));
        assertThatThrownBy(() -> service.selectTodoList(new TodoQuery(), 10L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate todoKey");
    }

    @Test
    void onboardDataRequestRouteCarriesExactRequestIdWithoutOrganizationContext()
    {
        OaTodoCandidate request = new OaTodoCandidate();
        request.setSource("oa");
        request.setType(OaTodoTypes.OA_SIGN_ONBOARD_DATA_REQUEST);
        request.setCategory("personal");
        request.setBusinessId(401L);
        request.setRouteType("onboard_data_request");
        request.setRouteReference("9007199254740993");
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt()))
                .thenReturn(new ArrayList<>(List.of(request)));

        service.selectTodoList(new TodoQuery(), 10L);

        assertThat(request.getTodoKey())
                .isEqualTo("oa:OA_SIGN_ONBOARD_DATA_REQUEST:401:onboard_data_request");
        assertThat(request.getRouteParams()).containsOnlyKeys("businessId", "requestId")
                .containsEntry("businessId", "401")
                .containsEntry("requestId", "9007199254740993");
    }

    @Test
    void signingHrRoutesCarryTaskAndValidatedNotificationReferences()
    {
        OaTodoCandidate notification = new OaTodoCandidate();
        notification.setSource("oa");
        notification.setType(OaTodoTypes.OA_SIGN_SEND_FAILED);
        notification.setCategory("risk");
        notification.setBusinessId(301L);
        notification.setRouteType("retry_sign_notification");
        notification.setRouteReference("SIGN_SENT:90:SP-90-V1");
        notification.setDeptId(10L);
        notification.setDeptName("门店A");
        notification.setDeptType("STORE");
        notification.setScopeMode("current_org");
        when(mapper.selectOaTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt()))
                .thenReturn(new ArrayList<>(List.of(notification)));

        service.selectTodoList(new TodoQuery(), 10L);

        assertThat(notification.getTodoKey())
                .isEqualTo("oa:OA_SIGN_SEND_FAILED:301:retry_sign_notification");
        assertThat(notification.getRouteParams())
                .containsEntry("businessId", "301")
                .containsEntry("taskId", "301")
                .containsEntry("notificationBusinessKey", "SIGN_SENT:90:SP-90-V1")
                .containsEntry("contextDeptId", "10");
    }

    @Test
    void summaryAggregatesCategoriesPrioritiesTypesAndConfigBounds()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(OaTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenReturn(R.ok("48"));
        when(remote.getConfigKey(OaTodoServiceImpl.SUMMARY_RECENT_LIMIT_KEY, "inner"))
                .thenReturn(R.ok("9"));
        service.setRemoteConfigService(remote);
        when(mapper.selectOaTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), eq(48))).thenReturn(List.of(
                        count(OaTodoTypes.OA_SIGN_EXPIRED, "risk", "urgent", 2),
                        count(OaTodoTypes.OA_LABOR_CONTRACT_SIGN, "personal", "important", 3)));

        TodoSummary summary = service.selectSummary(new TodoQuery(), null);

        assertThat(summary.getSource()).isEqualTo("oa");
        assertThat(summary.getTotal()).isEqualTo(5);
        assertThat(summary.getRisk()).isEqualTo(2);
        assertThat(summary.getPersonal()).isEqualTo(3);
        assertThat(summary.getUrgent()).isEqualTo(2);
        assertThat(summary.getImportant()).isEqualTo(3);
        assertThat(summary.getTypeCounts()).containsEntry(OaTodoTypes.OA_SIGN_EXPIRED, 2L)
                .containsEntry(OaTodoTypes.OA_LABOR_CONTRACT_SIGN, 3L);
        verify(mapper).selectRecentOaTodos(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(48), eq(9));
    }

    @Test
    void approvalUrgentConfigAcceptsInclusiveBounds()
    {
        assertConfiguredUrgentHours("1", 1);
        assertConfiguredUrgentHours("168", 168);
    }

    @Test
    void approvalUrgentConfigRejectsZeroAboveMaximumMalformedAndRemoteFailure()
    {
        assertConfiguredUrgentHours("0", 24);
        assertConfiguredUrgentHours("169", 24);
        assertConfiguredUrgentHours("not-a-number", 24);

        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(OaTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenThrow(new IllegalStateException("offline"));
        service.setRemoteConfigService(remote);
        clearInvocations(mapper);

        service.selectTodoList(new TodoQuery(), 10L);

        verify(mapper).selectOaTodoList(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24));
    }

    @Test
    void malformedOrOutOfRangeRemoteConfigFallsBack()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(OaTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenThrow(new IllegalStateException("offline"));
        when(remote.getConfigKey(OaTodoServiceImpl.SUMMARY_RECENT_LIMIT_KEY, "inner"))
                .thenReturn(R.ok("999"));
        service.setRemoteConfigService(remote);

        service.selectSummary(new TodoQuery(), null);

        verify(mapper).selectOaTodoCounts(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24));
        verify(mapper).selectRecentOaTodos(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(5));
    }

    private void assertConfiguredUrgentHours(String configured, int expected)
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(OaTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenReturn(R.ok(configured));
        service.setRemoteConfigService(remote);
        clearInvocations(mapper);

        service.selectTodoList(new TodoQuery(), 10L);

        verify(mapper).selectOaTodoList(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(expected));
    }

    private static TodoItem item(String type, Long id, String route, Long deptId)
    {
        TodoItem item = new TodoItem();
        item.setSource("oa");
        item.setType(type);
        item.setCategory(type.contains("SIGN") ? "personal" : "execution");
        item.setBusinessId(id);
        item.setRouteType(route);
        if (deptId != null)
        {
            item.setDeptId(deptId);
            item.setDeptName("门店A");
            item.setDeptType("STORE");
            item.setScopeMode("current_org");
        }
        return item;
    }

    private static OaTodoCountRow count(String type, String category, String priority, long count)
    {
        OaTodoCountRow row = new OaTodoCountRow();
        row.setType(type);
        row.setCategory(category);
        row.setPriority(priority);
        row.setCount(count);
        return row;
    }

    private static final class TestService extends OaTodoServiceImpl
    {
        private Long userId = 7L;
        private String username = "operator";
        private boolean admin;
        private boolean signHr;
        private boolean pageStarted;
        private final Set<String> permissions = new LinkedHashSet<>();
        private final Set<String> availableTables = new LinkedHashSet<>(Set.of(
                "oa_labor_contract",
                "oa_sign_package", "oa_sign_task",
                "oa_sign_notification_outbox",
                "oa_sign_onboard_data_request",
                "oa_sign_onboard_import_row"));
        private TestService(OaTodoMapper mapper, OaDeptScopeMapper deptMapper)
        {
            super(mapper, deptMapper);
        }

        @Override protected Long currentUserId() { return userId; }
        @Override protected String currentUsername() { return username; }
        @Override protected boolean currentUserIsAdmin() { return admin; }
        @Override protected boolean currentUserIsConfiguredSignHr() { return signHr; }
        @Override protected boolean hasPermission(String permission) { return permissions.contains(permission); }
        @Override protected Set<String> availableTodoTables() { return availableTables; }
        @Override protected void startPage(TodoQuery query) { pageStarted = true; }
        private Set<String> enabledTypes() { return resolveEnabledTypes(); }
    }
}
