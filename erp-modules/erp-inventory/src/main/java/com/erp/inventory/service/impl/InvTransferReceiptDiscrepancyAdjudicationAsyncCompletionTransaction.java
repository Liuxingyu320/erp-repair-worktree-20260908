package com.erp.inventory.service.impl;

import java.math.BigDecimal;
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
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.CompletionProof;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.PreparedExecution;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.StoredEventSnapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationExecutionMapper;
import com.erp.system.api.model.LoginUser;

/**
 * Unwired root transaction for authoritative async child completion only.
 *
 * <p>It never creates, ships or receives a child transfer. It completes an
 * already dispatched adjudication action only after the immutable workflow
 * relation and live child facts satisfy the shared P7/P10 completion
 * policy.</p>
 */
@Service
public class
        InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction
{
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;

    private final
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                    gate;
    private final InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
            executionMapper;
    private final
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper;
    private final ShopScopeService shopScopeService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction(
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                    gate,
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                    executionMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            ShopScopeService shopScopeService)
    {
        this(gate, executionMapper, workflowMapper, shopScopeService,
                Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionTransaction(
            InvTransferReceiptDiscrepancyAdjudicationAsyncCompletionWriteGate
                    gate,
            InvTransferReceiptDiscrepancyAdjudicationExecutionMapper
                    executionMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.gate = gate;
        this.executionMapper = executionMapper;
        this.workflowMapper = workflowMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result complete(
            InvTransferReceiptDiscrepancyAdjudicationExecutionCommand input,
            Long selectedShopDeptId)
    {
        gate.requireEnabled();
        InvTransferReceiptDiscrepancyAdjudicationExecutionCommand command =
                normalized(input);
        Actor actor = currentActor();
        List<Long> scopeDeptIds = scope(selectedShopDeptId);

        Long lockedCaseId = executionMapper.selectCaseIdForUpdate(
                command.caseId());
        if (!Objects.equals(command.caseId(), lockedCaseId)
                || executionMapper.countCaseInScope(command.caseId(),
                        scopeDeptIds) != 1)
        {
            throw new ServiceException(
                    "差异事项不存在或当前组织无权完成异步动作");
        }
        InvTransferReceiptDiscrepancyAdjudicationExecutionBoundary boundary =
                executionMapper.selectBoundaryForUpdate(command.caseId(),
                        command.adjudicationId());
        List<InvTransferReceiptDiscrepancyAdjudicationExecutionLockedAction>
                actions = executionMapper.selectActionsForUpdate(
                        command.caseId(), command.adjudicationId());
        if (boundary == null || actions == null || actions.isEmpty())
        {
            throw new ServiceException("差异裁决异步完成边界不存在");
        }
        InvTransferReceiptDiscrepancyAdjudicationExecutionStoredEvent
                existing = executionMapper
                        .selectEventByRequestIdForUpdate(
                                command.requestId());

        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink storedLink =
                workflowMapper.selectLinkForUpdate(command.actionId());
        if (storedLink == null)
        {
            throw new ServiceException("差异裁决异步工作流关系不存在");
        }
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .verifyLink(storedLink.toPolicyLink());
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                child = workflowMapper.selectChildForUpdate(
                        command.actionId(), link.childTransferId());
        if (child == null)
        {
            throw new ServiceException("差异裁决异步子调拨完成事实不存在");
        }
        CompletionProof proof =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .verifyCompletion(link, child.toPolicyChild());

        Request request = request(command);
        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Boundary
                policyBoundary = boundary.toPolicyBoundary(actions);
        if (existing != null)
        {
            StoredEventSnapshot replay =
                    InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .replay(request, policyBoundary,
                                    existing.toPolicySnapshot(), actor);
            requireAlignment(alignment(replay), link, proof);
            return result(replay, proof, true);
        }

        Instant executedAt = clock.instant().truncatedTo(
                ChronoUnit.SECONDS);
        PreparedExecution prepared =
                InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .prepare(request, policyBoundary, actor, executedAt);
        requireAlignment(alignment(prepared), link, proof);
        requireOne(executionMapper.insertExecutionEvent(prepared),
                "差异裁决异步完成事件追加冲突");
        requireOne(executionMapper.transitionAction(prepared),
                "差异裁决异步动作状态推进冲突");
        requireOne(executionMapper.transitionAdjudication(prepared),
                "差异裁决异步计划状态推进冲突");
        requireOne(executionMapper.transitionCase(prepared),
                "差异事项异步完成状态推进冲突");
        return result(prepared, proof, false);
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
                || !InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .COMMAND_COMPLETE.equals(value.command())
                || value.effectReference() == null)
        {
            throw new ServiceException("差异裁决异步完成请求无效");
        }
        String requestId = InvTransferCommandExecutor.requireRequestId(
                value.requestId());
        return new
                InvTransferReceiptDiscrepancyAdjudicationExecutionCommand(
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
            throw new ServiceException("差异裁决异步完成请求或权限无效");
        }
        return new Actor(userId, userName, permissions);
    }

    private List<Long> scope(Long selectedShopDeptId)
    {
        if (!positive(selectedShopDeptId) || shopScopeService == null)
        {
            throw new ServiceException("差异裁决异步完成组织范围无效");
        }
        List<Long> values = shopScopeService.resolveScopeDeptIds(
                selectedShopDeptId);
        if (values == null || values.isEmpty()
                || values.size() > MAX_SCOPE_DEPARTMENTS)
        {
            throw new ServiceException("差异裁决异步完成组织范围无效");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (!positive(value))
            {
                throw new ServiceException("差异裁决异步完成组织范围无效");
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static void requireAlignment(Alignment value, PreparedLink link,
            CompletionProof proof)
    {
        if (value == null || link == null || proof == null)
        {
            throw new ServiceException("差异裁决异步完成证据与执行变更不一致");
        }
        boolean workflowMatches =
                (InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .WORKFLOW_RESHIP.equals(link.workflowType())
                 && InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .EFFECT_RESHIP.equals(value.effectKind()))
                || (InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .WORKFLOW_RETURN.equals(link.workflowType())
                    && InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                            .EFFECT_RETURN.equals(value.effectKind()));
        if (!InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .COMMAND_COMPLETE.equals(value.command())
                || !InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                        .STATUS_COMPLETED.equals(value.actionStatusAfter())
                || !workflowMatches
                || !Objects.equals(value.caseId(), link.caseId())
                || !Objects.equals(value.adjudicationId(),
                        link.adjudicationId())
                || !Objects.equals(value.actionId(), link.actionId())
                || !Objects.equals(value.discrepancyType(),
                        link.discrepancyType())
                || !Objects.equals(value.actionType(), link.actionType())
                || !Objects.equals(value.effectKind(), link.effectKind())
                || !Objects.equals(value.effectReference(),
                        link.effectReference())
                || !Objects.equals(value.decisionFingerprint(),
                        link.decisionFingerprint())
                || !same(value.quantity(), link.quantity())
                || !same(value.sourceCostPrice(), link.sourceCostPrice())
                || !same(value.amount(), link.amount())
                || !Objects.equals(proof.actionId(), link.actionId())
                || !Objects.equals(proof.childTransferId(),
                        link.childTransferId())
                || !Objects.equals(proof.effectReference(),
                        link.effectReference())
                || !Objects.equals(proof.workflowFingerprint(),
                        link.workflowFingerprint()))
        {
            throw new ServiceException("差异裁决异步完成证据与执行变更不一致");
        }
    }

    private static Alignment alignment(PreparedExecution value)
    {
        return new Alignment(value.caseId(), value.adjudicationId(),
                value.actionId(), value.discrepancyType(),
                value.actionType(), value.effectKind(), value.command(),
                value.effectReference(), value.actionStatusAfter(),
                value.decisionFingerprint(), value.quantity(),
                value.sourceCostPrice(), value.amount());
    }

    private static Alignment alignment(StoredEventSnapshot value)
    {
        return new Alignment(value.caseId(), value.adjudicationId(),
                value.actionId(), value.discrepancyType(),
                value.actionType(), value.effectKind(), value.command(),
                value.effectReference(), value.actionStatusAfter(),
                value.decisionFingerprint(), value.quantity(),
                value.sourceCostPrice(), value.amount());
    }

    private static Result result(PreparedExecution value,
            CompletionProof proof, boolean replayed)
    {
        return new Result(value.requestId(), value.caseId(),
                value.caseVersionAfter(), value.caseStatusAfter(),
                value.adjudicationId(), value.planStatusAfter(),
                value.actionId(), value.actionStatusAfter(),
                value.executionVersionAfter(), value.effectKind(),
                value.effectReference(), proof.childTransferId(),
                proof.workflowFingerprint(), replayed, value.executedAt(),
                value.eventFingerprint());
    }

    private static Result result(StoredEventSnapshot value,
            CompletionProof proof, boolean replayed)
    {
        return new Result(value.requestId(), value.caseId(),
                value.caseVersionAfter(), value.caseStatusAfter(),
                value.adjudicationId(), value.planStatusAfter(),
                value.actionId(), value.actionStatusAfter(),
                value.executionVersionAfter(), value.effectKind(),
                value.effectReference(), proof.childTransferId(),
                proof.workflowFingerprint(), replayed, value.executedAt(),
                value.eventFingerprint());
    }

    private static void requireOne(int rows, String message)
    {
        if (rows != 1)
        {
            throw new ServiceException(message + "，业务操作已回滚");
        }
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static boolean same(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null
                && left.compareTo(right) == 0;
    }

    private record Alignment(Long caseId, Long adjudicationId,
            Long actionId, String discrepancyType, String actionType,
            String effectKind, String command, String effectReference,
            String actionStatusAfter, String decisionFingerprint,
            BigDecimal quantity, BigDecimal sourceCostPrice,
            BigDecimal amount)
    {
    }

    public record Result(String requestId, Long caseId, Long caseVersion,
            String caseStatus, Long adjudicationId, String planStatus,
            Long actionId, String actionStatus, Long executionVersion,
            String effectKind, String effectReference,
            Long childTransferId, String workflowFingerprint,
            boolean replayed, Instant executedAt, String eventFingerprint)
    {
    }
}
