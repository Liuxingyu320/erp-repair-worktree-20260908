package com.erp.common.security.shop;

import com.erp.common.core.web.domain.BaseEntity;
import java.util.List;
import java.util.Map;

public interface ShopScopeService
{
    Long resolveRequiredShopDept(Long selectedShopDeptId);

    List<Long> resolveScopeDeptIds(Long selectedShopDeptId);

    boolean hasUserShopScope(Long userId, Long deptId);

    default String resolveShopDeptName(Long deptId)
    {
        return null;
    }

    default void appendShopScope(BaseEntity entity, Long selectedShopDeptId)
    {
        if (entity != null)
        {
            appendShopScope(entity.getParams(), selectedShopDeptId);
        }
    }

    void appendShopScope(Map<String, Object> params, Long selectedShopDeptId);
}
