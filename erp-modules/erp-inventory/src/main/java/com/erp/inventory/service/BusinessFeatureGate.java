package com.erp.inventory.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.system.api.RemoteConfigService;

/** Fail-closed gate for inventory new-business entry points. */
@Service
public class BusinessFeatureGate
{
    public static final String STORE_RETURN = "feature.inventory.store-return.enabled";
    public static final String TRANSFER_DISCREPANCY = "feature.inventory.transfer-discrepancy.enabled";
    public static final String CUSTOMER_SERVICE_CARD = "feature.inventory.customer-service-card.enabled";
    public static final String CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS =
            "feature.inventory.customer-service-card.allowed-shop-dept-ids";
    public static final String STOCK_CHECK_NATIVE_APPROVAL =
            "feature.inventory.stock-check-native-approval.enabled";
    public static final String TRANSFER_NATIVE_APPROVAL =
            "feature.inventory.transfer-native-approval.enabled";
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

    public boolean isEnabledForShop(String configKey, String allowlistKey,
            Long shopDeptId)
    {
        try
        {
            return read(configKey) && shopAllowed(readValue(allowlistKey),
                    shopDeptId);
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

    /**
     * Allows a legacy endpoint only while its replacement is explicitly off.
     * Configuration failures are intentionally propagated so a cutover cannot
     * fail open and expose the retired contract.
     */
    public void requireDisabled(String configKey)
    {
        String normalized = readValue(configKey).toLowerCase(Locale.ROOT);
        if (FALSE_VALUES.contains(normalized))
        {
            return;
        }
        if (TRUE_VALUES.contains(normalized))
        {
            throw new ServiceException("FEATURE_REPLACED: " + configKey);
        }
        throw unavailable(configKey);
    }

    public void requireEnabledForShop(String configKey, String allowlistKey,
            Long shopDeptId)
    {
        requireEnabled(configKey);
        if (!shopAllowed(readValue(allowlistKey), shopDeptId))
        {
            throw new ServiceException("FEATURE_DISABLED_FOR_SHOP: "
                    + configKey);
        }
    }

    private boolean read(String configKey)
    {
        String normalized = readValue(configKey).toLowerCase(Locale.ROOT);
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

    private String readValue(String configKey)
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
        return response.getData() == null ? "" : response.getData().trim();
    }

    private boolean shopAllowed(String value, Long shopDeptId)
    {
        if (shopDeptId == null || shopDeptId <= 0 || StringUtils.isBlank(value))
        {
            return false;
        }
        String normalized = value.trim();
        boolean allowed = false;
        for (String item : normalized.split(",", -1))
        {
            String candidate = item.trim();
            if (!candidate.matches("[1-9][0-9]*"))
            {
                return false;
            }
            try
            {
                allowed |= Long.parseLong(candidate) == shopDeptId;
            }
            catch (NumberFormatException ex)
            {
                return false;
            }
        }
        return allowed;
    }

    private ServiceException unavailable(String configKey)
    {
        return new ServiceException("FEATURE_CONFIG_UNAVAILABLE: " + configKey);
    }
}
