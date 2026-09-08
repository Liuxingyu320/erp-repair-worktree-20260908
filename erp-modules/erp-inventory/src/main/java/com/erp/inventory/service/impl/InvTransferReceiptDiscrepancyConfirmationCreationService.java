package com.erp.inventory.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationPolicy.Prepared;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationPolicy.Request;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyConfirmationStoredEvent;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyConfirmationCreationVo;
import com.erp.inventory.mapper.InvDeptScopeMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyConfirmationMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyPlanningMapper;

/** Sole transaction owner for a future V2 bilateral confirmation append. */
@Service
public class InvTransferReceiptDiscrepancyConfirmationCreationService
        extends InvBaseService
{
    private final InvTransferReceiptDiscrepancyConfirmationMapper writeMapper;
    private final InvTransferReceiptDiscrepancyPlanningMapper readMapper;
    private final Clock clock;

    @Autowired
    public InvTransferReceiptDiscrepancyConfirmationCreationService(
            InvTransferReceiptDiscrepancyConfirmationMapper writeMapper,
            InvTransferReceiptDiscrepancyPlanningMapper readMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this(writeMapper, readMapper, deptScopeMapper, shopScopeService,
                Clock.systemUTC());
    }

    InvTransferReceiptDiscrepancyConfirmationCreationService(
            InvTransferReceiptDiscrepancyConfirmationMapper writeMapper,
            InvTransferReceiptDiscrepancyPlanningMapper readMapper,
            InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService, Clock clock)
    {
        this.writeMapper = writeMapper;
        this.readMapper = readMapper;
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferReceiptDiscrepancyConfirmationCreationVo create(
            Request input, Long selectedShopDeptId)
    {
        Request request = normalized(input);
        Long selectedDeptId = resolveAndValidateShopDept(selectedShopDeptId);
        Long operatorUserId = SecurityUtils.getUserId();
        String operatorName = SecurityUtils.getUsername();
        Instant now = clock.instant();
        requireActor(operatorUserId, operatorName);

        Long lockedCaseId = writeMapper.selectCaseIdForUpdate(
                request.caseId());
        if (!Objects.equals(request.caseId(), lockedCaseId))
        {
            throw new ServiceException("差异事项不存在");
        }
        InvTransferReceiptDiscrepancyReadFact fact = readMapper.selectCase(
                request.caseId(), selectedDeptId);
        if (fact == null)
        {
            throw new ServiceException("差异事项不存在或当前组织无权确认");
        }
        Resolved resolved = InvTransferReceiptDiscrepancyBoundaryPolicy
                .resolve(fact, request.caseId(), selectedDeptId);
        Prepared prepared =
                InvTransferReceiptDiscrepancyConfirmationPolicy.prepare(
                        request, resolved.boundary(), selectedDeptId,
                        operatorUserId, operatorName, now,
                        resolved.source(), resolved.target());

        InvTransferReceiptDiscrepancyConfirmationStoredEvent existing =
                writeMapper.selectByRequestIdForUpdate(
                        prepared.requestId());
        if (existing != null)
        {
            requireReplay(existing, prepared);
            return result(existing.getEventId(), prepared.caseId(),
                    prepared.partyRole(), prepared.decision(),
                    resolved.projection().confirmationState(),
                    resolved.projection().readyForAdjudication(), true,
                    existing.getCreateTime().toInstant(), now);
        }

        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        int inserted = writeMapper.insertConfirmationEvent(prepared,
                generatedId);
        if (inserted != 1)
        {
            throw new ServiceException("差异确认事件追加冲突，已回滚");
        }
        if (generatedId.getValue() == null || generatedId.getValue() <= 0)
        {
            throw new ServiceException("差异确认事件主键生成失败，已回滚");
        }
        return result(generatedId.getValue(), prepared.caseId(),
                prepared.partyRole(), prepared.decision(),
                prepared.confirmationStateAfter(),
                prepared.readyForAdjudicationAfter(), false,
                prepared.createdAt(), now);
    }

    private static Request normalized(Request value)
    {
        if (value == null || value.caseId() == null || value.caseId() <= 0)
        {
            throw new ServiceException("差异事实确认请求缺少有效事项");
        }
        String requestId = InvTransferCommandExecutor.requireRequestId(
                value.requestId());
        return new Request(requestId, value.caseId(), value.caseVersion(),
                value.factFingerprint(), value.decision(), value.note());
    }

    private static void requireActor(Long operatorUserId,
            String operatorName)
    {
        if (operatorUserId == null || operatorUserId <= 0
                || operatorName == null || operatorName.isBlank()
                || operatorName.trim().length() > 64)
        {
            throw new ServiceException("差异确认命令缺少有效登录用户");
        }
    }

    private static void requireReplay(
            InvTransferReceiptDiscrepancyConfirmationStoredEvent existing,
            Prepared prepared)
    {
        if (existing.getEventId() == null || existing.getEventId() <= 0
                || existing.getCreateTime() == null
                || !Objects.equals(existing.getRequestId(),
                        prepared.requestId())
                || !Objects.equals(existing.getCaseId(), prepared.caseId())
                || !Objects.equals(existing.getCaseVersion(),
                        prepared.caseVersion())
                || !Objects.equals(existing.getFactFingerprint(),
                        prepared.factFingerprint())
                || !Objects.equals(existing.getPartyRole(),
                        prepared.partyRole())
                || !Objects.equals(existing.getPartyDeptId(),
                        prepared.partyDeptId())
                || !Objects.equals(existing.getDecision(),
                        prepared.decision())
                || !Objects.equals(trimToNull(existing.getNote()),
                        prepared.note())
                || !Objects.equals(existing.getOperatorUserId(),
                        prepared.operatorUserId())
                || !Objects.equals(existing.getOperatorName(),
                        prepared.operatorName()))
        {
            throw new ServiceException("幂等标识已被不同差异确认占用");
        }
    }

    private static InvTransferReceiptDiscrepancyConfirmationCreationVo result(
            Long eventId, Long caseId, String partyRole, String decision,
            String state, boolean ready, boolean replayed,
            Instant eventCreatedAt, Instant responseAt)
    {
        return new InvTransferReceiptDiscrepancyConfirmationCreationVo(
                Long.toString(eventId), Long.toString(caseId), partyRole,
                decision, state, ready, replayed,
                DateTimeFormatter.ISO_INSTANT.format(eventCreatedAt),
                DateTimeFormatter.ISO_INSTANT.format(responseAt), "server");
    }

    private static String trimToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
