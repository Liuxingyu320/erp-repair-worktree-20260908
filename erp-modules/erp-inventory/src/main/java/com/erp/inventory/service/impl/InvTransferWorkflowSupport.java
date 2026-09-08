package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;
import com.erp.inventory.domain.transfer.InvTransferQuantityPolicy;

/** Shared infrastructure for transfer receipt and discrepancy processors. */
abstract class InvTransferWorkflowSupport extends InvBaseService
{
    protected final InvTransferWorkflowResources resources;
    protected final InvTransferDirectionPolicy directionPolicy;

    InvTransferWorkflowSupport(InvTransferWorkflowResources resources,
            InvTransferDirectionPolicy directionPolicy)
    {
        this.resources = resources;
        this.directionPolicy = directionPolicy;
        this.deptScopeMapper = resources.deptScopeMapper;
        this.shopScopeService = resources.shopScopeService;
    }

    protected InvTransferOrder assertAndGetScopedTransfer(Long transferId,
            Long selectedShopDeptId)
    {
        Long scopeRoot = resolveAndValidateShopDept(selectedShopDeptId);
        InvTransferOrder order = resources.transferOrderMapper
                .selectInvTransferOrderById(transferId);
        if (order == null)
        {
            throw new ServiceException("调拨单不存在");
        }
        if (!isDeptVisible(order.getFromDeptId(), scopeRoot)
                && !isDeptVisible(order.getToDeptId(), scopeRoot)
                && !isDeptVisible(order.getFromWarehouseId(), scopeRoot)
                && !isDeptVisible(order.getToWarehouseId(), scopeRoot))
        {
            throw new ServiceException("无权访问该调拨单");
        }
        return order;
    }

    protected void assertTransferReceiveScope(InvTransferOrder order,
            Long selectedShopDeptId)
    {
        assertShopVisible(resolveStockLocationDeptId(
                order.getToWarehouseId(), order.getToDeptId()),
                selectedShopDeptId, "无权操作该调拨单的目标门店");
    }

    protected void addTargetStockAndLog(InvTransferOrder order,
            String itemType, Long itemId, Long productId,
            Long targetWarehouseId, BigDecimal receivedQty,
            BigDecimal costPrice, String remark)
    {
        Long targetLocationDeptId = resolveStockLocationDeptId(
                targetWarehouseId, order.getToDeptId());
        addStockAtLocationAndLog(order, itemType, itemId, productId,
                targetLocationDeptId, receivedQty, costPrice, remark);
    }

    protected void addStockAtLocationAndLog(InvTransferOrder order,
            String itemType, Long itemId, Long productId,
            Long locationDeptId, BigDecimal quantity,
            BigDecimal costPrice, String remark)
    {
        BigDecimal incomingCost = quantity.multiply(costPrice);
        InvStock stock = resources.stockMapper
                .selectInvStockByItemShopWarehouseForUpdate(
                        itemType, itemId, locationDeptId, locationDeptId);
        BigDecimal beforeQty = stock != null
                ? stock.getCurrentQuantity() : BigDecimal.ZERO;
        if (stock == null)
        {
            stock = new InvStock();
            stock.setItemType(itemType);
            stock.setItemId(itemId);
            stock.setProductId(productId);
            stock.setShopDeptId(locationDeptId);
            stock.setWarehouseId(locationDeptId);
            stock.setCurrentQuantity(quantity);
            stock.setAvailableQuantity(quantity);
            stock.setLockedQuantity(BigDecimal.ZERO);
            stock.setCostPrice(costPrice);
            stock.setTotalCost(incomingCost);
            stock.setCreateBy(SecurityUtils.getUsername());
            stock.setCreateTime(new Date());
            try
            {
                if (resources.stockMapper.insertInvStock(stock) != 1)
                {
                    throw new ServiceException("创建库存失败，请重试");
                }
            }
            catch (org.springframework.dao.DuplicateKeyException e)
            {
                stock = resources.stockMapper
                        .selectInvStockByItemShopWarehouseForUpdate(
                                itemType, itemId, locationDeptId,
                                locationDeptId);
                if (stock == null)
                {
                    throw new ServiceException("创建库存失败，请重试");
                }
                beforeQty = stock.getCurrentQuantity();
                if (resources.stockMapper.addInvStockWithCost(
                        stock.getStockId(), stock.getVersion(), quantity,
                        incomingCost, SecurityUtils.getUsername()) != 1)
                {
                    throw new ServiceException("库存增加失败，请刷新后重试");
                }
                stock = resources.stockMapper.selectInvStockById(
                        stock.getStockId());
            }
        }
        else
        {
            if (resources.stockMapper.addInvStockWithCost(
                    stock.getStockId(), stock.getVersion(), quantity,
                    incomingCost, SecurityUtils.getUsername()) != 1)
            {
                throw new ServiceException("库存增加失败，请刷新后重试");
            }
            stock = resources.stockMapper.selectInvStockById(
                    stock.getStockId());
        }
        BigDecimal afterQty = stock != null
                ? stock.getCurrentQuantity() : beforeQty.add(quantity);
        writeStockLog(itemType, itemId, productId, locationDeptId,
                locationDeptId, InvStatusConstants.MOVEMENT_TRANSFER_IN,
                InvTransferTypes.stockBusinessType(order.getTransferType(),
                        order.getSourceBusinessType()),
                order.getTransferId(), order.getOrderNo(), quantity,
                beforeQty, afterQty, costPrice, remark);
    }

    protected void writeStatusLog(Long transferId, String fromStatus,
            String toStatus, String action, String reason)
    {
        InvTransferStatusLog log = new InvTransferStatusLog();
        log.setTransferId(transferId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setAction(action);
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorName(SecurityUtils.getUsername());
        log.setReason(reason);
        resources.statusLogMapper.insertLog(log);
    }

    protected void recordTransferDiscrepancy(String outcome)
    {
        if (resources.businessMetrics != null)
        {
            resources.businessMetrics.recordTransferDiscrepancy(outcome);
        }
    }

    protected String generateOrderNo(String prefix)
    {
        String date = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        resources.numberSequenceMapper.insertOrUpdateSequence(
                prefix, date, 0, prefix);
        resources.numberSequenceMapper.incrementAndGetSequence(prefix, date);
        Long sequence = resources.numberSequenceMapper.selectLastInsertId();
        return prefix + date + String.format("%04d", sequence);
    }

    protected boolean allRequestedReceived(List<InvTransferDetail> details)
    {
        for (InvTransferDetail detail : details)
        {
            if (!InvTransferQuantityPolicy.isFullyAccounted(
                    detail.getQuantity(), detail.getReceivedQuantity(),
                    "累计收货数量不能超过审批数量"))
            {
                return false;
            }
        }
        return !details.isEmpty();
    }

    protected static Long resolveStockLocationDeptId(Long warehouseId,
            Long deptId)
    {
        return InvTransferDirectionPolicy.resolveStockLocationDeptId(
                warehouseId, deptId);
    }

    protected static BigDecimal nullToZero(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    protected static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private boolean isDeptVisible(Long deptId, Long scopeRoot)
    {
        return deptId != null
                && resources.deptScopeMapper.countDeptInScope(
                        scopeRoot, deptId) > 0;
    }

    private void writeStockLog(String itemType, Long itemId,
            Long productId, Long shopDeptId, Long warehouseId,
            String movementType, String businessType, Long businessId,
            String businessNo, BigDecimal changeQty, BigDecimal beforeQty,
            BigDecimal afterQty, BigDecimal costPrice, String remark)
    {
        InvStockLog log = new InvStockLog();
        log.setItemType(itemType);
        log.setItemId(itemId);
        log.setProductId(productId);
        log.setShopDeptId(shopDeptId);
        log.setWarehouseId(warehouseId);
        log.setMovementType(movementType);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setBusinessNo(businessNo);
        log.setChangeQuantity(changeQty);
        log.setBeforeQuantity(beforeQty);
        log.setAfterQuantity(afterQty);
        log.setCostPrice(costPrice);
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        log.setRemark(remark);
        resources.stockLogMapper.insertInvStockLog(log);
    }
}
