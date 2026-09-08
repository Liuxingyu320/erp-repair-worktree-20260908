package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.PreparedChild;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.PreparedReservation;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnPolicy.Serial;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReturnSerialFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.transfer.InvTransferReceiptLocationCandidate;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetBalance;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetLot;
import com.erp.inventory.domain.transfer.InvTransferReceiptTargetStock;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReturnMapper;

/**
 * Unwired atomic owner of a reverse child transfer and its V2 quarantine
 * reservation. The future execution transaction owns the rollback boundary.
 */
@Service
public class InvTransferReceiptDiscrepancyReturnEffectOwner
{
    private final InvTransferReceiptDiscrepancyReturnMapper returnMapper;
    private final
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper;
    private final InvTransferServiceImpl transferService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReturnEffectOwner(
            InvTransferReceiptDiscrepancyReturnMapper returnMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            InvTransferServiceImpl transferService)
    {
        this(returnMapper, workflowMapper, transferService,
                Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReturnEffectOwner(
            InvTransferReceiptDiscrepancyReturnMapper returnMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            InvTransferServiceImpl transferService, Clock clock)
    {
        this.returnMapper = returnMapper;
        this.workflowMapper = workflowMapper;
        this.transferService = transferService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public DispatchResult dispatch(String requestId, Long caseId,
            Long caseVersion, Long adjudicationId, Long actionId,
            Long actionVersion, Long selectedShopDeptId, Actor actor)
    {
        String normalizedRequestId = InvTransferCommandExecutor
                .requireRequestId(requestId);
        requireEnvelope(caseId, caseVersion, adjudicationId, actionId,
                actionVersion, selectedShopDeptId, actor);

        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink stored =
                workflowMapper.selectLinkForUpdate(actionId);
        if (stored != null)
        {
            return replay(normalizedRequestId, caseId, caseVersion,
                    adjudicationId, actionId, actionVersion, actor,
                    stored.toPolicyLink());
        }

        InvTransferReceiptDiscrepancyReturnFact fact = returnMapper
                .selectSourceForUpdate(caseId, caseVersion,
                        adjudicationId, actionId, actionVersion);
        if (fact == null)
        {
            throw new ServiceException("退回裁决来源事实不存在或已变化");
        }
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Source source = fact.toWorkflowSource(normalizedRequestId,
                actor.userId(), actor.userName(), createdAt);
        PreparedChild childPlan =
                InvTransferReceiptDiscrepancyReturnPolicy.prepareChild(
                        source, fact, selectedShopDeptId);

        InvTransferOrder draft = transferService.saveDraft(order(childPlan),
                List.of(detail(childPlan)), selectedShopDeptId);
        if (draft == null || !positive(draft.getTransferId()))
        {
            throw new ServiceException("退回子调拨草稿创建结果无效，业务操作已回滚");
        }
        InvTransferDetail persistedDetail = returnMapper
                .selectCreatedDraftDetailForUpdate(actionId,
                        draft.getTransferId());
        if (persistedDetail == null)
        {
            throw new ServiceException("退回子调拨唯一明细缺失，业务操作已回滚");
        }

        BigDecimal disposed = returnMapper.selectDisposedQuantity(
                fact.getReceiptAllocationId());
        InvTransferReceiptTargetStock stock = returnMapper
                .selectStockForUpdate(fact);
        InvTransferReceiptTargetLot lot = returnMapper
                .selectLotForUpdate(fact);
        InvTransferReceiptLocationCandidate location = returnMapper
                .selectLocationForUpdate(fact);
        InvTransferReceiptTargetBalance balance = returnMapper
                .selectBalanceForUpdate(fact);
        List<InvTransferReceiptDiscrepancyReturnSerialFact> serials =
                returnMapper.selectEligibleSerialsForUpdate(fact);
        int reservationRound = draft.getApprovalRound() == null
                ? 1 : draft.getApprovalRound() + 1;
        PreparedReservation reservation =
                InvTransferReceiptDiscrepancyReturnPolicy
                        .prepareReservation(source, fact, childPlan, draft,
                                persistedDetail, reservationRound, disposed,
                                stock, lot, location, balance, serials);

        requireOne(returnMapper.reserveStock(reservation),
                "退回汇总隔离库存冻结冲突");
        requireOne(returnMapper.reserveBalance(reservation),
                "退回批次库位隔离余额冻结冲突");
        for (Serial serial : reservation.serials())
        {
            requireOne(returnMapper.reserveSerial(reservation, serial),
                    "退回隔离序列号冻结冲突");
        }
        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        requireOne(returnMapper.insertReservation(reservation, generatedId),
                "退回隔离预留台账写入冲突");
        if (!positive(generatedId.getValue()))
        {
            throw new ServiceException("退回隔离预留标识缺失，业务操作已回滚");
        }
        for (Serial serial : reservation.serials())
        {
            requireOne(returnMapper.insertReservationSerial(reservation,
                    serial, generatedId.getValue()),
                    "退回隔离序列号预留台账写入冲突");
        }

        InvTransferOrder submitted = transferService
                .finalizeAdjudicationReturnSubmission(draft,
                        List.of(persistedDetail));
        if (submitted == null || !Objects.equals(submitted.getTransferId(),
                draft.getTransferId()))
        {
            throw new ServiceException("退回子调拨提交结果无效，业务操作已回滚");
        }
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                childFact = returnMapper.selectCreatedChildForUpdate(
                        actionId, submitted.getTransferId());
        if (childFact == null)
        {
            throw new ServiceException("退回子调拨隔离预留事实缺失，业务操作已回滚");
        }
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, childFact.toPolicyChild());
        if (workflowMapper.insertLink(link) != 1)
        {
            throw new ServiceException("退回子调拨唯一关系追加冲突，业务操作已回滚");
        }
        return new DispatchResult(link, false);
    }

    private static DispatchResult replay(String requestId, Long caseId,
            Long caseVersion, Long adjudicationId, Long actionId,
            Long actionVersion, Actor actor, PreparedLink stored)
    {
        PreparedLink verified =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .verifyLink(stored);
        if (!Objects.equals(verified.requestId(), requestId)
                || !Objects.equals(verified.caseId(), caseId)
                || !Objects.equals(verified.caseVersionBefore(),
                        caseVersion)
                || !Objects.equals(verified.adjudicationId(),
                        adjudicationId)
                || !Objects.equals(verified.actionId(), actionId)
                || !Objects.equals(verified.actionVersionBefore(),
                        actionVersion)
                || !Objects.equals(verified.workflowType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .WORKFLOW_RETURN)
                || !Objects.equals(verified.sourceBusinessType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .SOURCE_RETURN)
                || !Objects.equals(verified.executorUserId(), actor.userId())
                || !Objects.equals(verified.executorName(), actor.userName()))
        {
            throw new ServiceException("退回幂等关系已被不同请求占用");
        }
        return new DispatchResult(verified, true);
    }

    private static InvTransferOrder order(PreparedChild prepared)
    {
        InvTransferOrder order = new InvTransferOrder();
        order.setTransferType(prepared.transferType());
        order.setFromDeptId(prepared.fromDeptId());
        order.setFromWarehouseId(prepared.fromWarehouseId());
        order.setToDeptId(prepared.toDeptId());
        order.setToWarehouseId(prepared.toWarehouseId());
        order.setReturnReasonCode(prepared.returnReasonCode());
        order.setReturnReasonText(prepared.returnReasonText());
        order.setSourceBusinessType(prepared.sourceBusinessType());
        order.setSourceBusinessId(prepared.sourceBusinessId());
        order.setRemark(prepared.remark());
        return order;
    }

    private static InvTransferDetail detail(PreparedChild prepared)
    {
        InvTransferDetail detail = new InvTransferDetail();
        detail.setItemType(prepared.itemType());
        detail.setItemId(prepared.itemId());
        detail.setProductId(prepared.productId());
        detail.setQuantity(prepared.quantity());
        detail.setCostPrice(prepared.referenceCostPrice());
        return detail;
    }

    private static void requireEnvelope(Long caseId, Long caseVersion,
            Long adjudicationId, Long actionId, Long actionVersion,
            Long selectedShopDeptId, Actor actor)
    {
        if (!positive(caseId) || caseVersion == null || caseVersion < 0
                || !positive(adjudicationId) || !positive(actionId)
                || actionVersion == null || actionVersion < 0
                || !positive(selectedShopDeptId) || actor == null
                || !positive(actor.userId()) || actor.userName() == null
                || actor.userName().isBlank()
                || !actor.userName().equals(actor.userName().trim())
                || actor.userName().length() > 64
                || actor.permissions() == null
                || !actor.permissions().contains(
                        InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy
                                .REQUIRED_PERMISSION)
                || !Objects.equals(SecurityUtils.getUserId(), actor.userId())
                || !Objects.equals(SecurityUtils.getUsername(),
                        actor.userName()))
        {
            throw new ServiceException("退回裁决执行上下文无效");
        }
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

    public record DispatchResult(PreparedLink link, boolean replayed)
    {
    }
}
