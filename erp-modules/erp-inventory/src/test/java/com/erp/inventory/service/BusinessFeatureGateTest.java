package com.erp.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.RemoteConfigService;

class BusinessFeatureGateTest
{
    @Test
    void everyNonTrueValueIsDisabledAndRemoteFailureIsUnavailable()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        BusinessFeatureGate gate = new BusinessFeatureGate(config);
        when(config.getConfigKey(BusinessFeatureGate.STORE_RETURN,
                SecurityConstants.INNER)).thenReturn(R.ok("maybe"));

        assertThat(gate.isEnabled(BusinessFeatureGate.STORE_RETURN)).isFalse();
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.STORE_RETURN))
                .isInstanceOf(ServiceException.class)
                .hasMessage("FEATURE_DISABLED: " + BusinessFeatureGate.STORE_RETURN);

        when(config.getConfigKey(BusinessFeatureGate.STORE_RETURN,
                SecurityConstants.INNER)).thenThrow(new IllegalStateException("timeout"));
        assertThatThrownBy(() -> gate.requireEnabled(BusinessFeatureGate.STORE_RETURN))
                .hasMessage("FEATURE_CONFIG_UNAVAILABLE: " + BusinessFeatureGate.STORE_RETURN);
    }

    @Test
    void explicitTrueEnablesInventoryEntry()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("1"));
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.isEnabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD)).isTrue();
    }

    @Test
    void customerCardWriteRequiresExplicitShopAllowlist()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("true"));
        when(config.getConfigKey(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                SecurityConstants.INNER)).thenReturn(R.ok("10, 12"));
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.isEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                10L)).isTrue();
        assertThat(gate.isEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                11L)).isFalse();
        assertThatThrownBy(() -> gate.requireEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                11L))
                .hasMessage("FEATURE_DISABLED_FOR_SHOP: "
                        + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);
    }

    @Test
    void blankWildcardAndMalformedAllowlistsDenyEveryShop()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("true"));
        when(config.getConfigKey(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                SecurityConstants.INNER)).thenReturn(R.ok(""));
        BusinessFeatureGate gate = new BusinessFeatureGate(config);

        assertThat(gate.isEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                10L)).isFalse();

        when(config.getConfigKey(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                SecurityConstants.INNER)).thenReturn(R.ok("*"));
        assertThat(gate.isEnabledForShop(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                999L)).isFalse();

        for (String malformed : new String[] {
                "10,garbage", "10,", "10,,12", "0,10", "-1,10",
                "10,9223372036854775808" })
        {
            when(config.getConfigKey(
                    BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                    SecurityConstants.INNER)).thenReturn(R.ok(malformed));
            assertThat(gate.isEnabledForShop(
                    BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                    BusinessFeatureGate.CUSTOMER_SERVICE_CARD_ALLOWED_SHOPS,
                    10L)).as(malformed).isFalse();
        }
    }

    @Test
    void legacyApiGuardFailsClosedAfterCutoverOrConfigFailure()
    {
        RemoteConfigService config = mock(RemoteConfigService.class);
        BusinessFeatureGate gate = new BusinessFeatureGate(config);
        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("true"));

        assertThatThrownBy(() -> gate.requireDisabled(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .hasMessage("FEATURE_REPLACED: "
                        + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);

        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenThrow(new IllegalStateException(
                        "timeout"));
        assertThatThrownBy(() -> gate.requireDisabled(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .hasMessage("FEATURE_CONFIG_UNAVAILABLE: "
                        + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);

        reset(config);
        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("maybe"));
        assertThatThrownBy(() -> gate.requireDisabled(
                BusinessFeatureGate.CUSTOMER_SERVICE_CARD))
                .hasMessage("FEATURE_CONFIG_UNAVAILABLE: "
                        + BusinessFeatureGate.CUSTOMER_SERVICE_CARD);

        when(config.getConfigKey(BusinessFeatureGate.CUSTOMER_SERVICE_CARD,
                SecurityConstants.INNER)).thenReturn(R.ok("false"));
        gate.requireDisabled(BusinessFeatureGate.CUSTOMER_SERVICE_CARD);
    }

}
