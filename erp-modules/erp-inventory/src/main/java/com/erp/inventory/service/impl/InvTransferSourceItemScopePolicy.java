package com.erp.inventory.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvDeptScopeMapper;

/**
 * Resolves warehouse-replenishment product ownership from the actual source
 * warehouse. Return and cross-store transfers retain their independent rules.
 */
final class InvTransferSourceItemScopePolicy
{
    private final InvDeptScopeMapper deptScopeMapper;

    InvTransferSourceItemScopePolicy(InvDeptScopeMapper deptScopeMapper)
    {
        this.deptScopeMapper = deptScopeMapper;
    }

    Set<Long> resolveOwnerScopeDeptIds(InvTransferOrder order)
    {
        if (order == null || !InvTransferTypes.WAREHOUSE.equals(
                InvTransferTypes.requireSupported(order.getTransferType())))
        {
            return Set.of();
        }
        Long sourceDeptId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getFromWarehouseId(),
                        order.getFromDeptId());
        Long targetDeptId = InvTransferDirectionPolicy
                .resolveStockLocationDeptId(order.getToWarehouseId(),
                        order.getToDeptId());
        assertWarehouseReplenishmentRouteAllowed(sourceDeptId,
                targetDeptId);
        return resolveWarehouseOwnerScopeDeptIds(sourceDeptId);
    }

    void assertWarehouseReplenishmentRouteAllowed(Long sourceWarehouseId,
            Long targetStoreId)
    {
        if (sourceWarehouseId == null || sourceWarehouseId <= 0
                || targetStoreId == null || targetStoreId <= 0)
        {
            throw new ServiceException("补货供货关系无效");
        }
        Long sourceBusinessRoot = resolveBusinessRootDeptId(
                sourceWarehouseId);
        Long targetBusinessRoot = resolveBusinessRootDeptId(targetStoreId);
        if (!Objects.equals(sourceBusinessRoot, targetBusinessRoot))
        {
            throw new ServiceException("当前门店不允许向该仓库要货");
        }
    }

    Set<Long> resolveWarehouseOwnerScopeDeptIds(Long sourceDeptId)
    {
        if (sourceDeptId == null || sourceDeptId <= 0)
        {
            throw new ServiceException("补货来源仓库物料范围无效");
        }
        List<Long> relatedDeptIds = deptScopeMapper
                .selectActiveRelatedDeptIdsForReplenishment(sourceDeptId);
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        if (relatedDeptIds != null)
        {
            for (Long deptId : relatedDeptIds)
            {
                if (deptId != null && deptId > 0)
                {
                    normalized.add(deptId);
                }
            }
        }
        if (!normalized.contains(sourceDeptId))
        {
            throw new ServiceException("补货来源仓库物料范围无效");
        }
        return Set.copyOf(normalized);
    }

    void assertEligible(String transferType, InventoryItemSnapshot item,
            Set<Long> ownerScopeDeptIds, String message)
    {
        if (!InvTransferTypes.WAREHOUSE.equals(
                InvTransferTypes.requireSupported(transferType)))
        {
            return;
        }
        if (item == null)
        {
            throw new ServiceException(message);
        }
        if (!InvItemTypes.PRODUCT.equals(
                InvItemTypes.normalize(item.getItemType())))
        {
            return;
        }
        if (item.getOwnerDeptId() == null || ownerScopeDeptIds == null
                || !ownerScopeDeptIds.contains(item.getOwnerDeptId()))
        {
            throw new ServiceException(message);
        }
    }

    private Long resolveBusinessRootDeptId(Long deptId)
    {
        Long rootDeptId = deptScopeMapper.selectRawBusinessRootDeptId(deptId);
        if (rootDeptId == null || rootDeptId <= 0
                || deptScopeMapper.selectDeptTypeById(rootDeptId) == null)
        {
            throw new ServiceException("补货供货关系无效");
        }
        return rootDeptId;
    }
}
