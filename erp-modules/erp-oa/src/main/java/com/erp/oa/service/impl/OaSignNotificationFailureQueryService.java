package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.vo.OaSignNotificationFailure;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;

/** Read-only, signing-scope query for notification failures shown in the HR task center. */
@Service
public class OaSignNotificationFailureQueryService
{
    private static final int MAX_RESULTS = 100;
    private static final Pattern BUSINESS_KEY = Pattern.compile("[A-Za-z0-9:._+\\-]{1,180}");

    private final OaSignNotificationOutboxMapper outboxMapper;
    private final ShopScopeService signScopeService;
    private final OaSignHrAccessService signHrAccessService;

    public OaSignNotificationFailureQueryService(OaSignNotificationOutboxMapper outboxMapper,
            @Qualifier("oaSignScopeService") ShopScopeService signScopeService,
            OaSignHrAccessService signHrAccessService)
    {
        this.outboxMapper = outboxMapper;
        this.signScopeService = signScopeService;
        this.signHrAccessService = signHrAccessService;
    }

    public List<OaSignNotificationFailure> list(Long selectedSignScopeDeptId)
    {
        signHrAccessService.requireCurrentHr();
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null || currentUserId <= 0)
        {
            throw new ServiceException("无法识别当前合同经办人");
        }
        List<Long> scopeDeptIds = signScopeService.resolveScopeDeptIds(selectedSignScopeDeptId);
        if (scopeDeptIds == null || scopeDeptIds.isEmpty())
        {
            return Collections.emptyList();
        }
        return sanitize(outboxMapper.selectNotificationFailuresForHr(
                currentUserId, scopeDeptIds, MAX_RESULTS));
    }

    private List<OaSignNotificationFailure> sanitize(List<OaSignNotificationFailure> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return Collections.emptyList();
        }
        List<OaSignNotificationFailure> safeRows = new ArrayList<>();
        Set<Long> taskIds = new LinkedHashSet<>();
        for (OaSignNotificationFailure row : rows)
        {
            if (row == null || row.getTaskId() == null || row.getTaskId() <= 0
                    || row.getNotificationBusinessKey() == null
                    || !BUSINESS_KEY.matcher(row.getNotificationBusinessKey()).matches()
                    || !taskIds.add(row.getTaskId()))
            {
                continue;
            }
            OaSignNotificationFailure safe = new OaSignNotificationFailure();
            safe.setTaskId(row.getTaskId());
            safe.setNotificationBusinessKey(row.getNotificationBusinessKey());
            safeRows.add(safe);
        }
        return safeRows;
    }
}
