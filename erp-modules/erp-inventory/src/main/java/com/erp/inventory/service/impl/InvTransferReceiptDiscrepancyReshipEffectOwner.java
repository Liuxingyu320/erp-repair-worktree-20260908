package com.erp.inventory.service.impl;

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
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.PreparedLink;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy.Source;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationExecutionPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReshipFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReshipPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReshipPolicy.PreparedChild;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyReshipMapper;
import com.erp.inventory.service.IInvTransferService;

/**
 * Unwired atomic owner for one adjudication-created reship transfer.
 * A future execution transaction must own the surrounding rollback boundary.
 */
@Service
public class InvTransferReceiptDiscrepancyReshipEffectOwner
{
    private final InvTransferReceiptDiscrepancyReshipMapper reshipMapper;
    private final
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper;
    private final IInvTransferService transferService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyReshipEffectOwner(
            InvTransferReceiptDiscrepancyReshipMapper reshipMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            IInvTransferService transferService)
    {
        this(reshipMapper, workflowMapper, transferService,
                Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyReshipEffectOwner(
            InvTransferReceiptDiscrepancyReshipMapper reshipMapper,
            InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowMapper
                    workflowMapper,
            IInvTransferService transferService, Clock clock)
    {
        this.reshipMapper = reshipMapper;
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

        InvTransferReceiptDiscrepancyReshipFact fact = reshipMapper
                .selectSourceForUpdate(caseId, caseVersion,
                        adjudicationId, actionId, actionVersion);
        if (fact == null)
        {
            throw new ServiceException("补发裁决来源事实不存在或已变化");
        }
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Source source = fact.toWorkflowSource(normalizedRequestId,
                actor.userId(), actor.userName(), createdAt);
        PreparedChild childPlan =
                InvTransferReceiptDiscrepancyReshipPolicy.prepare(source,
                        fact, selectedShopDeptId);

        InvTransferOrder submitted = transferService.submitTransfer(
                order(childPlan), List.of(detail(childPlan)),
                selectedShopDeptId);
        if (submitted == null || !positive(submitted.getTransferId()))
        {
            throw new ServiceException("补发子调拨创建结果无效，业务操作已回滚");
        }
        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowChildFact
                childFact = reshipMapper.selectCreatedChildForUpdate(
                        actionId, submitted.getTransferId());
        if (childFact == null)
        {
            throw new ServiceException("补发子调拨冻结事实缺失，业务操作已回滚");
        }
        PreparedLink link =
                InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                        .prepare(source, childFact.toPolicyChild());
        if (workflowMapper.insertLink(link) != 1)
        {
            throw new ServiceException("补发子调拨唯一关系追加冲突，业务操作已回滚");
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
                                .WORKFLOW_RESHIP)
                || !Objects.equals(verified.sourceBusinessType(),
                        InvTransferReceiptDiscrepancyAdjudicationAsyncWorkflowPolicy
                                .SOURCE_RESHIP)
                || !Objects.equals(verified.executorUserId(),
                        actor.userId())
                || !Objects.equals(verified.executorName(),
                        actor.userName()))
        {
            throw new ServiceException("补发幂等关系已被不同请求占用");
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
                                .REQUIRED_PERMISSION))
        {
            throw new ServiceException("补发裁决执行上下文无效");
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
