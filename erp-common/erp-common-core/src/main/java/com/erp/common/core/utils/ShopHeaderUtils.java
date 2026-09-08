package com.erp.common.core.utils;

import jakarta.servlet.http.HttpServletRequest;
import com.erp.common.core.text.Convert;

public class ShopHeaderUtils
{
    public static final String SHOP_HEADER = "Dept-NumId";

    private ShopHeaderUtils()
    {
    }

    public static Long resolveShopDeptId(HttpServletRequest request)
    {
        if (request == null)
        {
            return null;
        }
        String shopDeptId = request.getHeader(SHOP_HEADER);
        if (StringUtils.isEmpty(shopDeptId))
        {
            return null;
        }
        return Convert.toLong(shopDeptId);
    }
}
