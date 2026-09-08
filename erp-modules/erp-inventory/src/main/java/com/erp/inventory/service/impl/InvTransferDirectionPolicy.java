package com.erp.inventory.service.impl;

import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.mapper.InvDeptScopeMapper;

/**
 * Centralizes the organization-direction rules shared by transfer creation,
 * delivery and receipt.
 */
final class InvTransferDirectionPolicy extends InvBaseService
{
    InvTransferDirectionPolicy(InvDeptScopeMapper deptScopeMapper,
            ShopScopeService shopScopeService)
    {
        this.deptScopeMapper = deptScopeMapper;
        this.shopScopeService = shopScopeService;
    }

    void validateCreation(InvTransferOrder order, Long selectedDeptId,
            boolean salesDeliveryInTransit)
    {
        Long sourceStockDeptId = resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
        Long targetStockDeptId = resolveStockLocationDeptId(
                order.getToWarehouseId(), order.getToDeptId());
        Long sourceBusinessDeptId = order.getFromDeptId();
        Long targetBusinessDeptId = order.getToDeptId();
        String transferType = InvTransferTypes.requireSupported(
                order.getTransferType());
        order.setTransferType(transferType);
        if (InvTransferTypes.WAREHOUSE.equals(transferType))
        {
            assertDeptType(selectedDeptId, DEPT_TYPE_STORE,
                    "门店才能发起补货申请");
            assertDeptType(sourceBusinessDeptId, DEPT_TYPE_WAREHOUSE,
                    "补货业务来源必须为仓库");
            assertDeptType(targetBusinessDeptId, DEPT_TYPE_STORE,
                    "补货业务目标必须为门店");
            assertDeptType(sourceStockDeptId, DEPT_TYPE_WAREHOUSE,
                    "补货来源必须为仓库");
            assertDeptType(targetStockDeptId, DEPT_TYPE_STORE,
                    "补货目标必须为门店");
            requireSameStockLocation(sourceBusinessDeptId,
                    sourceStockDeptId,
                    "补货业务来源与实际发货仓库不一致");
            requireSameStockLocation(targetBusinessDeptId,
                    targetStockDeptId,
                    "补货业务目标与实际收货位置不一致");
            if (!selectedDeptId.equals(targetBusinessDeptId))
            {
                throw new ServiceException("补货目标必须为当前门店");
            }
            return;
        }
        if (InvTransferTypes.STORE_RETURN.equals(transferType))
        {
            assertDeptType(selectedDeptId, DEPT_TYPE_STORE,
                    "门店才能发起返仓");
            assertDeptType(sourceBusinessDeptId, DEPT_TYPE_STORE,
                    "返仓业务来源必须为门店");
            assertDeptType(targetBusinessDeptId, DEPT_TYPE_WAREHOUSE,
                    "返仓业务目标必须为仓库");
            assertDeptType(sourceStockDeptId, DEPT_TYPE_STORE,
                    "返仓来源必须为门店");
            assertDeptType(targetStockDeptId, DEPT_TYPE_WAREHOUSE,
                    "返仓目标必须为仓库");
            requireSameStockLocation(sourceBusinessDeptId,
                    sourceStockDeptId,
                    "返仓业务来源与实际出库位置不一致");
            requireSameStockLocation(targetBusinessDeptId,
                    targetStockDeptId,
                    "返仓业务目标与实际收货仓库不一致");
            if (!selectedDeptId.equals(sourceBusinessDeptId))
            {
                throw new ServiceException("返仓来源必须为当前门店");
            }
            if (isBlank(order.getReturnReasonCode()))
            {
                throw new ServiceException("请选择返仓原因");
            }
            if ("OTHER".equals(order.getReturnReasonCode())
                    && isBlank(order.getReturnReasonText()))
            {
                throw new ServiceException("请填写其他返仓原因");
            }
            return;
        }
        if (InvTransferTypes.CROSS_STORE.equals(transferType))
        {
            assertDeptType(sourceBusinessDeptId, DEPT_TYPE_STORE,
                    "异店调货来源必须为门店");
            assertDeptType(targetBusinessDeptId, DEPT_TYPE_STORE,
                    "异店调货目标必须为门店");
            if (sourceBusinessDeptId.equals(targetBusinessDeptId))
            {
                throw new ServiceException("异店调货来源和目标不能相同");
            }
            if (!targetBusinessDeptId.equals(targetStockDeptId))
            {
                throw new ServiceException("异店调货收货库存必须属于目标门店");
            }
            if (salesDeliveryInTransit)
            {
                return;
            }
            assertDeptType(selectedDeptId, DEPT_TYPE_STORE,
                    "门店才能发起异店调货");
            if (!selectedDeptId.equals(targetBusinessDeptId))
            {
                throw new ServiceException("异店调货目标必须为当前门店");
            }
            if (!sourceBusinessDeptId.equals(sourceStockDeptId))
            {
                throw new ServiceException("人工异店调货库存必须属于来源门店");
            }
            if (!SecurityUtils.isAdmin())
            {
                assertUserShopScope(sourceBusinessDeptId);
            }
        }
    }

    void validateDelivery(InvTransferOrder order, Long selectedShopDeptId)
    {
        Long selectedDeptId = requireSelectedShopDept(selectedShopDeptId);
        Long sourceDeptId = resolveStockLocationDeptId(
                order.getFromWarehouseId(), order.getFromDeptId());
        if (InvTransferTypes.WAREHOUSE.equals(order.getTransferType()))
        {
            assertDeptType(sourceDeptId, DEPT_TYPE_WAREHOUSE,
                    "调拨来源必须为仓库");
            requireSelectedDirection(selectedDeptId, sourceDeptId,
                    DEPT_TYPE_WAREHOUSE, "只能由来源仓库发货");
            return;
        }
        if (InvTransferTypes.STORE_RETURN.equals(order.getTransferType()))
        {
            assertDeptType(sourceDeptId, DEPT_TYPE_STORE,
                    "返仓来源必须为门店");
            requireSelectedDirection(selectedDeptId, sourceDeptId,
                    DEPT_TYPE_STORE, "只能由来源门店发货");
            return;
        }
        if (InvTransferTypes.CROSS_STORE.equals(order.getTransferType()))
        {
            assertDeptType(sourceDeptId, DEPT_TYPE_STORE,
                    "异店调货来源必须为门店");
            requireSelectedDirection(selectedDeptId, sourceDeptId,
                    DEPT_TYPE_STORE, "只能由来源门店发货");
        }
    }

    void validateDraftManagement(InvTransferOrder order,
            Long selectedShopDeptId)
    {
        Long selectedDeptId = requireSelectedShopDept(selectedShopDeptId);
        String transferType = InvTransferTypes.requireSupported(
                order.getTransferType());
        if (InvTransferTypes.WAREHOUSE.equals(transferType))
        {
            if ("oe_replenishment".equals(order.getSourceBusinessType()))
            {
                Long sourceStockDeptId = resolveStockLocationDeptId(
                        order.getFromWarehouseId(), order.getFromDeptId());
                requireSelectedDirection(selectedDeptId, sourceStockDeptId,
                        DEPT_TYPE_WAREHOUSE,
                        "只有来源仓库可以管理OE补货调拨单");
                return;
            }
            requireSelectedDirection(selectedDeptId, order.getToDeptId(),
                    DEPT_TYPE_STORE,
                    "只有要货门店可以管理该调拨单");
            return;
        }
        if (InvTransferTypes.STORE_RETURN.equals(transferType))
        {
            requireSelectedDirection(selectedDeptId, order.getFromDeptId(),
                    DEPT_TYPE_STORE,
                    "只有返仓门店可以管理该调拨单");
            return;
        }
        requireSelectedDirection(selectedDeptId, order.getToDeptId(),
                DEPT_TYPE_STORE,
                "只有调入门店可以管理该异店调拨单");
    }

    void validateReceipt(InvTransferOrder order, Long selectedShopDeptId)
    {
        Long selectedDeptId = requireSelectedShopDept(selectedShopDeptId);
        Long targetDeptId = resolveStockLocationDeptId(
                order.getToWarehouseId(), order.getToDeptId());
        if (InvTransferTypes.WAREHOUSE.equals(order.getTransferType()))
        {
            assertDeptType(targetDeptId, DEPT_TYPE_STORE,
                    "调拨目标必须为门店");
            requireSelectedDirection(selectedDeptId, targetDeptId,
                    DEPT_TYPE_STORE, "只能由目标门店收货");
            return;
        }
        if (InvTransferTypes.STORE_RETURN.equals(order.getTransferType()))
        {
            assertDeptType(targetDeptId, DEPT_TYPE_WAREHOUSE,
                    "返仓目标必须为仓库");
            requireSelectedDirection(selectedDeptId, targetDeptId,
                    DEPT_TYPE_WAREHOUSE, "只能由目标仓库收货");
            return;
        }
        if (InvTransferTypes.CROSS_STORE.equals(order.getTransferType()))
        {
            assertDeptType(targetDeptId, DEPT_TYPE_STORE,
                    "异店调货目标必须为门店");
            requireSelectedDirection(selectedDeptId, targetDeptId,
                    DEPT_TYPE_STORE, "只能由目标门店收货");
        }
    }

    static Long resolveStockLocationDeptId(Long warehouseId, Long deptId)
    {
        return warehouseId != null && warehouseId != 0 ? warehouseId : deptId;
    }

    private void requireSelectedDirection(Long selectedDeptId,
            Long expectedDeptId, String deptType, String message)
    {
        if (!selectedDeptId.equals(expectedDeptId))
        {
            throw new ServiceException(message);
        }
        assertDeptType(selectedDeptId, deptType, message);
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private void requireSameStockLocation(Long businessDeptId,
            Long stockDeptId, String message)
    {
        if (businessDeptId == null || !businessDeptId.equals(stockDeptId))
        {
            throw new ServiceException(message);
        }
    }
}
