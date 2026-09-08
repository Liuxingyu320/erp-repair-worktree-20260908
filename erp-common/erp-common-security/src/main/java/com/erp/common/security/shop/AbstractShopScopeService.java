package com.erp.common.security.shop;

import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractShopScopeService implements ShopScopeService
{
    @Override
    public Long resolveRequiredShopDept(Long selectedShopDeptId)
    {
        Long shopDeptId = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin() && !hasUserShopScope(SecurityUtils.getUserId(), shopDeptId))
        {
            throw new ServiceException("当前用户无权选择该店铺", HttpStatus.FORBIDDEN);
        }
        return shopDeptId;
    }

    @Override
    public List<Long> resolveScopeDeptIds(Long selectedShopDeptId)
    {
        if (SecurityUtils.isAdmin() && (selectedShopDeptId == null || selectedShopDeptId == 0))
        {
            return Collections.emptyList();
        }
        Long scopeRoot = resolveRequiredShopDept(selectedShopDeptId);
        List<Long> scopeDeptIds = selectSubDeptIds(scopeRoot);
        return scopeDeptIds == null ? Collections.emptyList() : scopeDeptIds;
    }

    @Override
    public boolean hasUserShopScope(Long userId, Long deptId)
    {
        return userId != null && deptId != null && countUserShopScope(userId, deptId) > 0;
    }

    @Override
    public void appendShopScope(Map<String, Object> params, Long selectedShopDeptId)
    {
        if (params == null)
        {
            return;
        }
        List<Long> scopeDeptIds = resolveScopeDeptIds(selectedShopDeptId);
        if (!scopeDeptIds.isEmpty())
        {
            params.put("scopeDeptIds", scopeDeptIds);
        }
    }

    protected Long requireSelectedShopDept(Long selectedShopDeptId)
    {
        if (selectedShopDeptId == null || selectedShopDeptId == 0)
        {
            throw new ServiceException("请先选择店铺或仓库");
        }
        return selectedShopDeptId;
    }

    protected abstract int countUserShopScope(Long userId, Long deptId);

    protected abstract List<Long> selectSubDeptIds(Long deptId);
}
