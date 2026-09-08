package com.erp.system.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;

/**
 * Fail-closed reader for business rollout switches stored in {@code sys_config}.
 * The switches only guard new entry points; callers must not use this gate to
 * stop workers or already-created workflow instances.
 */
@Service
public class BusinessFeatureGate
{
    public static final String HEALTH_CERTIFICATE = "feature.hr.health-certificate.enabled";
    public static final String STORE_RETURN = "feature.inventory.store-return.enabled";
    public static final String TRANSFER_DISCREPANCY = "feature.inventory.transfer-discrepancy.enabled";
    public static final String CUSTOMER_SERVICE_CARD = "feature.inventory.customer-service-card.enabled";
    public static final String SYSTEM_MANAGEMENT_UX_V2 = "feature.system.management-ux-v2.enabled";
    public static final String NOTICE_WORKFLOW = "feature.system.notice-workflow.enabled";
    public static final String ATTENDANCE_V2 = "feature.oa.attendance.v2.enabled";

    public static final String FEATURE_DISABLED = "FEATURE_DISABLED";
    public static final String FEATURE_CONFIG_UNAVAILABLE = "FEATURE_CONFIG_UNAVAILABLE";

    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on");
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "off");
    private static final Map<String, String> CAPABILITY_KEYS = capabilityKeys();

    private final ISysConfigService configService;

    public BusinessFeatureGate(ISysConfigService configService)
    {
        this.configService = configService;
    }

    public Decision inspect(String configKey)
    {
        try
        {
            String value = configService.selectConfigByKey(configKey);
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (TRUE_VALUES.contains(normalized))
            {
                return Decision.enabled();
            }
            if (normalized.isEmpty() || FALSE_VALUES.contains(normalized))
            {
                return Decision.disabled(false);
            }
            return Decision.disabled(true);
        }
        catch (RuntimeException ex)
        {
            return Decision.unavailable();
        }
    }

    public boolean isEnabled(String configKey)
    {
        return inspect(configKey).isEnabled();
    }

    public void requireEnabled(String configKey)
    {
        Decision decision = inspect(configKey);
        if (decision.isEnabled())
        {
            return;
        }
        String code = decision.isUnavailable() ? FEATURE_CONFIG_UNAVAILABLE : FEATURE_DISABLED;
        throw new ServiceException(code + ": " + configKey);
    }

    public Map<String, Boolean> capabilities()
    {
        Map<String, Boolean> result = new LinkedHashMap<>();
        CAPABILITY_KEYS.forEach((capability, configKey) -> result.put(capability, isEnabled(configKey)));
        return result;
    }

    public static Map<String, Boolean> disabledCapabilities()
    {
        Map<String, Boolean> result = new LinkedHashMap<>();
        CAPABILITY_KEYS.keySet().forEach(capability -> result.put(capability, false));
        return result;
    }

    private static Map<String, String> capabilityKeys()
    {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("healthCertificate", HEALTH_CERTIFICATE);
        result.put("storeReturn", STORE_RETURN);
        result.put("transferDiscrepancy", TRANSFER_DISCREPANCY);
        result.put("customerServiceCard", CUSTOMER_SERVICE_CARD);
        result.put("systemManagementUxV2", SYSTEM_MANAGEMENT_UX_V2);
        result.put("noticeWorkflow", NOTICE_WORKFLOW);
        result.put("attendanceV2", ATTENDANCE_V2);
        return Collections.unmodifiableMap(result);
    }

    public static final class Decision
    {
        private final boolean enabled;
        private final boolean unavailable;
        private final boolean invalid;

        private Decision(boolean enabled, boolean unavailable, boolean invalid)
        {
            this.enabled = enabled;
            this.unavailable = unavailable;
            this.invalid = invalid;
        }

        public static Decision enabled() { return new Decision(true, false, false); }
        public static Decision disabled(boolean invalid) { return new Decision(false, false, invalid); }
        public static Decision unavailable() { return new Decision(false, true, false); }
        public boolean isEnabled() { return enabled; }
        public boolean isUnavailable() { return unavailable; }
        public boolean isInvalid() { return invalid; }
    }
}
