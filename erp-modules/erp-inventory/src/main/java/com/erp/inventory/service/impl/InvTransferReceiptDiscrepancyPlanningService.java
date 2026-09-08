package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.PartyProjection;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Result;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationProjection.Snapshot;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyPlanningVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;

/** Authoritative, organization-scoped read service for one V2 discrepancy. */
@Service
public class InvTransferReceiptDiscrepancyPlanningService
        extends InvBaseService
{
    private final InvTransferReceiptDiscrepancyPlanningMapper mapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyPlanningService(
            InvTransferReceiptDiscrepancyPlanningMapper mapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(mapper, deptScopeMapper, shopScopeService, Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyPlanningService(
            InvTransferReceiptDiscrepancyPlanningMapper mapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.mapper = mapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvTransferReceiptDiscrepancyPlanningVo getPlanning(Long caseId,
            Long selectedShopDeptId)
    {
        if (!positive(caseId))
        {
            throw new ServiceException("差异事项标识无效");
        }
        Long selectedDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferReceiptDiscrepancyReadFact fact = mapper.selectCase(
                caseId, selectedDeptId);
        if (fact == null)
        {
            throw new ServiceException("差异事项不存在或当前组织无权读取");
        }
        Resolved resolved = InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolve(fact, caseId, selectedDeptId);
        return toVo(fact, resolved.projection(),
                resolved.currentPartyRole(), clock.instant());
    }

    private static InvTransferReceiptDiscrepancyPlanningVo toVo(
            InvTransferReceiptDiscrepancyReadFact fact, Result projection,
            String currentRole, Instant generatedAt)
    {
        return new InvTransferReceiptDiscrepancyPlanningVo(
                id(fact.getDiscrepancyCaseId()), id(fact.getReceiptId()),
                fact.getReceiptNo().trim(), id(fact.getShipmentId()),
                fact.getShipmentNo().trim(), id(fact.getTransferId()),
                fact.getOrderNo().trim(),
                id(fact.getShipmentAllocationId()),
                id(fact.getShipmentDetailId()),
                id(fact.getTransferDetailId()), fact.getItemType().trim(),
                id(fact.getItemId()), fact.getItemCode().trim(),
                fact.getItemName().trim(), fact.getUnit().trim(),
                fact.getDiscrepancyType(),
                decimal(fact.getDiscrepancyQuantity()),
                decimal(fact.getDiscrepancyAmount()),
                fact.getDiscrepancyNote().trim(),
                fact.getAttachmentRefs().trim(),
                fact.getFactFingerprint(), fact.getCaseStatus(),
                Long.toString(fact.getCaseVersion()),
                party(InvTransferReceiptDiscrepancyConfirmationProjection
                                .SOURCE,
                        fact.getSourceDeptId(), fact.getSourceName(),
                        projection.source()),
                party(InvTransferReceiptDiscrepancyConfirmationProjection
                                .TARGET,
                        fact.getTargetDeptId(), fact.getTargetName(),
                        projection.target()),
                currentRole, projection.confirmationState(),
                projection.readyForAdjudication(),
                format(instant(fact.getCaseCreateTime())),
                format(generatedAt), "server");
    }

    private static InvTransferReceiptDiscrepancyPlanningVo.Party party(
            String role, Long deptId, String name, PartyProjection value)
    {
        return new InvTransferReceiptDiscrepancyPlanningVo.Party(role,
                id(deptId), name.trim(), confirmation(value));
    }

    private static InvTransferReceiptDiscrepancyPlanningVo.Confirmation
            confirmation(PartyProjection value)
    {
        if (value == null || value.snapshot() == null)
        {
            return null;
        }
        Snapshot snapshot = value.snapshot();
        return new InvTransferReceiptDiscrepancyPlanningVo.Confirmation(
                id(snapshot.eventId()), snapshot.decision(),
                trimToNull(snapshot.note()), snapshot.operatorName().trim(),
                format(snapshot.createdAt()), value.currentFact());
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
            throw new ServiceException("差异事项包含无效标识");
        }
        return Long.toString(value);
    }

    private static Instant instant(Date value)
    {
        if (value == null)
        {
            throw new ServiceException("差异事项缺少时间事实");
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
