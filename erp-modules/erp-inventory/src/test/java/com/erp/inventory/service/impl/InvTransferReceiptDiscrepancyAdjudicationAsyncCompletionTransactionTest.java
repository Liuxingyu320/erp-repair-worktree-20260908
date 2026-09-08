package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Child;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationExecutionMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("V2调拨差异裁决异步子调拨权威完成根事务")
class
        InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransactionTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T08:30:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .REQUIRED_PERMISSION;
    private static final String REFERENCE = "return_transfer:9102";
    private static final List<Long> SCOPE = List.of(301L, 302L);

    @Test
    @DisplayName("权威退回终态按固定锁序追加事件并推进三层状态")
    void shouldCompleteAuthoritativeReturnInFixedOrder()
    {
        Fixture fixture = fixture();
        stubNew(fixture, before(), storedLink(), completedChild());

        var result = call(fixture, command(), 99L, Set.of(PERMISSION));

        assertThat(result.replayed()).isFalse();
        assertThat(result.caseStatus()).isEqualTo("resolved");
        assertThat(result.actionStatus()).isEqualTo("completed");
        assertThat(result.effectKind()).isEqualTo(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_RETURN);
        assertThat(result.childTransferId()).isEqualTo(9102L);
        assertThat(result.workflowFingerprint())
                .isEqualTo(storedLink().getWorkflowFingerprint());

        InOrder order = inOrder(fixture.gate(), fixture.scope(),
                fixture.executionMapper(), fixture.workflowMapper());
        order.verify(fixture.gate()).requireEnabled();
        order.verify(fixture.scope()).resolveScopeDeptIds(301L);
        order.verify(fixture.executionMapper())
                .selectCaseIdForUpdate(700L);
        order.verify(fixture.executionMapper())
                .countCaseInScope(700L, SCOPE);
        order.verify(fixture.executionMapper())
                .selectBoundaryForUpdate(700L, 800L);
        order.verify(fixture.executionMapper())
                .selectActionsForUpdate(700L, 800L);
        order.verify(fixture.executionMapper())
                .selectEventByRequestIdForUpdate("complete-action-0001");
        order.verify(fixture.workflowMapper()).selectLinkForUpdate(900L);
        order.verify(fixture.workflowMapper())
                .selectChildForUpdate(900L, 9102L);
        order.verify(fixture.executionMapper())
                .insertExecutionEvent(any());
        order.verify(fixture.executionMapper()).transitionAction(any());
        order.verify(fixture.executionMapper())
                .transitionAdjudication(any());
        order.verify(fixture.executionMapper()).transitionCase(any());
    }

    @Test
    @DisplayName("权威补发终态复用同一根事务但保持效果类型隔离")
    void shouldCompleteAuthoritativeReshipWithoutEffectDrift()
    {
        Fixture fixture = fixture();
        var link = storedLink(preparedReshipLink());
        stubNew(fixture, reshipBefore(), link, completedReshipChild());

        var result = call(fixture, reshipCommand(), 99L,
                Set.of(PERMISSION));

        assertThat(result.replayed()).isFalse();
        assertThat(result.effectKind()).isEqualTo(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_RESHIP);
        assertThat(result.effectReference())
                .isEqualTo("reship_transfer:9101");
        assertThat(result.childTransferId()).isEqualTo(9101L);
        verify(fixture.executionMapper()).insertExecutionEvent(any());
        verify(fixture.executionMapper()).transitionAction(any());
    }

    @Test
    @DisplayName("完全一致请求重验工作流终态后只读重放")
    void shouldReplayOnlyAfterRevalidatingCompletion()
    {
        Fixture fixture = fixture();
        Data before = before();
        PreparedExecution prepared = prepared(before);
        stubLocks(fixture, resulting(prepared, before));
        when(fixture.executionMapper().selectEventByRequestIdForUpdate(
                "complete-action-0001")).thenReturn(stored(prepared));
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(storedLink());
        when(fixture.workflowMapper().selectChildForUpdate(900L, 9102L))
                .thenReturn(completedChild());

        var result = call(fixture, command(), 99L, Set.of(PERMISSION));

        assertThat(result.replayed()).isTrue();
        assertThat(result.eventFingerprint())
                .isEqualTo(prepared.eventFingerprint());
        verify(fixture.workflowMapper())
                .selectChildForUpdate(900L, 9102L);
        verify(fixture.executionMapper(), never())
                .insertExecutionEvent(any());
        verify(fixture.executionMapper(), never()).transitionAction(any());
        verify(fixture.executionMapper(), never())
                .transitionAdjudication(any());
        verify(fixture.executionMapper(), never()).transitionCase(any());
    }

    @Test
    @DisplayName("子调拨未形成全量收货或零开放差异时首个事件前失败")
    void shouldRejectIncompleteChildBeforeFirstWrite()
    {
        Fixture fixture = fixture();
        var incomplete = completedChild();
        incomplete.setOpenDiscrepancyCount(1);
        stubNew(fixture, before(), storedLink(), incomplete);

        assertThatThrownBy(() -> call(fixture, command(), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("尚未形成权威完成事实");

        verify(fixture.executionMapper(), never())
                .insertExecutionEvent(any());
        verify(fixture.executionMapper(), never()).transitionAction(any());
    }

    @Test
    @DisplayName("Gate和权限分别在组织解析与数据库锁之前失败关闭")
    void shouldFailGateAndPermissionBeforeReads()
    {
        Fixture closed = fixture();
        doThrow(new ServiceException("closed"))
                .when(closed.gate()).requireEnabled();

        assertThatThrownBy(() -> closed.transaction().complete(command(),
                301L)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("closed");
        verifyNoInteractions(closed.scope(), closed.executionMapper(),
                closed.workflowMapper());

        Fixture denied = fixture();
        assertThatThrownBy(() -> call(denied, command(), 99L, Set.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权限无效");
        verifyNoInteractions(denied.scope(), denied.executionMapper(),
                denied.workflowMapper());
    }

    @Test
    @DisplayName("非完成命令和工作流关系漂移均失败关闭")
    void shouldRejectWrongCommandAndWorkflowDrift()
    {
        Fixture wrongCommand = fixture();
        var dispatch = new
                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                        "complete-action-0001", 700L, 5L, 800L, 900L,
                        1L, "dispatch", REFERENCE);
        assertThatThrownBy(() -> call(wrongCommand, dispatch, 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("完成请求无效");
        verifyNoInteractions(wrongCommand.scope(),
                wrongCommand.executionMapper(),
                wrongCommand.workflowMapper());

        Fixture drift = fixture();
        var link = storedLink();
        link.setEffectReference("return_transfer:9999");
        stubNew(drift, before(), link, completedChild());
        assertThatThrownBy(() -> call(drift, command(), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class);
        verify(drift.executionMapper(), never())
                .insertExecutionEvent(any());
    }

    @Test
    @DisplayName("任一条件状态写冲突要求根事务回滚")
    void shouldRollbackConditionalWriteConflict()
    {
        Fixture fixture = fixture();
        stubNew(fixture, before(), storedLink(), completedChild());
        when(fixture.executionMapper().transitionAction(any()))
                .thenReturn(0);

        assertThatThrownBy(() -> call(fixture, command(), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("动作状态推进冲突")
                .hasMessageContaining("已回滚");
        verify(fixture.executionMapper(), never())
                .transitionAdjudication(any());
        verify(fixture.executionMapper(), never()).transitionCase(any());
    }

    @Test
    @DisplayName("根事务拥有回滚边界且生产主代码零调用")
    void shouldOwnRollbackAndRemainUnwired() throws Exception
    {
        Method method =
                InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction
                        .class.getMethod("complete",
                                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                                        .class,
                                Long.class);
        Transactional transaction = method.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(List.of(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences()).containsExactly(
                Path.of("com", "erp", "inventory", "service", "impl",
                        "InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction.java"));
    }

    private static Fixture fixture()
    {
        var gate = mock(
                InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                        .class);
        var execution = mock(
                InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                        .class);
        var workflow = mock(
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                        .class);
        var scope = mock(ShopScopeService.class);
        when(scope.resolveScopeDeptIds(301L)).thenReturn(SCOPE);
        var transaction = new
                InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction(
                        gate, execution, workflow, scope,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(transaction, gate, execution, workflow, scope);
    }

    private static void stubNew(Fixture fixture, Data data,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink link,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                    child)
    {
        stubLocks(fixture, data);
        when(fixture.workflowMapper().selectLinkForUpdate(900L))
                .thenReturn(link);
        when(fixture.workflowMapper().selectChildForUpdate(900L,
                link.getChildTransferId())).thenReturn(child);
        when(fixture.executionMapper().insertExecutionEvent(any()))
                .thenReturn(1);
        when(fixture.executionMapper().transitionAction(any()))
                .thenReturn(1);
        when(fixture.executionMapper().transitionAdjudication(any()))
                .thenReturn(1);
        when(fixture.executionMapper().transitionCase(any()))
                .thenReturn(1);
    }

    private static void stubLocks(Fixture fixture, Data data)
    {
        when(fixture.executionMapper().selectCaseIdForUpdate(700L))
                .thenReturn(700L);
        when(fixture.executionMapper().countCaseInScope(700L, SCOPE))
                .thenReturn(1);
        when(fixture.executionMapper().selectBoundaryForUpdate(700L, 800L))
                .thenReturn(data.boundary());
        when(fixture.executionMapper().selectActionsForUpdate(700L, 800L))
                .thenReturn(data.actions());
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction
                    .Result call(Fixture fixture,
                            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                                    command,
                            Long userId, Set<String> permissions)
    {
        LoginUser login = new LoginUser();
        login.setUserid(userId);
        login.setUsername("completion-user");
        login.setPermissions(permissions);
        try (var security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(login);
            security.when(SecurityUtils::getUserId).thenReturn(userId);
            security.when(SecurityUtils::getUsername)
                    .thenReturn("completion-user");
            return fixture.transaction().complete(command, 301L);
        }
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
            command()
    {
        return new
                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                        "complete-action-0001", 700L, 5L, 800L, 900L,
                        1L,
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .COMMAND_COMPLETE,
                        REFERENCE);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
            reshipCommand()
    {
        return new
                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                        "complete-reship-0001", 700L, 5L, 800L, 900L,
                        1L,
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .COMMAND_COMPLETE,
                        "reship_transfer:9101");
    }

    private static Data before()
    {
        var boundary = boundary(5L, "adjudication_executing");
        return new Data(boundary, List.of(action("in_progress", 1L,
                REFERENCE)));
    }

    private static Data reshipBefore()
    {
        var boundary = boundary("shortage", 5L,
                "adjudication_executing");
        return new Data(boundary, List.of(action("reship", "in_progress",
                1L, "reship_transfer:9101")));
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary
            boundary(Long version, String status)
    {
        return boundary("damaged", version, status);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary
            boundary(String discrepancyType, Long version, String status)
    {
        var value =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary();
        value.setCaseId(700L);
        value.setCaseVersionAtPlan(4L);
        value.setCaseVersion(version);
        value.setCaseStatus(status);
        value.setAdjudicationId(800L);
        value.setDecisionFingerprint("a".repeat(64));
        value.setDiscrepancyType(discrepancyType);
        value.setDiscrepancyQuantity(new BigDecimal("1.0000"));
        value.setSourceCostPrice(new BigDecimal("10.000000"));
        value.setDiscrepancyAmount(new BigDecimal("10.000000"));
        value.setPlanStatus(status);
        return value;
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction
                    action(String status, Long version, String reference)
    {
        return action("return_to_source", status, version, reference);
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction
                    action(String actionType, String status, Long version,
                            String reference)
    {
        var value = new
                InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction();
        value.setActionId(900L);
        value.setAdjudicationId(800L);
        value.setCaseId(700L);
        value.setSequence(1);
        value.setActionType(actionType);
        value.setCoverageKind("resolution");
        value.setQuantity(new BigDecimal("1.0000"));
        value.setAmount(new BigDecimal("10.000000"));
        value.setResponsibleParty("company");
        value.setExecutionStatus(status);
        value.setExecutionVersion(version);
        value.setEffectReference(reference);
        return value;
    }

    private static PreparedExecution prepared(Data data)
    {
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(new Request("complete-action-0001", 700L, 5L,
                                800L, 900L, 1L, "complete", REFERENCE),
                        data.boundary().toPolicyBoundary(data.actions()),
                        new Actor(99L, "completion-user",
                                Set.of(PERMISSION)), NOW);
    }

    private static Data resulting(PreparedExecution prepared, Data before)
    {
        var boundary = boundary(prepared.caseVersionAfter(),
                prepared.caseStatusAfter());
        var current = prepared.resultingActions().get(0);
        return new Data(boundary, List.of(action(
                current.executionStatus(), current.executionVersion(),
                current.effectReference())));
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
            stored(PreparedExecution source)
    {
        var value = new
                InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent();
        value.setEventId(1001L);
        value.setRequestId(source.requestId());
        value.setCaseId(source.caseId());
        value.setCaseVersionBefore(source.caseVersionBefore());
        value.setCaseVersionAfter(source.caseVersionAfter());
        value.setCaseStatusBefore(source.caseStatusBefore());
        value.setCaseStatusAfter(source.caseStatusAfter());
        value.setAdjudicationId(source.adjudicationId());
        value.setDecisionFingerprint(source.decisionFingerprint());
        value.setPlanStatusBefore(source.planStatusBefore());
        value.setPlanStatusAfter(source.planStatusAfter());
        value.setActionId(source.actionId());
        value.setActionSequence(source.actionSequence());
        value.setActionType(source.actionType());
        value.setCoverageKind(source.coverageKind());
        value.setDiscrepancyType(source.discrepancyType());
        value.setEffectKind(source.effectKind());
        value.setCommand(source.command());
        value.setEffectReference(source.effectReference());
        value.setActionStatusBefore(source.actionStatusBefore());
        value.setActionStatusAfter(source.actionStatusAfter());
        value.setExecutionVersionBefore(source.executionVersionBefore());
        value.setExecutionVersionAfter(source.executionVersionAfter());
        value.setQuantity(source.quantity());
        value.setSourceCostPrice(source.sourceCostPrice());
        value.setAmount(source.amount());
        value.setResponsibleParty(source.responsibleParty());
        value.setRequiredPermission(source.requiredPermission());
        value.setExecutorUserId(source.executorUserId());
        value.setExecutorName(source.executorName());
        value.setCreateTime(Date.from(source.executedAt()));
        value.setEventFingerprint(source.eventFingerprint());
        return value;
    }

    private static PreparedLink preparedLink()
    {
        Source source = new Source("workflow-dispatch-0001", 700L, 4L,
                800L, 900L, 0L, "damaged", "return_to_source", 600L,
                "warehouse", 301L, 302L, 5001L, 5002L, "product",
                4001L, 4001L, "serial", 7001L, 7002L, 7003L,
                new BigDecimal("1.0000"), new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "a".repeat(64), 99L,
                "dispatch-user", NOW);
        Child child = new Child(9102L, "store_return",
                "transfer_discrepancy_return", 900L, 302L, 301L,
                "submitted", 1, "product", 4001L, 4001L,
                new BigDecimal("1.0000"), BigDecimal.ZERO,
                "quarantine_detail", 1, new BigDecimal("1.0000"),
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .ACTIVE,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0);
        return InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .prepare(source, child);
    }

    private static PreparedLink preparedReshipLink()
    {
        Source source = new Source("workflow-reship-0001", 700L, 4L,
                800L, 900L, 0L, "shortage", "reship", 600L,
                "warehouse", 301L, 302L, 5001L, 5002L, "product",
                4001L, 4001L, "serial", null, null, null,
                new BigDecimal("1.0000"), new BigDecimal("10.000000"),
                new BigDecimal("10.000000"), "a".repeat(64), 99L,
                "dispatch-user", NOW);
        Child child = new Child(9101L, "warehouse",
                "transfer_discrepancy_reship", 900L, 301L, 302L,
                "submitted", 1, "product", 4001L, 4001L,
                new BigDecimal("1.0000"), BigDecimal.ZERO,
                "available_stock", 1, new BigDecimal("1.0000"),
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .ACTIVE,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0);
        return InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                .prepare(source, child);
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
                    storedLink()
    {
        return storedLink(preparedLink());
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink
                    storedLink(PreparedLink source)
    {
        var value = new
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink();
        value.setWorkflowId(1L);
        value.setRequestId(source.requestId());
        value.setCaseId(source.caseId());
        value.setCaseVersionBefore(source.caseVersionBefore());
        value.setAdjudicationId(source.adjudicationId());
        value.setActionId(source.actionId());
        value.setActionVersionBefore(source.actionVersionBefore());
        value.setDiscrepancyType(source.discrepancyType());
        value.setActionType(source.actionType());
        value.setEffectKind(source.effectKind());
        value.setWorkflowType(source.workflowType());
        value.setParentTransferId(source.parentTransferId());
        value.setParentTransferType(source.parentTransferType());
        value.setChildTransferId(source.childTransferId());
        value.setChildTransferType(source.childTransferType());
        value.setOriginalSourceLocationDeptId(
                source.originalSourceLocationDeptId());
        value.setOriginalTargetLocationDeptId(
                source.originalTargetLocationDeptId());
        value.setChildSourceLocationDeptId(
                source.childSourceLocationDeptId());
        value.setChildTargetLocationDeptId(
                source.childTargetLocationDeptId());
        value.setReceiptAllocationId(source.receiptAllocationId());
        value.setShipmentAllocationId(source.shipmentAllocationId());
        value.setItemType(source.itemType());
        value.setItemId(source.itemId());
        value.setProductId(source.productId());
        value.setTrackingPolicy(source.trackingPolicy());
        value.setInventorySource(source.inventorySource());
        value.setQuarantineBalanceId(source.quarantineBalanceId());
        value.setQuarantineLotId(source.quarantineLotId());
        value.setQuarantineLocationId(source.quarantineLocationId());
        value.setQuantity(source.quantity());
        value.setSourceCostPrice(source.sourceCostPrice());
        value.setAmount(source.amount());
        value.setSourceBusinessType(source.sourceBusinessType());
        value.setSourceBusinessId(source.sourceBusinessId());
        value.setEffectReference(source.effectReference());
        value.setDispatchChildStatus(source.dispatchChildStatus());
        value.setReservationCount(source.reservationCount());
        value.setReservedQuantity(source.reservedQuantity());
        value.setDecisionFingerprint(source.decisionFingerprint());
        value.setWorkflowFingerprint(source.workflowFingerprint());
        value.setExecutorUserId(source.executorUserId());
        value.setExecutorName(source.executorName());
        value.setCreateTime(Date.from(source.createdAt()));
        return value;
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                    completedReshipChild()
    {
        var value = completedChild();
        value.setTransferId(9101L);
        value.setTransferType("warehouse");
        value.setSourceBusinessType("transfer_discrepancy_reship");
        value.setSourceLocationDeptId(301L);
        value.setTargetLocationDeptId(302L);
        value.setReservationKind("available_stock");
        return value;
    }

    private static
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                    completedChild()
    {
        var value = new
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact();
        value.setTransferId(9102L);
        value.setTransferType("store_return");
        value.setSourceBusinessType("transfer_discrepancy_return");
        value.setSourceBusinessId(900L);
        value.setSourceLocationDeptId(302L);
        value.setTargetLocationDeptId(301L);
        value.setStatus("received");
        value.setDetailCount(1);
        value.setItemType("product");
        value.setItemId(4001L);
        value.setProductId(4001L);
        value.setRequestedQuantity(new BigDecimal("1.0000"));
        value.setDeliveredQuantity(new BigDecimal("1.0000"));
        value.setReservationKind("quarantine_detail");
        value.setReservationCount(1);
        value.setReservedQuantity(new BigDecimal("1.0000"));
        value.setReservationStatus(
                InvTransferReceiptDiscrepancyReturnReservationLifecyclePolicy
                        .CONSUMED);
        value.setConsumedQuantity(new BigDecimal("1.0000"));
        value.setReleasedQuantity(BigDecimal.ZERO);
        value.setV2ShipmentCount(1);
        value.setLegacyShipmentCount(0);
        value.setReceiptCount(1);
        value.setOpenDiscrepancyCount(0);
        return value;
    }

    private static List<Path> productionReferences() throws Exception
    {
        Path root = Path.of("src", "main", "java");
        String type =
                "InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction";
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try
                        {
                            return Files.readString(path).contains(type);
                        }
                        catch (Exception exception)
                        {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction
                    transaction,
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                    gate,
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                    executionMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            ShopScopeService scope)
    {
    }

    private record Data(
            InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary
                    boundary,
            List<InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction>
                    actions)
    {
    }
}
