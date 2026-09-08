package com.erp.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import com.erp.approval.domain.vo.ApprovalTodoCountRow;
import com.erp.approval.domain.vo.ApprovalTodoAccessRule;
import com.erp.approval.domain.vo.ApprovalTodoRow;
import com.erp.approval.mapper.ApprovalCandidateDirectoryMapper;
import com.erp.approval.mapper.ApprovalRuntimeMapper;
import com.erp.common.core.domain.todo.TodoQuery;
import com.erp.common.core.domain.todo.TodoSummary;
import com.erp.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("统一审批待办摘要")
class ApprovalTodoServiceTest
{
    private ApprovalRuntimeMapper runtimeMapper;
    private TestApprovalTodoService service;
    private ApprovalPermissionPolicy permissions;

    @BeforeEach
    void setUp()
    {
        runtimeMapper = mock(ApprovalRuntimeMapper.class);
        permissions = mock(ApprovalPermissionPolicy.class);
        ApprovalCandidateDirectoryMapper directory =
                mock(ApprovalCandidateDirectoryMapper.class);
        when(permissions.requiredPermission("OA_PURCHASE"))
                .thenReturn("oa:todo:approve");
        when(permissions.candidatePermissions("OA_PURCHASE"))
                .thenReturn(Set.of("oa:todo:approve"));
        when(permissions.permissionForCandidate("OA_PURCHASE", null))
                .thenReturn("oa:todo:approve");
        when(permissions.supportedBusinessCodes())
                .thenReturn(Set.of("OA_PURCHASE"));
        service = new TestApprovalTodoService(runtimeMapper, permissions,
                directory);
        service.sessionPermissions.add("oa:todo:approve");
        service.activePermissions.add("oa:todo:approve");
    }

    @Test
    @DisplayName("摘要在数据库聚合计数且只读取最近五条")
    void shouldAggregateWithoutLoadingTheCompleteTodoList()
    {
        TodoQuery query = new TodoQuery();
        when(runtimeMapper.selectTodoTypeCounts(eq(9L), eq(query), any()))
                .thenReturn(
                List.of(count("OA_PURCHASE_APPROVAL", 120_000L),
                        count("INV_TRANSFER_APPROVAL", 80_000L)));
        when(runtimeMapper.selectRecentTodoRows(eq(9L), eq(query), any()))
                .thenReturn(List.of(row(1L), row(2L)));

        TodoSummary summary = service.summary(query, 9L);

        assertThat(summary.getTotal()).isEqualTo(200_000L);
        assertThat(summary.getApproval()).isEqualTo(200_000L);
        assertThat(summary.getNormal()).isEqualTo(200_000L);
        assertThat(summary.getTypeCounts()).containsEntry(
                "OA_PURCHASE_APPROVAL", 120_000L);
        assertThat(summary.getRecent()).hasSize(2);
        verify(runtimeMapper, never()).selectTodoRows(any(), any(), any());
        ArgumentCaptor<List<ApprovalTodoAccessRule>> rules =
                accessRulesCaptor();
        verify(runtimeMapper).selectTodoTypeCounts(eq(9L), eq(query),
                rules.capture());
        assertThat(rules.getValue()).singleElement().satisfies(rule -> {
            assertThat(rule.getBusinessCode()).isEqualTo("OA_PURCHASE");
            assertThat(rule.isBasePermissionAllowed()).isTrue();
            assertThat(rule.getSpecialCandidatePermissions()).isEmpty();
        });
    }

    @Test
    @DisplayName("缺少用户时不访问待办数据")
    void shouldFailBeforeQueryWhenUserIsMissing()
    {
        assertThatThrownBy(() -> service.summary(new TodoQuery(), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前用户");
        verifyNoInteractions(runtimeMapper);
    }

    @Test
    @DisplayName("当前业务权限撤销后列表与摘要立即排除候选任务")
    void shouldExcludeCandidatesAfterCurrentPermissionIsRevoked()
    {
        TodoQuery query = new TodoQuery();
        service.activePermissions.clear();
        when(runtimeMapper.selectTodoRows(eq(9L), eq(query), eq(List.of())))
                .thenReturn(List.of());
        when(runtimeMapper.selectTodoTypeCounts(eq(9L), eq(query),
                eq(List.of()))).thenReturn(List.of());
        when(runtimeMapper.selectRecentTodoRows(eq(9L), eq(query),
                eq(List.of()))).thenReturn(List.of());

        assertThat(service.list(query, 9L)).isEmpty();
        assertThat(service.summary(query, 9L).getTotal()).isZero();
        verify(runtimeMapper).selectTodoRows(9L, query, List.of());
        verify(runtimeMapper).selectTodoTypeCounts(9L, query, List.of());
    }

    @Test
    @DisplayName("原生调拨待办显式携带审批引擎和精确任务")
    void shouldDeclareNativeEngineForTransferTodo()
    {
        when(permissions.requiredPermission("INV_TRANSFER"))
                .thenReturn("inv:transfer:approve");
        when(permissions.candidatePermissions("INV_TRANSFER"))
                .thenReturn(Set.of("inv:transfer:approve"));
        when(permissions.permissionForCandidate("INV_TRANSFER", null))
                .thenReturn("inv:transfer:approve");
        when(permissions.supportedBusinessCodes())
                .thenReturn(Set.of("INV_TRANSFER"));
        service.sessionPermissions.clear();
        service.activePermissions.clear();
        service.sessionPermissions.add("inv:transfer:approve");
        service.activePermissions.add("inv:transfer:approve");

        TodoQuery query = new TodoQuery();
        ApprovalTodoRow transfer = row(41L);
        transfer.setBusinessCode("INV_TRANSFER");
        transfer.setBusinessId("701");
        when(runtimeMapper.selectTodoRows(eq(9L), eq(query), any()))
                .thenReturn(List.of(transfer));

        var item = service.list(query, 9L).get(0);

        assertThat(item.getType()).isEqualTo("INV_TRANSFER_APPROVAL");
        assertThat(item.getRouteParams())
                .containsEntry("approvalEngine", "NATIVE")
                .containsEntry("approvalTaskId", "41")
                .containsEntry("approvalInstanceId", "141");
    }

    @Test
    @DisplayName("财务专用权限可独立看到报销财务节点且待办携带准确权限")
    void shouldExposeFinanceTodoWithDedicatedPermissionOnly()
    {
        when(permissions.supportedBusinessCodes())
                .thenReturn(Set.of("OA_REIMBURSEMENT"));
        when(permissions.requiredPermission("OA_REIMBURSEMENT"))
                .thenReturn("oa:reimbursement:approve");
        when(permissions.candidatePermissions("OA_REIMBURSEMENT"))
                .thenReturn(Set.of("oa:reimbursement:approve",
                        "oa:reimbursement:finance:approve"));
        when(permissions.permissionForCandidate("OA_REIMBURSEMENT",
                "oa:reimbursement:finance:approve"))
                .thenReturn("oa:reimbursement:finance:approve");
        service.sessionPermissions.clear();
        service.activePermissions.clear();
        service.sessionPermissions.add(
                "oa:reimbursement:finance:approve");
        service.activePermissions.add(
                "oa:reimbursement:finance:approve");

        TodoQuery query = new TodoQuery();
        ApprovalTodoRow financeRow = row(3L);
        financeRow.setBusinessCode("OA_REIMBURSEMENT");
        financeRow.setCandidateSourceCode(
                "oa:reimbursement:finance:approve");
        when(runtimeMapper.selectTodoRows(eq(9L), eq(query), any()))
                .thenReturn(List.of(financeRow));

        List<com.erp.common.core.domain.todo.TodoItem> items =
                service.list(query, 9L);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getType())
                    .isEqualTo("OA_REIMBURSEMENT_APPROVAL");
            assertThat(item.getRequiredPermission())
                    .isEqualTo("oa:reimbursement:finance:approve");
        });
        ArgumentCaptor<List<ApprovalTodoAccessRule>> rules =
                accessRulesCaptor();
        verify(runtimeMapper).selectTodoRows(eq(9L), eq(query),
                rules.capture());
        assertThat(rules.getValue()).singleElement().satisfies(rule -> {
            assertThat(rule.isBasePermissionAllowed()).isFalse();
            assertThat(rule.getSpecialCandidatePermissions())
                    .containsExactly("oa:reimbursement:finance:approve");
            assertThat(rule.getAllowedSpecialCandidatePermissions())
                    .containsExactly("oa:reimbursement:finance:approve");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<ApprovalTodoAccessRule>>
            accessRulesCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private static ApprovalTodoCountRow count(String type, long value)
    {
        ApprovalTodoCountRow row = new ApprovalTodoCountRow();
        row.setTodoType(type);
        row.setTodoCount(value);
        return row;
    }

    private static ApprovalTodoRow row(long taskId)
    {
        ApprovalTodoRow row = new ApprovalTodoRow();
        row.setTaskId(taskId);
        row.setInstanceId(taskId + 100L);
        row.setBusinessCode("OA_PURCHASE");
        row.setBusinessSource("oa");
        row.setBusinessId(String.valueOf(taskId));
        row.setNodeName("门店负责人");
        row.setApplicantName("申请人");
        row.setCreatedTime(new Date());
        return row;
    }

    private static class TestApprovalTodoService extends ApprovalTodoService
    {
        private final Set<String> sessionPermissions = new LinkedHashSet<>();
        private final Set<String> activePermissions = new LinkedHashSet<>();

        TestApprovalTodoService(ApprovalRuntimeMapper runtimeMapper,
                ApprovalPermissionPolicy permissionPolicy,
                ApprovalCandidateDirectoryMapper directoryMapper)
        {
            super(runtimeMapper, permissionPolicy, directoryMapper);
        }

        @Override
        protected boolean hasPermission(String permission)
        {
            return sessionPermissions.contains(permission);
        }

        @Override
        protected boolean hasActiveAssignment(Long userId, String permission)
        {
            return activePermissions.contains(permission);
        }
    }
}
