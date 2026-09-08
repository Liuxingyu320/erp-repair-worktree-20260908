package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationProjection;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationProjection.Projection;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.ReadBoundary;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.PartyProjection;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudication;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyStoredAdjudicationAction;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationPlanningVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;
import com.erp.system.api.model.LoginUser;

/** Permission and organization scoped read service for V2 adjudication. */
@Service
public class InvTransferReceiptDiscrepancyAdjudicationPlanningService
{
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;

    private final InvTransferReceiptDiscrepancyPlanningMapper mapper;
    private final ShopScopeService shopScopeService;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyAdjudicationPlanningService(
            InvTransferReceiptDiscrepancyPlanningMapper mapper,
            ShopScopeService shopScopeService)
    {
        this(mapper, shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyAdjudicationPlanningService(
            InvTransferReceiptDiscrepancyPlanningMapper mapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.mapper = mapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InvTransferReceiptDiscrepancyAdjudicationPlanningVo getPlanning(
            Long caseId, Long selectedShopDeptId)
    {
        if (!positive(caseId))
        {
            throw new ServiceException("差异事项标识无效");
        }
        requireExactPermission();
        List<Long> scopeDeptIds = scope(selectedShopDeptId);
        InvTransferReceiptDiscrepancyReadFact fact =
                mapper.selectCaseForAdjudication(caseId, scopeDeptIds);
        if (fact == null)
        {
            throw new ServiceException("差异事项不存在或当前范围无权读取");
        }
        List<InvTransferReceiptDiscrepancyStoredAdjudication> headers =
                mapper.selectAdjudicationsByCaseId(caseId);
        List<InvTransferReceiptDiscrepancyStoredAdjudicationAction> actions =
                mapper.selectAdjudicationActionsByCaseId(caseId);
        ReadBoundary read = InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolveForAdjudicationRead(fact, caseId);
        Projection projection =
                InvTransferReceiptDiscrepancyAdjudicationProjection.project(
                        read, headers, actions);
        return toVo(read, projection, headers, actions, clock.instant());
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
        if (unique.isEmpty())
        {
            throw new ServiceException("差异裁决组织范围无效");
        }
        return List.copyOf(unique);
    }

    private static void requireExactPermission()
    {
        LoginUser login = SecurityUtils.getLoginUser();
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        Set<String> permissions = login == null
                ? null : login.getPermissions();
        if (login == null || !positive(userId)
                || !Objects.equals(login.getUserid(), userId)
                || userName == null || userName.isBlank()
                || !Objects.equals(login.getUsername(), userName)
                || permissions == null || !permissions.contains(
                        InvTransferReceiptDiscrepancyAdjudicationPolicy
                                .REQUIRED_PERMISSION))
        {
            throw new ServiceException("独立差异裁决读取权限无效");
        }
    }

    private static InvTransferReceiptDiscrepancyAdjudicationPlanningVo toVo(
            ReadBoundary read, Projection projection,
            List<InvTransferReceiptDiscrepancyStoredAdjudication> headers,
            List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                    actions,
            Instant generatedAt)
    {
        InvTransferReceiptDiscrepancyReadFact fact = read.fact();
        return new InvTransferReceiptDiscrepancyAdjudicationPlanningVo(
                id(fact.getDiscrepancyCaseId()), id(fact.getReceiptId()),
                fact.getReceiptNo().trim(), id(fact.getShipmentId()),
                fact.getShipmentNo().trim(), id(fact.getTransferId()),
                fact.getOrderNo().trim(), fact.getItemType().trim(),
                id(fact.getItemId()), fact.getItemCode().trim(),
                fact.getItemName().trim(), fact.getUnit().trim(),
                fact.getDiscrepancyType(),
                decimal(fact.getDiscrepancyQuantity()),
                decimal(fact.getDiscrepancyAmount()),
                fact.getDiscrepancyNote().trim(),
                fact.getAttachmentRefs().trim(), fact.getCaseStatus(),
                Long.toString(fact.getCaseVersion()),
                party("source", fact.getSourceDeptId(),
                        fact.getSourceName(),
                        projection.confirmations().source()),
                party("target", fact.getTargetDeptId(),
                        fact.getTargetName(),
                        projection.confirmations().target()),
                projection.workflowState(),
                projection.readyForAdjudication(),
                plan(projection, headers, actions),
                format(instant(fact.getCaseCreateTime())),
                format(generatedAt), "server");
    }

    private static InvTransferReceiptDiscrepancyAdjudicationPlanningVo.Party
            party(String role, Long organizationId, String organizationName,
                    PartyProjection projection)
    {
        return new InvTransferReceiptDiscrepancyAdjudicationPlanningVo.Party(
                role, id(organizationId), organizationName.trim(),
                confirmation(projection));
    }

    private static InvTransferReceiptDiscrepancyAdjudicationPlanningVo
            .Confirmation confirmation(PartyProjection projection)
    {
        if (projection == null || projection.snapshot() == null)
        {
            return null;
        }
        Snapshot value = projection.snapshot();
        return new InvTransferReceiptDiscrepancyAdjudicationPlanningVo
                .Confirmation(id(value.eventId()), value.decision(),
                        trimToNull(value.note()),
                        value.operatorName().trim(),
                        format(value.createdAt()),
                        projection.currentFact());
    }

    private static InvTransferReceiptDiscrepancyAdjudicationPlanningVo.Plan
            plan(Projection projection,
                    List<InvTransferReceiptDiscrepancyStoredAdjudication>
                            headers,
                    List<InvTransferReceiptDiscrepancyStoredAdjudicationAction>
                            actions)
    {
        if (projection.plan() == null)
        {
            return null;
        }
        InvTransferReceiptDiscrepancyStoredAdjudication header =
                headers.get(0);
        List<InvTransferReceiptDiscrepancyAdjudicationPlanningVo.Action>
                resultActions = new ArrayList<>();
        for (InvTransferReceiptDiscrepancyStoredAdjudicationAction action
                : actions)
        {
            resultActions.add(
                    new InvTransferReceiptDiscrepancyAdjudicationPlanningVo
                            .Action(id(action.getActionId()),
                                    action.getSequence(),
                                    action.getActionType(),
                                    action.getCoverageKind(),
                                    decimal(action.getQuantity()),
                                    decimal(action.getAmount()),
                                    action.getResponsibleParty(),
                                    action.getNote().trim(),
                                    action.getExecutionStatus()));
        }
        return new InvTransferReceiptDiscrepancyAdjudicationPlanningVo.Plan(
                id(header.getAdjudicationId()), header.getPlanStatus(),
                Long.toString(header.getCaseVersionBefore()),
                Long.toString(header.getCaseVersionAfter()),
                header.getAdjudicationNote().trim(),
                header.getEvidenceRefs().trim(),
                header.getAdjudicatorName().trim(),
                format(instant(header.getCreateTime())), resultActions);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static String id(Long value)
    {
        if (!positive(value))
        {
            throw new ServiceException("差异裁决投影包含无效标识");
        }
        return Long.toString(value);
    }

    private static Instant instant(Date value)
    {
        if (value == null)
        {
            throw new ServiceException("差异裁决投影缺少时间事实");
        }
        return value.toInstant();
    }

    private static String format(Instant value)
    {
        return DateTimeFormatter.ISO_INSTANT.format(value);
    }

    private static String trimToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }
}
