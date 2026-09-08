package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import com.erp.common.core.domain.R;
import com.erp.common.core.domain.todo.TodoConstants;
import com.erp.common.core.domain.todo.TodoItem;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvTodoTypes;
import com.erp.inventory.domain.InvTransferShipment;
import com.erp.inventory.domain.vo.InvTodoCountRow;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTodoMapper;
import com.erp.inventory.mapper.InvTransferShipmentMapper;
import com.erp.system.api.RemoteConfigService;

class InvTodoServiceImplTest
{
    private InvTodoMapper todoMapper;
    private InvDeptScopeMapper deptScopeMapper;
    private InvTransferShipmentMapper shipmentMapper;
    private TestService service;

    @BeforeEach
    void setUp()
    {
        todoMapper = mock(InvTodoMapper.class);
        deptScopeMapper = mock(InvDeptScopeMapper.class);
        shipmentMapper = mock(InvTransferShipmentMapper.class);
        service = new TestService(todoMapper, deptScopeMapper, shipmentMapper);
        service.userId = 7L;
        service.username = "operator";
        when(deptScopeMapper.selectUserAuthorizedInventoryDeptIds(7L)).thenReturn(List.of(10L, 20L));
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>());
        when(todoMapper.selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(todoMapper.selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt(), anyInt())).thenReturn(new ArrayList<>());
    }

    @Test
    void serviceContractShouldExposeSummaryAndList() throws Exception
    {
        assertThat(Class.forName("com.erp.inventory.service.IInvTodoService")
                .getMethod("selectSummary", TodoQuery.class, Long.class).getReturnType())
                .isEqualTo(TodoSummary.class);
        assertThat(Class.forName("com.erp.inventory.service.IInvTodoService")
                .getMethod("selectTodoList", TodoQuery.class, Long.class)
                .getGenericReturnType().getTypeName())
                .isEqualTo("java.util.List<com.erp.common.core.domain.todo.TodoItem>");
    }

    @Test
    void eachActionRequiresItsTargetPageReadPermission()
    {
        String[][] pairs = {
                { InvTodoTypes.INV_TRANSFER_APPROVAL, "inv:transfer:approve", "inv:transfer:list" },
                { InvTodoTypes.INV_STOCK_CHECK_APPROVAL, "inv:stockCheck:approve", "inv:stockCheck:list" },
                { InvTodoTypes.INV_STOCK_CHECK_EXECUTE, "inv:stockCheck:submit", "inv:stockCheck:list" },
                { InvTodoTypes.INV_PURCHASE_QC, "inv:purchase:qc", "inv:purchase:list" },
                { InvTodoTypes.INV_PURCHASE_RECEIVE, "inv:purchase:receive", "inv:purchase:list" },
                { InvTodoTypes.INV_SALES_NOTICE_CREATE, "inv:deliveryNotice:add", "inv:sales:list" },
                { InvTodoTypes.INV_DELIVERY_EXECUTE, "inv:deliveryNotice:deliver", "inv:deliveryNotice:list" },
                { InvTodoTypes.INV_TRANSFER_DELIVER, "inv:transfer:deliver", "inv:transfer:list" },
                { InvTodoTypes.INV_TRANSFER_RECEIVE, "inv:transfer:receive", "inv:transfer:list" },
                { InvTodoTypes.INV_TRANSFER_DISCREPANCY, "inv:transfer:discrepancy:handle", "inv:transfer:list" },
                { InvTodoTypes.INV_PURCHASE_RETURN_CONFIRM, "inv:purchaseReturn:confirm", "inv:purchaseReturn:list" },
                { InvTodoTypes.INV_SALES_RETURN_CONFIRM, "inv:salesReturn:confirm", "inv:salesReturn:list" },
                { InvTodoTypes.INV_STOCK_CHECK_RETURNED, "inv:stockCheck:submit", "inv:stockCheck:list" },
                { InvTodoTypes.INV_STOCK_CHECK_RESTART, "inv:stockCheck:submit", "inv:stockCheck:list" }
        };

        for (String[] pair : pairs)
        {
            service.permissions.clear();
            service.permissions.add(pair[1]);
            assertThat(service.enabledTypes()).doesNotContain(pair[0]);
            service.permissions.add(pair[2]);
            assertThat(service.enabledTypes()).contains(pair[0]);
        }
    }

    @Test
    void returnedTransferAndRiskUseTheirExactOrPermissions()
    {
        service.permissions.add("inv:transfer:list");
        assertThat(service.enabledTypes()).doesNotContain(InvTodoTypes.INV_TRANSFER_RETURNED);
        service.permissions.add("inv:transfer:edit");
        assertThat(service.enabledTypes()).contains(InvTodoTypes.INV_TRANSFER_RETURNED);

        service.permissions.clear();
        service.permissions.add("inv:stock:list");
        assertThat(service.enabledTypes()).doesNotContain(
                InvTodoTypes.INV_LOW_STOCK, InvTodoTypes.INV_OUT_OF_STOCK);
        service.permissions.add("inv:stock:adjust");
        assertThat(service.enabledTypes()).contains(
                InvTodoTypes.INV_LOW_STOCK, InvTodoTypes.INV_OUT_OF_STOCK);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    void passesSeparateCurrentAndAllAuthorizedScopesAndFailsClosedForUnauthorizedSelection()
    {
        service.selectTodoList(new TodoQuery(), 10L);
        ArgumentCaptor<List> current = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> authorized = ArgumentCaptor.forClass(List.class);
        verify(todoMapper).selectInventoryTodoList(any(), anySet(), current.capture(), authorized.capture(),
                eq(7L), eq("operator"), eq(24), eq(24));
        assertThat(current.getValue()).containsExactly(10L);
        assertThat(authorized.getValue()).containsExactly(10L, 20L);

        clearInvocations(todoMapper);
        service.selectTodoList(new TodoQuery(), 99L);
        verify(todoMapper).selectInventoryTodoList(any(), anySet(), current.capture(), authorized.capture(),
                eq(7L), eq("operator"), eq(24), eq(24));
        assertThat(current.getValue()).isEmpty();
        assertThat(authorized.getValue()).containsExactly(10L, 20L);
    }

    @Test
    void normalizesLegacyAndBlankScopesToActionableAndRejectsUnknownScope()
    {
        for (String legacy : new String[] { null, "", " ", TodoConstants.SCOPE_ACTIONABLE,
                TodoConstants.SCOPE_CURRENT_ORG, TodoConstants.SCOPE_ALL_AUTHORIZED })
        {
            TodoQuery query = new TodoQuery();
            query.setScopeMode(legacy);

            service.selectTodoList(query, 10L);

            assertThat(query.getScopeMode()).isEqualTo(TodoConstants.SCOPE_ACTIONABLE);
        }

        TodoQuery invalid = new TodoQuery();
        invalid.setScopeMode("other_users");
        assertThatThrownBy(() -> service.selectTodoList(invalid, 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("不支持的待办组织范围");
    }

    @Test
    void adminUsesAllActiveInventoryScopeButStillPassesConcreteUserId()
    {
        service.admin = true;
        service.userId = 1L;
        when(deptScopeMapper.selectAllActiveInventoryDeptIds()).thenReturn(List.of(30L, 40L));
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                eq(1L), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>());

        service.selectTodoList(new TodoQuery(), null);

        verify(deptScopeMapper).selectAllActiveInventoryDeptIds();
        verify(deptScopeMapper, never()).selectUserAuthorizedInventoryDeptIds(anyLong());
        verify(todoMapper).selectInventoryTodoList(any(), anySet(), eq(Collections.emptyList()),
                eq(List.of(30L, 40L)), eq(1L), eq("operator"), eq(24), eq(24));
    }

    @SuppressWarnings("unchecked")
    @Test
    void defaultMixedSummaryKeepsCrossOrganizationApprovalsWithoutCurrentExecutionScope()
    {
        when(todoMapper.selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    List<Long> current = invocation.getArgument(2);
                    List<Long> authorized = invocation.getArgument(3);
                    List<InvTodoCountRow> rows = new ArrayList<>();
                    if (authorized.contains(20L))
                    {
                        rows.add(count(InvTodoTypes.INV_TRANSFER_APPROVAL, "approval", "important", 1));
                    }
                    if (current.contains(20L))
                    {
                        rows.add(count(InvTodoTypes.INV_PURCHASE_RECEIVE, "execution", "normal", 1));
                    }
                    return rows;
                });
        when(todoMapper.selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    List<Long> current = invocation.getArgument(2);
                    List<Long> authorized = invocation.getArgument(3);
                    List<TodoItem> rows = new ArrayList<>();
                    if (authorized.contains(20L))
                    {
                        rows.add(item(InvTodoTypes.INV_TRANSFER_APPROVAL, 201L, "approve"));
                    }
                    if (current.contains(20L))
                    {
                        rows.add(item(InvTodoTypes.INV_PURCHASE_RECEIVE, 202L, "receive"));
                    }
                    return rows;
                });

        TodoQuery query = new TodoQuery();
        TodoSummary result = service.selectSummary(query, null);

        assertThat(query.getScopeMode()).isEqualTo(TodoConstants.SCOPE_ACTIONABLE);
        assertThat(result.getApproval()).isEqualTo(1L);
        assertThat(result.getExecution()).isZero();
        assertThat(result.getRecent()).extracting(TodoItem::getBusinessId)
                .contains(201L)
                .doesNotContain(202L);
        verify(todoMapper).selectInventoryTodoCounts(eq(query), anySet(),
                eq(Collections.emptyList()), eq(List.of(10L, 20L)),
                eq(7L), eq("operator"), eq(24), eq(24));
        verify(todoMapper).selectRecentInventoryTodos(eq(query), anySet(),
                eq(Collections.emptyList()), eq(List.of(10L, 20L)),
                eq(7L), eq("operator"), eq(24), eq(24), eq(5));
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminDoesNotGainNonCandidateApprovalAndExecutionStillUsesCurrentScope()
    {
        service.admin = true;
        service.userId = 1L;
        when(deptScopeMapper.selectAllActiveInventoryDeptIds()).thenReturn(List.of(30L, 40L));
        when(todoMapper.selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    List<Long> current = invocation.getArgument(2);
                    Long candidateUserId = invocation.getArgument(4);
                    List<InvTodoCountRow> rows = new ArrayList<>();
                    if (Long.valueOf(99L).equals(candidateUserId))
                    {
                        rows.add(count(InvTodoTypes.INV_TRANSFER_APPROVAL, "approval", "important", 5));
                    }
                    if (current.contains(30L))
                    {
                        rows.add(count(InvTodoTypes.INV_PURCHASE_RECEIVE, "execution", "normal", 2));
                    }
                    return rows;
                });
        when(todoMapper.selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    List<Long> current = invocation.getArgument(2);
                    Long candidateUserId = invocation.getArgument(4);
                    List<TodoItem> rows = new ArrayList<>();
                    if (Long.valueOf(99L).equals(candidateUserId))
                    {
                        rows.add(item(InvTodoTypes.INV_TRANSFER_APPROVAL, 399L, "approve"));
                    }
                    if (current.contains(30L))
                    {
                        rows.add(item(InvTodoTypes.INV_PURCHASE_RECEIVE, 301L, "receive"));
                    }
                    return rows;
                });

        TodoSummary selected = service.selectSummary(new TodoQuery(), 30L);
        TodoSummary noSelection = service.selectSummary(new TodoQuery(), null);

        assertThat(selected.getApproval()).isZero();
        assertThat(selected.getExecution()).isEqualTo(2L);
        assertThat(selected.getRecent()).extracting(TodoItem::getBusinessId).containsExactly(301L);
        assertThat(noSelection.getApproval()).isZero();
        assertThat(noSelection.getExecution()).isZero();
        assertThat(noSelection.getRecent()).isEmpty();
        verify(todoMapper).selectInventoryTodoCounts(any(), anySet(), eq(List.of(30L)),
                eq(List.of(30L, 40L)), eq(1L), eq("operator"), eq(24), eq(24));
        verify(todoMapper).selectInventoryTodoCounts(any(), anySet(), eq(Collections.emptyList()),
                eq(List.of(30L, 40L)), eq(1L), eq("operator"), eq(24), eq(24));
    }

    @Test
    void validatesBeforePaginationAndDoesNotPageInvalidQueries()
    {
        TodoQuery query = new TodoQuery();
        query.setCategory("unknown");

        assertThatThrownBy(() -> service.selectTodoList(query, 10L))
                .isInstanceOf(ServiceException.class);
        assertThat(service.pageStarted).isFalse();
        verify(todoMapper, never()).selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    void summaryFailsClosedBeforeScopeOrQueriesWhenUsernameIsNull()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        service.setRemoteConfigService(remote);
        service.username = null;

        assertThatThrownBy(() -> service.selectSummary(new TodoQuery(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户");

        verifyNoInteractions(deptScopeMapper, remote);
        verify(todoMapper, never()).selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt());
        verify(todoMapper, never()).selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt(), anyInt());
        assertThat(service.pageStarted).isFalse();
    }

    @Test
    void listFailsClosedBeforeScopePaginationOrQueriesWhenUsernameIsBlank()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        service.setRemoteConfigService(remote);
        service.username = " \t ";

        assertThatThrownBy(() -> service.selectTodoList(new TodoQuery(), 10L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户");

        verifyNoInteractions(deptScopeMapper, remote);
        verify(todoMapper, never()).selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt());
        assertThat(service.pageStarted).isFalse();
    }

    @Test
    void keepsPageInstanceAndTotalWhileDecorating()
    {
        Page<TodoItem> page = new Page<>(2, 10);
        page.setTotal(42L);
        page.add(item(InvTodoTypes.INV_PURCHASE_RECEIVE, 101L, "receive"));
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(page);

        List<TodoItem> result = service.selectTodoList(new TodoQuery(), 10L);

        assertThat(result).isSameAs(page);
        assertThat(new PageInfo<>(result).getTotal()).isEqualTo(42L);
        assertThat(result.get(0).getTodoKey()).isEqualTo(
                "inventory:INV_PURCHASE_RECEIVE:101:receive");
    }

    @Test
    void aggregatesCountsPrioritiesTypesAndConfiguredRecentLimit()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(InvTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenReturn(R.ok("48"));
        when(remote.getConfigKey(InvTodoServiceImpl.STOCK_CHECK_DUE_SOON_HOURS_KEY, "inner"))
                .thenReturn(R.ok("36"));
        when(remote.getConfigKey(InvTodoServiceImpl.SUMMARY_RECENT_LIMIT_KEY, "inner"))
                .thenReturn(R.ok("9"));
        service.setRemoteConfigService(remote);
        when(todoMapper.selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), eq(48), eq(36))).thenReturn(List.of(
                        count(InvTodoTypes.INV_TRANSFER_APPROVAL, "approval", "urgent", 2),
                        count(InvTodoTypes.INV_LOW_STOCK, "risk", "normal", 3)));
        when(todoMapper.selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), eq(48), eq(36), eq(9))).thenReturn(new ArrayList<>(List.of(
                        item(InvTodoTypes.INV_TRANSFER_APPROVAL, 11L, "approve"))));

        TodoSummary result = service.selectSummary(new TodoQuery(), null);

        assertThat(result.getSource()).isEqualTo("inventory");
        assertThat(result.getTotal()).isEqualTo(5L);
        assertThat(result.getApproval()).isEqualTo(2L);
        assertThat(result.getRisk()).isEqualTo(3L);
        assertThat(result.getUrgent()).isEqualTo(2L);
        assertThat(result.getNormal()).isEqualTo(3L);
        assertThat(result.getTypeCounts()).containsEntry(InvTodoTypes.INV_TRANSFER_APPROVAL, 2L)
                .containsEntry(InvTodoTypes.INV_LOW_STOCK, 3L);
        assertThat(result.getRecent()).extracting(TodoItem::getBusinessId).containsExactly(11L);
    }

    @Test
    void remoteFailuresMalformedAndOutOfRangeValuesFallBackSafely()
    {
        RemoteConfigService remote = mock(RemoteConfigService.class);
        when(remote.getConfigKey(InvTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner"))
                .thenThrow(new IllegalStateException("offline"));
        when(remote.getConfigKey(InvTodoServiceImpl.SUMMARY_RECENT_LIMIT_KEY, "inner"))
                .thenReturn(R.ok("999"));
        when(remote.getConfigKey(InvTodoServiceImpl.STOCK_CHECK_DUE_SOON_HOURS_KEY, "inner"))
                .thenReturn(R.ok("0"));
        service.setRemoteConfigService(remote);

        service.selectSummary(new TodoQuery(), 10L);

        verify(todoMapper).selectInventoryTodoCounts(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(24));
        verify(todoMapper).selectRecentInventoryTodos(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(24), eq(5));

        doReturn(R.ok("not-a-number")).when(remote)
                .getConfigKey(InvTodoServiceImpl.APPROVAL_URGENT_HOURS_KEY, "inner");
        service.selectTodoList(new TodoQuery(), 10L);
        verify(todoMapper).selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(24));
    }

    @Test
    void batchesTransferShipmentDecorationAndSetsReturnedPermissionActuallyOwned()
    {
        service.permissions.add("inv:transfer:edit");
        TodoItem receive = item(InvTodoTypes.INV_TRANSFER_RECEIVE, 501L, "receive_shipment");
        TodoItem returned = item(InvTodoTypes.INV_TRANSFER_RETURNED, 601L, "revise");
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>(List.of(receive, returned)));
        InvTransferShipment shipment = new InvTransferShipment();
        shipment.setShipmentId(501L);
        shipment.setTransferId(91L);
        when(shipmentMapper.selectByIds(List.of(501L))).thenReturn(List.of(shipment));

        List<TodoItem> result = service.selectTodoList(new TodoQuery(), 10L);

        verify(shipmentMapper).selectByIds(List.of(501L));
        assertThat(result.get(0).getRouteParams()).containsEntry("shipmentId", "501")
                .containsEntry("transferId", "91")
                .containsEntry("contextDeptId", "10");
        assertThat(result.get(1).getRequiredPermission()).isEqualTo("inv:transfer:edit");

        service.permissions.add("inv:transfer:add");
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>(List.of(
                        item(InvTodoTypes.INV_TRANSFER_RETURNED, 602L, "revise"))));
        assertThat(service.selectTodoList(new TodoQuery(), 10L).get(0).getRequiredPermission())
                .isEqualTo("inv:transfer:add");
    }

    @Test
    void transferApprovalTodoDeclaresLegacyApprovalEngine()
    {
        TodoItem transfer = item(InvTodoTypes.INV_TRANSFER_APPROVAL, 701L,
                "approve");
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(),
                anyList(), anyLong(), anyString(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>(List.of(transfer)));

        TodoItem result = service.selectTodoList(new TodoQuery(), 10L).get(0);

        assertThat(result.getRouteParams())
                .containsEntry("approvalEngine", "LEGACY")
                .containsEntry("businessId", "701")
                .containsEntry("contextDeptId", "10");
    }

    @Test
    void duplicateTodoKeyIsRejected()
    {
        when(todoMapper.selectInventoryTodoList(any(), anySet(), anyList(), anyList(),
                anyLong(), anyString(), anyInt(), anyInt())).thenReturn(new ArrayList<>(List.of(
                        item(InvTodoTypes.INV_PURCHASE_QC, 88L, "quality_check"),
                        item(InvTodoTypes.INV_PURCHASE_QC, 88L, "quality_check"))));

        assertThatThrownBy(() -> service.selectTodoList(new TodoQuery(), 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate todoKey");
    }

    @Test
    void foreignProviderSourceDisablesAllInventoryTypes()
    {
        TodoQuery query = new TodoQuery();
        query.setSource(TodoConstants.SOURCE_OA);
        service.selectTodoList(query, 10L);
        ArgumentCaptor<Set<String>> types = ArgumentCaptor.forClass(Set.class);
        verify(todoMapper).selectInventoryTodoList(eq(query), types.capture(), anyList(), anyList(),
                eq(7L), eq("operator"), eq(24), eq(24));
        assertThat(types.getValue()).isEmpty();
    }

    @Test
    void authorizedScopeSqlExpandsRootsToActiveStoresAndWarehouses() throws Exception
    {
        String xml;
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("mapper/inventory/InvDeptScopeMapper.xml"))
        {
            assertThat(stream).isNotNull();
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml).contains(
                "selectUserAuthorizedInventoryDeptIds",
                "selectAllActiveInventoryDeptIds",
                "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                "target_dept.dept_type in ('STORE', 'WAREHOUSE')",
                "target_dept.del_flag = '0'",
                "target_dept.status = '0'");
    }

    private static TodoItem item(String type, Long businessId, String action)
    {
        TodoItem item = new TodoItem();
        item.setSource("inventory");
        item.setType(type);
        item.setBusinessId(businessId);
        item.setRouteType(action);
        item.setDeptId(10L);
        item.setDeptName("一号仓");
        item.setDeptType("WAREHOUSE");
        item.setScopeMode("current_org");
        return item;
    }

    private static InvTodoCountRow count(String type, String category, String priority, long value)
    {
        InvTodoCountRow row = new InvTodoCountRow();
        row.setType(type);
        row.setCategory(category);
        row.setPriority(priority);
        row.setCount(value);
        return row;
    }

    private static final class TestService extends InvTodoServiceImpl
    {
        private final Set<String> permissions = new LinkedHashSet<>();
        private Long userId;
        private String username;
        private boolean admin;
        private boolean pageStarted;

        private TestService(InvTodoMapper todoMapper, InvDeptScopeMapper deptScopeMapper,
                InvTransferShipmentMapper shipmentMapper)
        {
            super(todoMapper, deptScopeMapper, shipmentMapper);
        }

        @Override
        protected Long currentUserId()
        {
            return userId;
        }

        @Override
        protected String currentUsername()
        {
            return username;
        }

        @Override
        protected boolean currentUserIsAdmin()
        {
            return admin;
        }

        @Override
        protected boolean hasPermission(String permission)
        {
            return permissions.contains(permission);
        }

        @Override
        protected void startPage(TodoQuery query)
        {
            pageStarted = true;
        }

        private Set<String> enabledTypes()
        {
            return resolveEnabledTypes();
        }
    }
}
