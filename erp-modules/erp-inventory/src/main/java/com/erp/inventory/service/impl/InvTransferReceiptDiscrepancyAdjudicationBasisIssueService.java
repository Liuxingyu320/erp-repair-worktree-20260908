package com.erp.inventory.service.impl;

import java.security.SecureRandom;
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
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationBasisPolicy.IssuedBasis;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyAdjudicationPolicy.Actor;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyBoundaryPolicy.Resolved;
import com.erp.inventory.domain.transfer.InvTransferReceiptDiscrepancyReadFact;
import com.erp.inventory.domain.transfer.InvTransferReceiptGeneratedId;
import com.erp.inventory.domain.vo.InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationBasisMapper;
import com.erp.inventory.mapper.InvTransferReceiptDiscrepancyAdjudicationMapper;
import com.erp.system.api.model.LoginUser;

/** Sole transaction owner for a future opaque adjudication basis issue. */
@Service
public class InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
{
    private static final int MAX_SCOPE_DEPARTMENTS = 10000;
    private static final int TOKEN_ENTROPY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InvTransferReceiptDiscrepancyAdjudicationMapper caseMapper;
    private final InvTransferReceiptDiscrepancyAdjudicationBasisMapper
            basisMapper;
    private final ShopScopeService shopScopeService;
    private final Clock clock;
    private final EntropySource entropySource;

    @Autowired
    public InvTransferReceiptDiscrepancyAdjudicationBasisIssueService(
            InvTransferReceiptDiscrepancyAdjudicationMapper caseMapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService shopScopeService)
    {
        this(caseMapper, basisMapper, shopScopeService, Clock.systemUTC(),
                InvTransferReceiptDiscrepancyAdjudicationBasisIssueService
                        ::secureEntropy);
    }

    InvTransferReceiptDiscrepancyAdjudicationBasisIssueService(
            InvTransferReceiptDiscrepancyAdjudicationMapper caseMapper,
            InvTransferReceiptDiscrepancyAdjudicationBasisMapper basisMapper,
            ShopScopeService shopScopeService, Clock clock,
            EntropySource entropySource)
    {
        this.caseMapper = caseMapper;
        this.basisMapper = basisMapper;
        this.shopScopeService = shopScopeService;
        this.clock = clock;
        this.entropySource = entropySource;
    }

    @Transactional(rollbackFor = Exception.class)
    public InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo issue(
            Long caseId, Long selectedShopDeptId)
    {
        if (!positive(caseId))
        {
            throw new ServiceException("差异裁决依据事项无效");
        }
        Actor actor = currentActor();
        List<Long> scopeDeptIds = scope(selectedShopDeptId);
        InvTransferReceiptDiscrepancyReadFact fact =
                caseMapper.selectCaseForUpdate(caseId, scopeDeptIds);
        if (fact == null)
        {
            throw unavailable();
        }
        IssuedBasis issued;
        try
        {
            Resolved resolved = InvTransferReceiptDiscrepancyBoundaryPolicy
                    .resolveForAdjudication(fact, caseId);
            Instant now = clock.instant();
            issued =
                    InvTransferReceiptDiscrepancyAdjudicationBasisPolicy
                            .issue(resolved, actor, selectedShopDeptId,
                                    scopeDeptIds, now,
                                    entropySource.next());
        }
        catch (ServiceException invalidBoundary)
        {
            throw unavailable();
        }
        InvTransferReceiptGeneratedId generatedId =
                new InvTransferReceiptGeneratedId();
        if (basisMapper.insertIssuedBasis(issued.prepared(), generatedId)
                != 1)
        {
            throw new ServiceException("差异裁决依据签发冲突，已回滚");
        }
        if (!positive(generatedId.getValue()))
        {
            throw new ServiceException("差异裁决依据主键生成失败，已回滚");
        }
        return result(issued, selectedShopDeptId);
    }

    private static Actor currentActor()
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
            throw new ServiceException("独立差异裁决依据签发权限无效");
        }
        return new Actor(userId, userName, permissions);
    }

    private List<Long> scope(Long selectedShopDeptId)
    {
        if (!positive(selectedShopDeptId) || shopScopeService == null)
        {
            throw new ServiceException("差异裁决依据组织范围无效");
        }
        List<Long> values = shopScopeService.resolveScopeDeptIds(
                selectedShopDeptId);
        if (values == null || values.isEmpty()
                || values.size() > MAX_SCOPE_DEPARTMENTS)
        {
            throw new ServiceException("差异裁决依据组织范围无效");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values)
        {
            if (!positive(value))
            {
                throw new ServiceException("差异裁决依据组织范围无效");
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static byte[] secureEntropy()
    {
        byte[] bytes = new byte[TOKEN_ENTROPY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo
            result(IssuedBasis issued, Long selectedShopDeptId)
    {
        var prepared = issued.prepared();
        return new InvTransferReceiptDiscrepancyAdjudicationBasisIssueVo(
                issued.token(), Long.toString(prepared.caseId()),
                Long.toString(prepared.caseVersion()),
                Long.toString(selectedShopDeptId),
                DateTimeFormatter.ISO_INSTANT.format(prepared.issuedAt()),
                DateTimeFormatter.ISO_INSTANT.format(prepared.expiresAt()),
                "server");
    }

    private static boolean positive(Long value)
    {
        return value != null && value > 0;
    }

    private static ServiceException unavailable()
    {
        return new ServiceException("差异事项不存在或当前范围无权签发依据");
    }

    @FunctionalInterface
    interface EntropySource
    {
        byte[] next();
    }
}
