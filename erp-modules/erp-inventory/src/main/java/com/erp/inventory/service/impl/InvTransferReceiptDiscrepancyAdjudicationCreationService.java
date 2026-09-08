package com.erp.inventory.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.PreparedConsumption;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.VerifiedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationCommand;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.PreparedAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyFacts;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationCreationVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationBasisMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationMapper;
import com.erp.system.api.model.LoginUser;

/** Sole transaction owner for a future V2 discrepancy adjudication plan. */
@Service
public class InvTransferReceiptDiscrepancyAdjudicationCreationService
{
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;

    private final InvTransferReceiptDiscrepancyAdjudicationMapper mapper;
    private final InvTransferReceiptDiscrepancyAdjudicationBasisMapper
            basisMapper;
    private final ShopScopeService shopScopeService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyAdjudicationCreationService(
            InvTransferReceiptDiscrepancyAdjudicationMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService shopScopeService)
    {
        this(mapper, basisMapper, shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyAdjudicationCreationService(
            InvTransferReceiptDiscrepancyAdjudicationMapper mapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.mapper = mapper;
        this.basisMapper = basisMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferReceiptDiscrepancyAdjudicationCreationVo create(
            InvTransferReceiptDiscrepancyAdjudicationCommand input,
            Long selectedShopDeptId)
    {
        InvTransferReceiptDiscrepancyAdjudicationCommand command =
                normalized(input);
        Actor actor = currentActor();
        List<Long> scopeDeptIds = scope(selectedShopDeptId);
        String tokenHash =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .tokenHash(command.basisToken());

        InvTransferReceiptDiscrepancyReadFact fact =
                mapper.selectCaseForUpdate(command.caseId(), scopeDeptIds);
        if (fact == null)
        {
            throw unavailable();
        }
        InvTransferReceiptDiscrepancyStoredAdjudication existing =
                mapper.selectByRequestIdForUpdate(command.requestId());
        InvTransferReceiptDiscrepancyStoredAdjudicationBasis storedBasis =
                basisMapper.selectByTokenHashForUpdate(tokenHash);
        if (existing != null)
        {
            VerifiedBasis verified =
                    InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                            .verifyConsumedReplay(command.basisToken(),
                                    storedBasis, fact, existing, actor,
                                    selectedShopDeptId, scopeDeptIds,
                                    command.requestId());
            Request request = request(command,
                    verified.factFingerprint());
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    actions = mapper.selectActionsByAdjudicationId(
                            existing.getAdjudicationId());
            PreparedAdjudication replay =
                    InvTransferReceiptDiscrepancyAdjudicationPolicy.replay(
                            request, existing, actions, actor);
            requireReplayCurrentFact(fact, replay);
            return result(existing.getAdjudicationId(), replay, true,
                    replay.createdAt(), clock.instant());
        }

        Resolved resolved = InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolveForAdjudication(fact, command.caseId());
        Instant preparedAt = clock.instant();
        VerifiedBasis verified =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .verifyIssued(command.basisToken(), storedBasis,
                                resolved, actor, selectedShopDeptId,
                                scopeDeptIds, preparedAt);
        Request request = request(command, verified.factFingerprint());
        PreparedAdjudication prepared =
                InvTransferReceiptDiscrepancyAdjudicationPolicy.prepare(
                        request, resolved, actor, preparedAt);
        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        if (mapper.insertAdjudication(prepared, generatedId) != 1)
        {
            throw new ServiceException("差异裁决计划写入冲突，已回滚");
        }
        if (generatedId.getValue() == null || generatedId.getValue() <= 0)
        {
            throw new ServiceException("差异裁决计划主键生成失败，已回滚");
        }
        int actionRows = mapper.insertActions(generatedId.getValue(),
                prepared);
        if (actionRows != prepared.actions().size())
        {
            throw new ServiceException("差异裁决动作写入不完整，已回滚");
        }
        if (mapper.transitionCaseToPlanned(prepared) != 1)
        {
            throw new ServiceException("差异事项状态推进冲突，已回滚");
        }
        Instant consumedAt = clock.instant();
        PreparedConsumption consumption =
                InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                        .prepareConsumption(verified, request.requestId(),
                                generatedId.getValue(), consumedAt, actor);
        if (basisMapper.consumeIssuedBasis(consumption) != 1)
        {
            throw new ServiceException("差异裁决依据消费冲突，已回滚");
        }
        return result(generatedId.getValue(), prepared, false,
                prepared.createdAt(), consumedAt);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationCommand
            normalized(InvTransferReceiptDiscrepancyAdjudicationCommand value)
    {
        if (value == null || !positive(value.caseId())
                || value.caseVersion() == null
                || value.caseVersion() < 0)
        {
            throw new ServiceException("差异裁决请求缺少有效事项");
        }
        String requestId = InvTransferCommandExecutor.requireRequestId(
                value.requestId());
        return new InvTransferReceiptDiscrepancyAdjudicationCommand(
                requestId, value.basisToken(), value.caseId(),
                value.caseVersion(), value.adjudicationNote(),
                value.evidenceRefs(), value.actions());
    }

    private static Request request(
            InvTransferReceiptDiscrepancyAdjudicationCommand command,
            String factFingerprint)
    {
        return new Request(command.requestId(), command.caseId(),
                command.caseVersion(), factFingerprint,
                command.adjudicationNote(), command.evidenceRefs(),
                command.actions());
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
                || userName.trim().length() > 64 || !Objects.equals(
                        loginUser.getUsername(), userName)
                || permissions == null || !permissions.contains(
                        InvTransferReceiptDiscrepancyAdjudicationPolicy
                                .REQUIRED_PERMISSION))
        {
            throw new ServiceException("独立差异裁决请求或权限无效");
        }
        return new Actor(userId, userName, permissions);
    }

    private List<Long> scope(Long selectedShopDeptId)
    {
        if (!positive(selectedShopDeptId) || shopScopeService == null)
        {
            throw new ServiceException("差异裁决组织范围无效");
        }
        List<Long> values = shopScopeService.resolveScopeDeptIds(
                selectedShopDeptId);
        if (values == null || values.isEmpty()
                || values.size() > MAX_SCOPE_DEPARTMENTS)
        {
            throw new ServiceException("差异裁决组织范围无效");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (!positive(value))
            {
                throw new ServiceException("差异裁决组织范围无效");
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static ServiceException unavailable()
    {
        return new ServiceException("差异事项不存在或当前范围无权裁决");
    }

    private static void requireReplayCurrentFact(
            InvTransferReceiptDiscrepancyReadFact fact,
            PreparedAdjudication replay)
    {
        InvTransferReceiptDiscrepancyReadFact.Confirmation source =
                fact.getSourceConfirmation();
        InvTransferReceiptDiscrepancyReadFact.Confirmation target =
                fact.getTargetConfirmation();
        String fingerprint = InvTransferReceiptDiscrepancyFacts.fingerprint(
                fact.getReceiptPlanVersion(),
                fact.getShipmentAllocationId(), fact.getDiscrepancyType(),
                fact.getDiscrepancyQuantity(), fact.getSourceCostPrice(),
                fact.getDiscrepancyAmount(), fact.getDiscrepancyNote(),
                fact.getAttachmentRefs());
        if (!Objects.equals(fact.getDiscrepancyCaseId(), replay.caseId())
                || !Objects.equals(fact.getCaseVersion(),
                        replay.caseVersionAfter())
                || !Objects.equals(fact.getCaseStatus(),
                        InvTransferReceiptDiscrepancyAdjudicationPolicy
                                .PLAN_STATUS)
                || !Objects.equals(fact.getFactFingerprint(),
                        replay.factFingerprint())
                || !Objects.equals(fingerprint, replay.factFingerprint())
                || !Objects.equals(fact.getDiscrepancyType(),
                        replay.discrepancyType())
                || fact.getDiscrepancyQuantity() == null
                || fact.getDiscrepancyQuantity().compareTo(
                        replay.discrepancyQuantity()) != 0
                || fact.getSourceCostPrice() == null
                || fact.getSourceCostPrice().compareTo(
                        replay.sourceCostPrice()) != 0
                || fact.getDiscrepancyAmount() == null
                || fact.getDiscrepancyAmount().compareTo(
                        replay.discrepancyAmount()) != 0
                || fact.getSourceDeptId() == null
                || fact.getSourceDeptId() <= 0
                || fact.getTargetDeptId() == null
                || fact.getTargetDeptId() <= 0
                || Objects.equals(fact.getSourceDeptId(),
                        fact.getTargetDeptId())
                || !currentConfirmation(source, "source",
                        fact.getSourceDeptId(),
                        replay.sourceConfirmationEventId(),
                        replay.caseVersionBefore(),
                        replay.factFingerprint(),
                        replay.adjudicatorUserId())
                || !currentConfirmation(target, "target",
                        fact.getTargetDeptId(),
                        replay.targetConfirmationEventId(),
                        replay.caseVersionBefore(),
                        replay.factFingerprint(),
                        replay.adjudicatorUserId()))
        {
            throw new ServiceException("已存差异裁决与当前事项不可核验");
        }
    }

    private static boolean currentConfirmation(
            InvTransferReceiptDiscrepancyReadFact.Confirmation value,
            String role, Long deptId, Long eventId, Long version,
            String fingerprint, Long adjudicatorUserId)
    {
        return value != null && Objects.equals(value.getEventId(), eventId)
                && value.getRequestId() != null
                && value.getRequestId().matches(
                        "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")
                && Objects.equals(value.getPartyRole(), role)
                && Objects.equals(value.getPartyDeptId(), deptId)
                && Objects.equals(value.getCaseVersion(), version)
                && Objects.equals(value.getFactFingerprint(), fingerprint)
                && Objects.equals(value.getDecision(), "confirmed")
                && value.getOperatorUserId() != null
                && value.getOperatorUserId() > 0
                && value.getOperatorName() != null
                && !value.getOperatorName().isBlank()
                && value.getOperatorName().length() <= 64
                && value.getCreateTime() != null
                && !Objects.equals(value.getOperatorUserId(),
                        adjudicatorUserId);
    }

    private static InvTransferReceiptDiscrepancyAdjudicationCreationVo result(
            Long adjudicationId, PreparedAdjudication prepared,
            boolean replayed, Instant planCreatedAt, Instant responseAt)
    {
        return new InvTransferReceiptDiscrepancyAdjudicationCreationVo(
                Long.toString(adjudicationId),
                Long.toString(prepared.caseId()), prepared.planStatus(),
                prepared.actions().size(), replayed,
                DateTimeFormatter.ISO_INSTANT.format(planCreatedAt),
                DateTimeFormatter.ISO_INSTANT.format(responseAt), "server");
    }
}
