package com.erp.oa.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteConfigService;

/** Fail-closed gate for OA new-business entry points. */
@Service
public class BusinessFeatureGate
{
    public static final String OA_PURCHASE = "feature.oa.purchase.enabled";
    public static final String PURCHASE = "feature.oa.purchase.enabled";
    public static final String REIMBURSEMENT =
            "feature.oa.reimbursement.enabled";
    /**
     * Attendance V2 is deliberately fail-closed until the schema, mobile
     * evidence flow and store pilot have all been accepted.
     */
    public static final String ATTENDANCE_V2 =
            "feature.oa.attendance.v2.enabled";
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "off");
    private final RemoteConfigService configService;

    public BusinessFeatureGate(RemoteConfigService configService)
    {
        this.configService = configService;
    }

    public boolean isEnabled(String configKey)
    {
        try
        {
            return read(configKey);
        }
        catch (ServiceException ex)
        {
            return false;
        }
    }

    public void requireEnabled(String configKey)
    {
        if (!read(configKey))
        {
            throw new ServiceException("FEATURE_DISABLED: " + configKey);
        }
    }

    private boolean read(String configKey)
    {
        final R<String> response;
        try
        {
            response = configService.getConfigKey(configKey, SecurityConstants.INNER);
        }
        catch (RuntimeException ex)
        {
            throw unavailable(configKey);
        }
        if (response == null || R.isError(response))
        {
            throw unavailable(configKey);
        }
        String normalized = response.getData() == null ? ""
                : response.getData().trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized))
        {
            return true;
        }
        if (normalized.isEmpty() || FALSE_VALUES.contains(normalized))
        {
            return false;
        }
        return false;
    }

    private ServiceException unavailable(String configKey)
    {
        return new ServiceException("FEATURE_CONFIG_UNAVAILABLE: " + configKey);
    }
}
