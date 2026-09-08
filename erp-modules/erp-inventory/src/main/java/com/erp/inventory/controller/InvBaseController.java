package com.erp.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.core.web.controller.BaseController;

public abstract class InvBaseController extends BaseController
{
    protected Long resolveShopDeptId(HttpServletRequest request)
    {
        return ShopHeaderUtils.resolveShopDeptId(request);
    }
}
