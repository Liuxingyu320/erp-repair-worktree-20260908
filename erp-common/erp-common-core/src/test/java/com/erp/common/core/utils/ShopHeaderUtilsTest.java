package com.erp.common.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class ShopHeaderUtilsTest
{
    @Test
    void shouldResolveSelectedShopHeader()
    {
        assertThat(ShopHeaderUtils.resolveShopDeptId(null)).isNull();
        assertThat(ShopHeaderUtils.resolveShopDeptId(requestWithHeader(null))).isNull();
        assertThat(ShopHeaderUtils.resolveShopDeptId(requestWithHeader(""))).isNull();
        assertThat(ShopHeaderUtils.resolveShopDeptId(requestWithHeader("202"))).isEqualTo(202L);
    }

    private static HttpServletRequest requestWithHeader(String value)
    {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName()) && args != null
                            && ShopHeaderUtils.SHOP_HEADER.equals(args[0]))
                    {
                        return value;
                    }
                    if (method.getReturnType().isPrimitive())
                    {
                        return primitiveDefault(method.getReturnType());
                    }
                    return null;
                });
    }

    private static Object primitiveDefault(Class<?> type)
    {
        if (boolean.class.equals(type))
        {
            return false;
        }
        if (char.class.equals(type))
        {
            return '\0';
        }
        return 0;
    }
}
