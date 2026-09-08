package com.erp.oa.service.impl;

import java.util.List;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.mapper.OaSignTaskMapper;

/**
 * Centralizes the server-owned access boundary for sign packages.
 */
final class OaSignPackageAccessPolicy
{
    private final OaSignPackageMapper packageMapper;
    private final OaSignTaskMapper taskMapper;
    private final ShopScopeService shopScopeService;
    private final OaSignHrAccessService hrAccessService;

    OaSignPackageAccessPolicy(OaSignPackageMapper packageMapper,
            OaSignTaskMapper taskMapper,
            ShopScopeService shopScopeService,
            OaSignHrAccessService hrAccessService)
    {
        this.packageMapper = packageMapper;
        this.taskMapper = taskMapper;
        this.shopScopeService = shopScopeService;
        this.hrAccessService = hrAccessService;
    }

    OaSignPackage scopedPackage(Long packageId, Long selectedShopDeptId)
    {
        OaSignPackage signPackage = requirePackage(packageId);
        if (SecurityUtils.isAdmin())
        {
            return signPackage;
        }
        Long selected = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        if (selectedShopDeptId == null || selectedShopDeptId <= 0)
        {
            throw new ServiceException("请先选择店铺或仓库");
        }
        List<Long> scopeDeptIds = shopScopeService.resolveScopeDeptIds(selectedShopDeptId);
        if (scopeDeptIds != null && !scopeDeptIds.isEmpty())
        {
            if (signPackage.getShopDeptId() == null
                    || !scopeDeptIds.contains(signPackage.getShopDeptId()))
            {
                throw new ServiceException("无权访问该签约包");
            }
            return signPackage;
        }
        Long exactShopDeptId = selected != null && selected > 0
                ? selected : selectedShopDeptId;
        if (signPackage.getShopDeptId() == null
                || !Objects.equals(signPackage.getShopDeptId(), exactShopDeptId))
        {
            throw new ServiceException("无权访问该签约包");
        }
        return signPackage;
    }

    OaSignPackage hrScopedPackage(Long packageId, Long selectedShopDeptId)
    {
        OaSignPackage signPackage = scopedPackage(packageId, selectedShopDeptId);
        requireBoundTaskOwner(signPackage);
        return signPackage;
    }

    void requireBoundTaskOwner(OaSignPackage signPackage)
    {
        if (signPackage.getTaskId() == null)
        {
            return;
        }
        OaSignTask task = taskMapper.selectOaSignTaskById(signPackage.getTaskId());
        if (task == null || !Objects.equals(task.getPackageId(), signPackage.getPackageId()))
        {
            task = null;
        }
        hrAccessService.requireTaskOwner(task);
    }

    OaSignPackage employeePackage(Long packageId)
    {
        OaSignPackage signPackage = requirePackage(packageId);
        if (!Objects.equals(signPackage.getEmployeeId(), SecurityUtils.getUserId()))
        {
            throw new ServiceException("只能查看和签署本人的签约包");
        }
        return signPackage;
    }

    private OaSignPackage requirePackage(Long packageId)
    {
        OaSignPackage signPackage = packageMapper.selectOaSignPackageById(packageId);
        if (signPackage == null)
        {
            throw new ServiceException("签约包不存在");
        }
        return signPackage;
    }
}
