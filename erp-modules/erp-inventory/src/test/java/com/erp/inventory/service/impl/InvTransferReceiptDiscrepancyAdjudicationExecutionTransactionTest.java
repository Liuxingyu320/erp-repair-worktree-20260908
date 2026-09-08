package com.erp.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationExecutionMapper;
import com.erp.system.api.model.LoginUser;

@DisplayName("V2调拨差异裁决同步效果唯一执行事务")
class InvTransferReceiptDiscrepancyAdjudicationExecutionTransactionTest
{
    private static final Instant NOW = Instant.parse(
            "2026-08-03T05:00:00Z");
    private static final String PERMISSION =
            InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .REQUIRED_PERMISSION;
    private static final List<Long> SCOPE = List.of(301L, 302L);

    @Test
    @DisplayName("责任台账严格执行锁定事件效果和三层状态顺序")
    void shouldExecuteResponsibilityInFixedOrder()
    {
        Fixture fixture = fixture();
        Data data = data("shortage", "responsibility_adjustment");
        stubNew(fixture, data);

        var result = call(fixture, command(null), 99L,
                Set.of(PERMISSION));

        assertThat(result.replayed()).isFalse();
        assertThat(result.caseStatus()).isEqualTo("resolved");
        assertThat(result.effectKind()).isEqualTo(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_RESPONSIBILITY);
        InOrder order = inOrder(fixture.scope(), fixture.mapper(),
                fixture.atomicLedgerOwner());
        order.verify(fixture.scope()).resolveScopeDeptIds(301L);
        order.verify(fixture.mapper()).selectCaseIdForUpdate(700L);
        order.verify(fixture.mapper()).countCaseInScope(700L, SCOPE);
        order.verify(fixture.mapper()).selectBoundaryForUpdate(700L, 800L);
        order.verify(fixture.mapper()).selectActionsForUpdate(700L, 800L);
        order.verify(fixture.mapper()).selectEventByRequestIdForUpdate(
                "execute-action-0001");
        order.verify(fixture.mapper()).insertExecutionEvent(any());
        order.verify(fixture.atomicLedgerOwner()).apply(any());
        order.verify(fixture.mapper()).transitionAction(any());
        order.verify(fixture.mapper()).transitionAdjudication(any());
        order.verify(fixture.mapper()).transitionCase(any());
        verifyNoInteractions(fixture.damageWriteOffOwner());
    }

    @Test
    @DisplayName("短缺损失和受损写销分别路由到唯一同步效果所有者")
    void shouldRouteEachSupportedSynchronousEffect()
    {
        Fixture loss = fixture();
        Data lossData = data("shortage", "transport_loss_write_off");
        stubNew(loss, lossData);
        assertThat(call(loss, command(null), 99L, Set.of(PERMISSION))
                .effectKind()).isEqualTo(
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .EFFECT_SHORTAGE_LOSS);
        verify(loss.atomicLedgerOwner()).apply(any());
        verifyNoInteractions(loss.damageWriteOffOwner());

        Fixture damage = fixture();
        Data damageData = data("damaged", "damage_write_off");
        stubNew(damage, damageData);
        assertThat(call(damage, command(null), 99L, Set.of(PERMISSION))
                .effectKind()).isEqualTo(
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .EFFECT_QUARANTINE_WRITE_OFF);
        verify(damage.damageWriteOffOwner()).apply(any());
        verifyNoInteractions(damage.atomicLedgerOwner());
    }

    @Test
    @DisplayName("补发工作流在首个事件或效果DML前失败关闭")
    void shouldRejectAsyncWorkflowBeforeAnyWrite()
    {
        Fixture fixture = fixture();
        Data data = data("shortage", "reship");
        stubNew(fixture, data);

        assertThatThrownBy(() -> call(fixture,
                command("reship_transfer:123"), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("补发或退回");

        verify(fixture.mapper(), never()).insertExecutionEvent(any());
        verify(fixture.mapper(), never()).transitionAction(any());
        verifyNoInteractions(fixture.atomicLedgerOwner(),
                fixture.damageWriteOffOwner());
    }

    @Test
    @DisplayName("完全一致请求只读重放原事件且不重复任何写入")
    void shouldReplayExactStoredEventWithoutWrites()
    {
        Fixture fixture = fixture();
        Data before = data("shortage", "responsibility_adjustment");
        PreparedExecution prepared = prepared(before, command(null));
        Data current = resulting(prepared, before);
        stubLocks(fixture, current);
        when(fixture.mapper().selectEventByRequestIdForUpdate(
                "execute-action-0001")).thenReturn(stored(prepared));

        var result = call(fixture, command(null), 99L,
                Set.of(PERMISSION));

        assertThat(result.replayed()).isTrue();
        assertThat(result.eventFingerprint()).isEqualTo(
                prepared.eventFingerprint());
        verify(fixture.mapper(), never()).insertExecutionEvent(any());
        verify(fixture.mapper(), never()).transitionAction(any());
        verify(fixture.mapper(), never()).transitionAdjudication(any());
        verify(fixture.mapper(), never()).transitionCase(any());
        verifyNoInteractions(fixture.atomicLedgerOwner(),
                fixture.damageWriteOffOwner());
    }

    @Test
    @DisplayName("重放操作者或覆盖维度漂移时失败关闭")
    void shouldRejectTamperedReplay()
    {
        Data before = data("shortage", "responsibility_adjustment");
        PreparedExecution prepared = prepared(before, command(null));

        Fixture actorDrift = fixture();
        stubLocks(actorDrift, resulting(prepared, before));
        when(actorDrift.mapper().selectEventByRequestIdForUpdate(
                "execute-action-0001")).thenReturn(stored(prepared));
        assertThatThrownBy(() -> call(actorDrift, command(null), 100L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("幂等标识");

        Fixture coverageDrift = fixture();
        stubLocks(coverageDrift, resulting(prepared, before));
        InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent event =
                stored(prepared);
        event.setCoverageKind("responsibility");
        when(coverageDrift.mapper().selectEventByRequestIdForUpdate(
                "execute-action-0001")).thenReturn(event);
        assertThatThrownBy(() -> call(coverageDrift, command(null), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重放事实");
    }

    @Test
    @DisplayName("权限在组织解析和数据库锁之前校验")
    void shouldRejectPermissionBeforeScopeOrLocks()
    {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> call(fixture, command(null), 99L,
                Set.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("权限无效");

        verifyNoInteractions(fixture.scope(), fixture.mapper(),
                fixture.atomicLedgerOwner(), fixture.damageWriteOffOwner());

        Fixture invalid = fixture();
        var invalidCommand =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                        "execute-action-0001", 700L, 4L, 800L, 900L,
                        0L, "finish", null);
        assertThatThrownBy(() -> call(invalid, invalidCommand, 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行请求无效");
        verifyNoInteractions(invalid.scope(), invalid.mapper(),
                invalid.atomicLedgerOwner(),
                invalid.damageWriteOffOwner());
    }

    @Test
    @DisplayName("组织越权和任一非单行状态DML全部失败关闭")
    void shouldRejectScopeAndConditionalWriteConflicts()
    {
        Fixture scopeDenied = fixture();
        when(scopeDenied.mapper().selectCaseIdForUpdate(700L))
                .thenReturn(700L);
        assertThatThrownBy(() -> call(scopeDenied, command(null), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权执行");
        verify(scopeDenied.mapper(), never())
                .selectBoundaryForUpdate(700L, 800L);

        Fixture conflict = fixture();
        Data data = data("shortage", "responsibility_adjustment");
        stubNew(conflict, data);
        when(conflict.mapper().transitionAction(any())).thenReturn(0);
        assertThatThrownBy(() -> call(conflict, command(null), 99L,
                Set.of(PERMISSION)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("动作状态推进冲突")
                .hasMessageContaining("已回滚");
        verify(conflict.atomicLedgerOwner()).apply(any());
        verify(conflict.mapper(), never()).transitionAdjudication(any());
        verify(conflict.mapper(), never()).transitionCase(any());
    }

    @Test
    @DisplayName("外层拥有回滚事务且没有生产调用点")
    void shouldOwnRollbackTransactionAndRemainUnwired() throws Exception
    {
        Method execute =
                InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction
                        .class.getMethod("execute",
                                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                                        .class,
                                Long.class);
        Transactional transaction = execute.getAnnotation(
                Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(List.of(transaction.rollbackFor()))
                .contains(Exception.class);
        assertThat(productionReferences()).containsExactly(
                Path.of("service", "impl",
                        "InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction.java"));
    }

    private static Fixture fixture()
    {
        var mapper = mock(
                InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                        .class);
        var atomic = mock(
                InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                        .class);
        var damage = mock(
                InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner.class);
        var scope = mock(ShopScopeService.class);
        when(scope.resolveScopeDeptIds(301L)).thenReturn(SCOPE);
        var service =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction(
                        mapper, atomic, damage, scope,
                        Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, mapper, atomic, damage, scope);
    }

    private static void stubNew(Fixture fixture, Data data)
    {
        stubLocks(fixture, data);
        when(fixture.mapper().insertExecutionEvent(any())).thenReturn(1);
        when(fixture.mapper().transitionAction(any())).thenReturn(1);
        when(fixture.mapper().transitionAdjudication(any())).thenReturn(1);
        when(fixture.mapper().transitionCase(any())).thenReturn(1);
    }

    private static void stubLocks(Fixture fixture, Data data)
    {
        when(fixture.mapper().selectCaseIdForUpdate(700L))
                .thenReturn(700L);
        when(fixture.mapper().countCaseInScope(700L, SCOPE))
                .thenReturn(1);
        when(fixture.mapper().selectBoundaryForUpdate(700L, 800L))
                .thenReturn(data.boundary());
        when(fixture.mapper().selectActionsForUpdate(700L, 800L))
                .thenReturn(data.actions());
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction
            .Result call(Fixture fixture,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                            command,
                    Long userId, Set<String> permissions)
    {
        LoginUser login = new LoginUser();
        login.setUserid(userId);
        login.setUsername("execution-user");
        login.setPermissions(permissions);
        try (var security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getLoginUser).thenReturn(login);
            security.when(SecurityUtils::getUserId).thenReturn(userId);
            security.when(SecurityUtils::getUsername)
                    .thenReturn("execution-user");
            return fixture.transaction().execute(command, 301L);
        }
    }

    private static Data data(String discrepancyType, String actionType)
    {
        var boundary =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary();
        boundary.setCaseId(700L);
        boundary.setCaseVersionAtPlan(4L);
        boundary.setCaseVersion(4L);
        boundary.setCaseStatus("adjudication_planned");
        boundary.setAdjudicationId(800L);
        boundary.setDecisionFingerprint("a".repeat(64));
        boundary.setDiscrepancyType(discrepancyType);
        boundary.setDiscrepancyQuantity(new BigDecimal("1.0000"));
        boundary.setSourceCostPrice(new BigDecimal("10.000000"));
        boundary.setDiscrepancyAmount(new BigDecimal("10.000000"));
        boundary.setPlanStatus("adjudication_planned");
        return new Data(boundary, List.of(action(actionType,
                "damaged".equals(discrepancyType)
                        && "responsibility_adjustment".equals(actionType)
                        ? "responsibility" : "resolution",
                "pending", 0L, null)));
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction
            action(String actionType, String coverageKind, String status,
                    Long version, String effectReference)
    {
        var value =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction();
        value.setActionId(900L);
        value.setAdjudicationId(800L);
        value.setCaseId(700L);
        value.setSequence(1);
        value.setActionType(actionType);
        value.setCoverageKind(coverageKind);
        value.setQuantity(new BigDecimal("1.0000"));
        value.setAmount(new BigDecimal("10.000000"));
        value.setResponsibleParty("company");
        value.setExecutionStatus(status);
        value.setExecutionVersion(version);
        value.setEffectReference(effectReference);
        return value;
    }

    private static Data resulting(PreparedExecution prepared, Data before)
    {
        var boundary =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary();
        boundary.setCaseId(prepared.caseId());
        boundary.setCaseVersionAtPlan(4L);
        boundary.setCaseVersion(prepared.caseVersionAfter());
        boundary.setCaseStatus(prepared.caseStatusAfter());
        boundary.setAdjudicationId(prepared.adjudicationId());
        boundary.setDecisionFingerprint(prepared.decisionFingerprint());
        boundary.setDiscrepancyType(prepared.discrepancyType());
        boundary.setDiscrepancyQuantity(
                before.boundary().getDiscrepancyQuantity());
        boundary.setSourceCostPrice(prepared.sourceCostPrice());
        boundary.setDiscrepancyAmount(
                before.boundary().getDiscrepancyAmount());
        boundary.setPlanStatus(prepared.planStatusAfter());
        var snapshot = prepared.resultingActions().get(0);
        return new Data(boundary, List.of(action(snapshot.actionType(),
                snapshot.coverageKind(), snapshot.executionStatus(),
                snapshot.executionVersion(),
                snapshot.effectReference())));
    }

    private static PreparedExecution prepared(Data data,
            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand command)
    {
        return InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                .prepare(new Request(command.requestId(), command.caseId(),
                                command.caseVersion(),
                                command.adjudicationId(),
                                command.actionId(),
                                command.executionVersion(),
                                command.command(),
                                command.effectReference()),
                        data.boundary().toPolicyBoundary(data.actions()),
                        new Actor(99L, "execution-user",
                                Set.of(PERMISSION)), NOW);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
            stored(PreparedExecution source)
    {
        var value =
                new InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent();
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
        value.setCommand(source.command());
        value.setEffectKind(source.effectKind());
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
        value.setEventFingerprint(source.eventFingerprint());
        value.setCreateTime(Date.from(source.executedAt()));
        return value;
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
            command(String effectReference)
    {
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                "execute-action-0001", 700L, 4L, 800L, 900L, 0L,
                "dispatch", effectReference);
    }

    private static List<Path> productionReferences() throws Exception
    {
        String name =
                "InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction";
        Path root = Path.of(System.getProperty("user.dir"), "src", "main",
                "java", "com", "erp", "inventory").normalize();
        try (var paths = Files.walk(root))
        {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, name))
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static boolean contains(Path path, String value)
    {
        try
        {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains(value);
        }
        catch (java.io.IOException error)
        {
            throw new IllegalStateException(error);
        }
    }

    private record Fixture(
            InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction
                    transaction,
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                    atomicLedgerOwner,
            InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner
                    damageWriteOffOwner,
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
