package com.erp.inventory.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.StoredEventSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationExecutionMapper;
import com.erp.system.api.model.LoginUser;

/**
 * Unwired transaction owner for synchronous adjudication effects only.
 * The hard-closed execution entry deliberately does not reference it.
 */
@Service
public class InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction
{
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;

    private final InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
            mapper;
    private final
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                    atomicLedgerOwner;
    private final InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner
            damageWriteOffOwner;
    private final ShopScopeService shopScopeService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction(
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                    atomicLedgerOwner,
            InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner
                    damageWriteOffOwner,
            ShopScopeService shopScopeService)
    {
        this(mapper, atomicLedgerOwner, damageWriteOffOwner,
                shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyAdjudicationExecutionTransaction(
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationAtomicLedgerEffectOwner
                    atomicLedgerOwner,
            InvTransferReceiptDiscrepancyDamageWriteOffEffectOwner
                    damageWriteOffOwner,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.mapper = mapper;
        this.atomicLedgerOwner = atomicLedgerOwner;
        this.damageWriteOffOwner = damageWriteOffOwner;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result execute(
            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand input,
            Long selectedShopDeptId)
    {
        InvTransferReceiptDiscrepancyAdjudicationExecutionCommand command =
                normalized(input);
        Actor actor = currentActor();
        List<Long> scopeDeptIds = scope(selectedShopDeptId);

        Long lockedCaseId = mapper.selectCaseIdForUpdate(command.caseId());
        if (!Objects.equals(command.caseId(), lockedCaseId)
                || mapper.countCaseInScope(command.caseId(),
                        scopeDeptIds) != 1)
        {
            throw new ServiceException(
                    "差异事项不存在或当前组织无权执行");
        }
        InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary boundary =
                mapper.selectBoundaryForUpdate(command.caseId(),
                        command.adjudicationId());
        List<InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction>
                actions = mapper.selectActionsForUpdate(command.caseId(),
                        command.adjudicationId());
        if (boundary == null || actions == null || actions.isEmpty())
        {
            throw new ServiceException("差异裁决执行边界不存在");
        }
        InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
                existing = mapper.selectEventByRequestIdForUpdate(
                        command.requestId());
        Request request = request(command);
        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary
                policyBoundary = boundary.toPolicyBoundary(actions);
        if (existing != null)
        {
            StoredEventSnapshot replay =
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .replay(request, policyBoundary,
                                    existing.toPolicySnapshot(), actor);
            return result(replay, true);
        }

        Instant executedAt = clock.instant().truncatedTo(
                ChronoUnit.SECONDS);
        PreparedExecution prepared =
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .prepare(request, policyBoundary, actor,
                                executedAt);
        requireSynchronous(prepared);
        requireOne(mapper.insertExecutionEvent(prepared),
                "差异裁决执行事件追加冲突");
        applyEffect(prepared);
        requireOne(mapper.transitionAction(prepared),
                "差异裁决动作状态推进冲突");
        requireOne(mapper.transitionAdjudication(prepared),
                "差异裁决计划状态推进冲突");
        requireOne(mapper.transitionCase(prepared),
                "差异事项状态推进冲突");
        return result(prepared, false);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
            normalized(
                    InvTransferReceiptDiscrepancyAdjudicationExecutionCommand
                            value)
    {
        if (value == null || !positive(value.caseId())
                || value.caseVersion() == null
                || value.caseVersion() < 0
                || !positive(value.adjudicationId())
                || !positive(value.actionId())
                || value.executionVersion() == null
                || value.executionVersion() < 0
                || value.command() == null
                || !Set.of(
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .COMMAND_DISPATCH,
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .COMMAND_COMPLETE)
                        .contains(value.command()))
        {
            throw new ServiceException("差异裁决执行请求无效");
        }
        String requestId = InvTransferCommandExecutor.requireRequestId(
                value.requestId());
        return new InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
                requestId, value.caseId(), value.caseVersion(),
                value.adjudicationId(), value.actionId(),
                value.executionVersion(), value.command(),
                value.effectReference());
    }

    private static Request request(
            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand value)
    {
        return new Request(value.requestId(), value.caseId(),
                value.caseVersion(), value.adjudicationId(),
                value.actionId(), value.executionVersion(),
                value.command(), value.effectReference());
    }

    private static Actor currentActor()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        Set<String> permissions = loginUser == null
                ? null : loginUser.getPermissions();
        if (loginUser == null || !positive(userId)
                || !Objects.equals(loginUser.getUserid(), userId)
                || userName == null || userName.isBlank()
                || userName.trim().length() > 64
                || !Objects.equals(loginUser.getUsername(), userName)
                || permissions == null || !permissions.contains(
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .REQUIRED_PERMISSION))
        {
            throw new ServiceException("差异裁决执行请求或权限无效");
        }
        return new Actor(userId, userName, permissions);
    }

    private List<Long> scope(Long selectedShopDeptId)
    {
        if (!positive(selectedShopDeptId) || shopScopeService == null)
        {
            throw new ServiceException("差异裁决执行组织范围无效");
        }
        List<Long> values = shopScopeService.resolveScopeDeptIds(
                selectedShopDeptId);
        if (values == null || values.isEmpty()
                || values.size() > MAX_SCOPE_DEPARTMENTS)
        {
            throw new ServiceException("差异裁决执行组织范围无效");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (!positive(value))
            {
                throw new ServiceException("差异裁决执行组织范围无效");
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static void requireSynchronous(PreparedExecution execution)
    {
        if (!Set.of(
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_RESPONSIBILITY,
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_SHORTAGE_LOSS,
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_QUARANTINE_WRITE_OFF)
                .contains(execution.effectKind()))
        {
            throw new ServiceException(
                    "补发或退回工作流执行边界尚未实现");
        }
    }

    private void applyEffect(PreparedExecution execution)
    {
        switch (execution.effectKind())
        {
            case InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .EFFECT_RESPONSIBILITY,
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_SHORTAGE_LOSS ->
                    atomicLedgerOwner.apply(execution);
            case InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                    .EFFECT_QUARANTINE_WRITE_OFF ->
                    damageWriteOffOwner.apply(execution);
            default -> throw new ServiceException(
                    "差异裁决同步效果路由无效");
        }
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static Result result(PreparedExecution value,
            boolean replayed)
    {
        return new Result(value.requestId(), value.caseId(),
                value.caseVersionAfter(), value.caseStatusAfter(),
                value.adjudicationId(), value.planStatusAfter(),
                value.actionId(), value.actionStatusAfter(),
                value.executionVersionAfter(), value.effectKind(),
                value.effectReference(), replayed, value.executedAt(),
                value.eventFingerprint());
    }

    private static Result result(StoredEventSnapshot value,
            boolean replayed)
    {
        return new Result(value.requestId(), value.caseId(),
                value.caseVersionAfter(), value.caseStatusAfter(),
                value.adjudicationId(), value.planStatusAfter(),
                value.actionId(), value.actionStatusAfter(),
                value.executionVersionAfter(), value.effectKind(),
                value.effectReference(), replayed, value.executedAt(),
                value.eventFingerprint());
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    public record Result(
            String requestId,
            Long caseId,
            Long caseVersion,
            String caseStatus,
            Long adjudicationId,
            String planStatus,
            Long actionId,
            String actionStatus,
            Long executionVersion,
            String effectKind,
            String effectReference,
            boolean replayed,
            Instant executedAt,
            String eventFingerprint)
    {
    }
}
