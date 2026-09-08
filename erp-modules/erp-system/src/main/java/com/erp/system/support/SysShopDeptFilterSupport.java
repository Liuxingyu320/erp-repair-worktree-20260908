package com.erp.system.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.erp.common.core.utils.ShopHeaderUtils;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.system.api.domain.SysUser;
import com.erp.system.service.ISysUserShopService;

/**
 * 店铺请求头查询过滤支持
 */
@Component
public class SysShopDeptFilterSupport
{
    @Autowired
    private ISysUserShopService userShopService;

    public void applyTo(SysUser user, HttpServletRequest request)
    {
        if (request == null || user == null)
        {
            return;
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        Long deptId = ShopHeaderUtils.resolveShopDeptId(request);
        if (deptId != null)
        {
            userShopService.checkUserShopScope(SecurityUtils.getUserId(), deptId, false);
            user.setDeptId(deptId);
        }
    }
}
