package com.erp.common.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;

class SignScopeHeaderUtilsTest
{
    @Test
    void shouldPreferDedicatedSignScopeHeader()
    {
        HttpServletRequest request = requestWithHeaders(Map.of(
                SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "1157",
                ShopHeaderUtils.SHOP_HEADER, "1185"));

        assertThat(SignScopeHeaderUtils.resolveSignScopeDeptId(request)).isEqualTo(1157L);
    }

    @Test
    void shouldRetainLegacyStoreHeaderFallback()
    {
        HttpServletRequest request = requestWithHeaders(Map.of(ShopHeaderUtils.SHOP_HEADER, "1185"));

        assertThat(SignScopeHeaderUtils.resolveSignScopeDeptId(request)).isEqualTo(1185L);
    }

    @Test
    void shouldRejectMalformedDedicatedHeaderInsteadOfFallingBack()
    {
        HttpServletRequest request = requestWithHeaders(Map.of(
                SignScopeHeaderUtils.SIGN_SCOPE_HEADER, "not-a-number",
                ShopHeaderUtils.SHOP_HEADER, "1185"));

        assertThatThrownBy(() -> SignScopeHeaderUtils.resolveSignScopeDeptId(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约组织编号");
    }

    private static HttpServletRequest requestWithHeaders(Map<String, String> headers)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName()) && args != null)
                    {
                        return headers.get(String.valueOf(args[0]));
                    }
                    if (method.getReturnType().isPrimitive())
                    {
                        if (boolean.class.equals(method.getReturnType())) return false;
                        if (char.class.equals(method.getReturnType())) return '\0';
                        return 0;
                    }
                    return null;
                });
    }
}
