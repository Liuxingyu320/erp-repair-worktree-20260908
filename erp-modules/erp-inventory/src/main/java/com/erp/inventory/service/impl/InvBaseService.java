package com.erp.inventory.service.impl;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.web.domain.BaseEntity;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.mapper.InvDeptScopeMapper;

public abstract class InvBaseService
{
    protected static final String DEPT_TYPE_STORE = "STORE";
    protected static final String DEPT_TYPE_WAREHOUSE = "WAREHOUSE";

    @Autowired
    protected InvDeptScopeMapper deptScopeMapper;

    @Autowired
    protected ShopScopeService shopScopeService;

    protected Long resolveAndValidateShopDept(Long selectedShopDeptId)
    {
        if (shopScopeService != null)
        {
            return shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        }
        Long shopDeptId = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(shopDeptId);
        }
        return shopDeptId;
    }

    protected void assertShopVisible(Long shopDeptId, Long selectedShopDeptId, String errorMsg)
    {
        Long rootDeptId = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(rootDeptId);
        }
        int visible = deptScopeMapper.countDeptInScope(rootDeptId, shopDeptId);
        if (visible <= 0)
        {
            throw new ServiceException(errorMsg);
        }
    }

    protected void assertRelatedShopVisible(Long shopDeptId, Long selectedShopDeptId, String errorMsg)
    {
        Long rootDeptId = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(rootDeptId);
        }
        List<Long> scopeDeptIds = deptScopeMapper.selectRelatedDeptIds(rootDeptId);
        if (scopeDeptIds == null || !scopeDeptIds.contains(shopDeptId))
        {
            throw new ServiceException(errorMsg);
        }
    }

    protected void appendShopScope(BaseEntity entity, Long selectedShopDeptId)
    {
        if (shopScopeService != null)
        {
            shopScopeService.appendShopScope(entity, selectedShopDeptId);
            return;
        }
        if (entity != null)
        {
            appendShopScopeToParams(entity.getParams(), selectedShopDeptId);
        }
    }

    protected void appendShopScopeToParams(Map<String, Object> params, Long selectedShopDeptId)
    {
        if (shopScopeService != null)
        {
            shopScopeService.appendShopScope(params, selectedShopDeptId);
            return;
        }
        if (params == null)
        {
            return;
        }
        if (SecurityUtils.isAdmin() && (selectedShopDeptId == null || selectedShopDeptId == 0))
        {
            return;
        }
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        List<Long> scopeDeptIds = deptScopeMapper.selectSubDeptIds(scopeRoot);
        if (scopeDeptIds != null && !scopeDeptIds.isEmpty())
        {
            params.put("scopeDeptIds", scopeDeptIds);
        }
    }

    protected List<Long> resolveRelatedScopeDeptIds(Long selectedShopDeptId)
    {
        Long scopeRoot = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(scopeRoot);
        }
        return deptScopeMapper.selectRelatedDeptIds(scopeRoot);
    }

    /**
     * Resolves the active store and warehouse organizations the current user
     * may operate. Unlike a parent/child tree check, this scope also preserves
     * legitimate sibling organizations granted through a company or group.
     */
    protected List<Long> resolveAuthorizedInventoryDeptIds()
    {
        List<Long> deptIds;
        if (SecurityUtils.isAdmin())
        {
            deptIds = deptScopeMapper.selectAllActiveInventoryDeptIds();
        }
        else
        {
            Long userId = SecurityUtils.getUserId();
            deptIds = userId == null
                    ? Collections.emptyList()
                    : deptScopeMapper.selectUserAuthorizedInventoryDeptIds(
                            userId);
        }
        if (deptIds == null || deptIds.isEmpty())
        {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long deptId : deptIds)
        {
            if (deptId != null && deptId > 0)
            {
                normalized.add(deptId);
            }
        }
        return List.copyOf(normalized);
    }

    protected void assertAuthorizedInventoryDept(Long inventoryDeptId,
            List<Long> authorizedDeptIds, String message)
    {
        if (inventoryDeptId == null || inventoryDeptId <= 0
                || authorizedDeptIds == null
                || !authorizedDeptIds.contains(inventoryDeptId))
        {
            throw new ServiceException(message);
        }
    }

    protected void appendRelatedShopScope(BaseEntity entity, Long selectedShopDeptId)
    {
        if (entity != null)
        {
            entity.getParams().put("scopeDeptIds", resolveRelatedScopeDeptIds(selectedShopDeptId));
        }
    }

    protected Long requireWarehouseContext(Long selectedDeptId, String message)
    {
        Long deptId = resolveAndValidateShopDept(selectedDeptId);
        assertDeptType(deptId, DEPT_TYPE_WAREHOUSE, message);
        return deptId;
    }

    protected Long requireStoreContext(Long selectedDeptId, String message)
    {
        Long deptId = resolveAndValidateShopDept(selectedDeptId);
        assertDeptType(deptId, DEPT_TYPE_STORE, message);
        return deptId;
    }

    protected boolean isWarehouseDept(Long deptId)
    {
        return DEPT_TYPE_WAREHOUSE.equals(deptScopeMapper.selectDeptTypeById(deptId));
    }

    protected boolean isStoreDept(Long deptId)
    {
        return DEPT_TYPE_STORE.equals(deptScopeMapper.selectDeptTypeById(deptId));
    }

    protected void assertDeptType(Long deptId, String deptType, String message)
    {
        if (deptId == null || deptId == 0)
        {
            throw new ServiceException(message);
        }
        String actualDeptType = deptScopeMapper.selectDeptTypeById(deptId);
        if (!deptType.equals(actualDeptType))
        {
            throw new ServiceException(message);
        }
    }

    protected void assertWritableInventoryDept(Long inventoryDeptId, Long selectedDeptId, String message)
    {
        Long selected = resolveAndValidateShopDept(selectedDeptId);
        Long target = inventoryDeptId == null || inventoryDeptId == 0 ? selected : inventoryDeptId;
        if (!selected.equals(target))
        {
            throw new ServiceException(message);
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

    protected void assertUserShopScope(Long shopDeptId)
    {
        Long userId = SecurityUtils.getUserId();
        boolean visible = shopScopeService != null
                ? shopScopeService.hasUserShopScope(userId, shopDeptId)
                : userId != null && shopDeptId != null && deptScopeMapper.countUserShopScope(userId, shopDeptId) > 0;
        if (!visible)
        {
            throw new ServiceException("当前用户无权选择该店铺");
        }
    }
}
