package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.dto.InvExistingStockAdjustRequest;
import com.erp.inventory.domain.dto.InvStockAdjustRequest;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.domain.vo.InvStockSummary;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvStockService;

@Service
public class InvStockServiceImpl extends InvBaseService implements IInvStockService
{
    private static final int MAX_ADJUSTMENT_INTEGER_DIGITS = 14;
    private static final int MAX_ADJUSTMENT_FRACTION_DIGITS = 4;
    private static final int MAX_ADJUSTMENT_REASON_LENGTH = 500;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvProductMapper productMapper;

    @Autowired
    private InventoryItemResolver itemResolver;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Override
    public List<InvStock> selectStockList(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = prepareStockQuery(stock, selectedShopDeptId);
        List<InvStock> list = stockMapper.selectInvStockList(query);
        if (Boolean.TRUE.equals(query.getTransferSource()))
        {
            list.forEach(this::hideTransferSourceStockSensitiveFields);
        }
        return list;
    }

    @Override
    public InvStockSummary selectStockSummary(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = prepareStockQuery(stock, selectedShopDeptId);
        InvStockSummary summary = stockMapper.selectInvStockSummary(query);
        return summary == null ? new InvStockSummary() : summary;
    }

    @Override
    public InvStock selectStockById(Long stockId, Long selectedShopDeptId)
    {
        return assertAndGetScopedStock(stockId, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvStock adjustExistingStock(Long stockId, InvExistingStockAdjustRequest request,
            Long selectedShopDeptId)
    {
        if (stockId == null || stockId <= 0)
        {
            throw new ServiceException("库存记录标识无效");
        }
        if (request == null)
        {
            throw new ServiceException("库存调整参数不能为空");
        }

        BigDecimal adjustQuantity = validateAdjustmentQuantity(request.getAdjustQuantity());
        String reason = normalizeAdjustmentReason(request.getReason(), true);
        Long inventoryDeptId = requireSelectedShopDept(selectedShopDeptId);
        assertWritableInventoryDept(inventoryDeptId, selectedShopDeptId, "只能调整当前组织库存");

        InvStock stock = stockMapper.selectInvStockByIdForUpdate(stockId);
        if (stock == null)
        {
            throw new ServiceException("库存记录不存在");
        }
        Long stockWarehouseId = resolveStockWarehouseId(stock);
        if (!inventoryDeptId.equals(stock.getShopDeptId()) || !inventoryDeptId.equals(stockWarehouseId))
        {
            throw new ServiceException("只能调整当前组织库存");
        }

        long currentVersion = stock.getVersion() == null ? 0L : stock.getVersion();
        if (request.getExpectedVersion() == null || request.getExpectedVersion() != currentVersion)
        {
            throw new ServiceException("库存信息已发生变化，请刷新后重试");
        }

        BigDecimal beforeQuantity = quantityOrZero(stock.getCurrentQuantity());
        BigDecimal availableQuantity = quantityOrZero(stock.getAvailableQuantity());
        assertAdjustmentBalance(beforeQuantity, availableQuantity, adjustQuantity);
        BigDecimal unitCost = stock.getCostPrice() == null ? BigDecimal.ZERO : stock.getCostPrice();
        BigDecimal movementCost = null;
        int affectedRows;
        if (adjustQuantity.compareTo(BigDecimal.ZERO) > 0)
        {
            affectedRows = stockMapper.addInvStockWithCost(stockId, currentVersion, adjustQuantity,
                    adjustQuantity.multiply(unitCost), SecurityUtils.getUsername());
        }
        else
        {
            BigDecimal deductQuantity = adjustQuantity.abs();
            InvStockCostAllocator.Allocation allocation = InvStockCostAllocator.allocate(stock, deductQuantity);
            unitCost = allocation.unitCost();
            movementCost = allocation.amount();
            affectedRows = stockMapper.deductInvStockWithCost(stockId, currentVersion, deductQuantity,
                    movementCost, SecurityUtils.getUsername());
        }
        if (affectedRows != 1)
        {
            throw new ServiceException("库存信息已发生变化或可用库存不足，请刷新后重试");
        }

        InvStock updated = stockMapper.selectInvStockById(stockId);
        if (updated == null)
        {
            throw new ServiceException("库存调整结果不存在");
        }
        insertManualAdjustmentLog(updated, beforeQuantity, adjustQuantity, reason, null,
                movementCost == null ? updated.getCostPrice() : unitCost, movementCost);
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(Long productId, Long shopDeptId, Long warehouseId, BigDecimal adjustQuantity, String reason, Long selectedShopDeptId)
    {
        InvStockAdjustRequest request = new InvStockAdjustRequest();
        request.setItemType(InvItemTypes.PRODUCT);
        request.setItemId(productId);
        request.setProductId(productId);
        request.setShopDeptId(shopDeptId);
        request.setWarehouseId(warehouseId);
        request.setAdjustQuantity(adjustQuantity);
        request.setReason(reason);
        adjustStock(request, selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(InvStockAdjustRequest request, Long selectedShopDeptId)
    {
        if (request == null)
        {
            throw new ServiceException("库存调整参数不能为空");
        }
        String itemType = InvItemTypes.normalize(request.getItemType());
        Long itemId = InvItemTypes.resolveItemId(itemType, request.getItemId(), request.getProductId());
        Long productId = InvItemTypes.PRODUCT.equals(itemType) ? itemId : null;
        Long shopDeptId = request.getShopDeptId();
        Long warehouseId = request.getWarehouseId();
        BigDecimal adjustQuantity = validateAdjustmentQuantity(request.getAdjustQuantity());
        String reason = normalizeAdjustmentReason(request.getReason(), false);
        if (itemId == null)
        {
            throw new ServiceException("请选择物料");
        }
        if (itemResolver != null) itemResolver.lockReferences(java.util.List.of(
                InventoryItemResolver.referenceKey(itemType, itemId, productId)));
        InventoryItemSnapshot item = resolveInventoryItem(itemType, itemId, productId);
        Long inventoryDeptId = resolveInventoryDeptId(shopDeptId, warehouseId, selectedShopDeptId);
        assertWritableInventoryDept(inventoryDeptId, selectedShopDeptId, "只能调整当前组织库存");

        InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(itemType, itemId, inventoryDeptId, inventoryDeptId);
        BigDecimal beforeQty = BigDecimal.ZERO;
        BigDecimal movementCost = null;
        BigDecimal movementUnitCost = null;
        if (stock == null)
        {
            if (adjustQuantity.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("库存不存在，无法扣减");
            }
            stock = new InvStock();
            stock.setItemType(itemType);
            stock.setItemId(itemId);
            stock.setProductId(productId);
            stock.setShopDeptId(inventoryDeptId);
            stock.setWarehouseId(inventoryDeptId);
            stock.setCurrentQuantity(adjustQuantity);
            stock.setAvailableQuantity(adjustQuantity);
            stock.setLockedQuantity(BigDecimal.ZERO);
            BigDecimal unitCost = resolveManualAdjustmentUnitCost(item, null);
            stock.setCostPrice(unitCost);
            stock.setTotalCost(adjustQuantity.multiply(unitCost));
            stock.setLastInTime(new Date());
            stock.setCreateBy(SecurityUtils.getUsername());
            applyStockMetadata(stock, request);
            int insertedRows = stockMapper.insertInvStock(stock);
            if (insertedRows != 1)
            {
                throw new ServiceException("库存调整失败");
            }
        }
        else
        {
            BigDecimal currentQuantity = stock.getCurrentQuantity() != null ? stock.getCurrentQuantity() : BigDecimal.ZERO;
            BigDecimal availableQuantity = stock.getAvailableQuantity() != null ? stock.getAvailableQuantity() : BigDecimal.ZERO;
            beforeQty = currentQuantity;
            BigDecimal newQty = currentQuantity.add(adjustQuantity);
            if (newQty.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("调整后库存不能为负数，当前库存: " + beforeQty.toString());
            }
            BigDecimal newAvailableQty = availableQuantity.add(adjustQuantity);
            if (newAvailableQty.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("可用库存不足，当前可用库存: " + availableQuantity);
            }
            BigDecimal unitCost = resolveManualAdjustmentUnitCost(item, stock);
            if (adjustQuantity.compareTo(BigDecimal.ZERO) > 0)
            {
                BigDecimal incomingCost = adjustQuantity.multiply(unitCost);
                int rows = stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(), adjustQuantity, incomingCost,
                        SecurityUtils.getUsername());
                if (rows == 0)
                {
                    throw new ServiceException("库存调整失败");
                }
            }
            else
            {
                BigDecimal deductQuantity = adjustQuantity.abs();
                InvStockCostAllocator.Allocation allocation = InvStockCostAllocator.allocate(stock, deductQuantity);
                movementUnitCost = allocation.unitCost();
                BigDecimal deductCost = allocation.amount();
                movementCost = deductCost;
                int rows = stockMapper.deductInvStockWithCost(stock.getStockId(), stock.getVersion(), deductQuantity, deductCost,
                        SecurityUtils.getUsername());
                if (rows == 0)
                {
                    throw new ServiceException("可用库存不足或调整失败");
                }
            }
            stock = stockMapper.selectInvStockById(stock.getStockId());
            if (stock == null)
            {
                throw new ServiceException("库存调整结果不存在");
            }
            if (hasStockMetadata(request))
            {
                applyStockMetadata(stock, request);
                stock.setUpdateBy(SecurityUtils.getUsername());
                int metadataRows = stockMapper.updateInvStock(stock);
                if (metadataRows == 0)
                {
                    throw new ServiceException("库存信息已发生变化，请刷新后重试");
                }
                stock = stockMapper.selectInvStockById(stock.getStockId());
                if (stock == null)
                {
                    throw new ServiceException("库存调整结果不存在");
                }
            }
        }

        insertManualAdjustmentLog(stock, beforeQty, adjustQuantity, reason, request,
                movementCost == null ? stock.getCostPrice() : movementUnitCost, movementCost);
    }

    private BigDecimal validateAdjustmentQuantity(BigDecimal quantity)
    {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0)
        {
            throw new ServiceException("调整数量不能为0");
        }
        BigDecimal normalized = quantity.stripTrailingZeros();
        int fractionDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        if (fractionDigits > MAX_ADJUSTMENT_FRACTION_DIGITS || integerDigits > MAX_ADJUSTMENT_INTEGER_DIGITS)
        {
            throw new ServiceException("调整数量最多14位整数和4位小数");
        }
        return quantity;
    }

    private String normalizeAdjustmentReason(String reason, boolean required)
    {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty())
        {
            if (required)
            {
                throw new ServiceException("调整原因不能为空");
            }
            return "手动库存调整";
        }
        int length = normalized.codePointCount(0, normalized.length());
        if ((required && length < 2) || length > MAX_ADJUSTMENT_REASON_LENGTH)
        {
            throw new ServiceException(required
                    ? "调整原因长度必须在2到500个字符之间"
                    : "调整原因长度不能超过500个字符");
        }
        return normalized;
    }

    private BigDecimal quantityOrZero(BigDecimal quantity)
    {
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    private void assertAdjustmentBalance(BigDecimal currentQuantity, BigDecimal availableQuantity,
            BigDecimal adjustQuantity)
    {
        BigDecimal nextCurrent = currentQuantity.add(adjustQuantity);
        if (nextCurrent.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("调整后库存不能为负数，当前库存: " + currentQuantity);
        }
        BigDecimal nextAvailable = availableQuantity.add(adjustQuantity);
        if (nextAvailable.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("可用库存不足，当前可用库存: " + availableQuantity);
        }
    }

    private void insertManualAdjustmentLog(InvStock stock, BigDecimal beforeQuantity,
            BigDecimal adjustQuantity, String reason, InvStockAdjustRequest request,
            BigDecimal movementUnitCost, BigDecimal movementCost)
    {
        InvStockLog log = new InvStockLog();
        log.setItemType(InvItemTypes.normalize(stock.getItemType()));
        log.setItemId(InvItemTypes.resolveItemId(log.getItemType(), stock.getItemId(), stock.getProductId()));
        log.setProductId(stock.getProductId());
        log.setShopDeptId(stock.getShopDeptId());
        log.setWarehouseId(resolveStockWarehouseId(stock));
        log.setMovementType(InvStatusConstants.MOVEMENT_ADJUSTMENT);
        log.setBusinessType("stock");
        log.setBusinessId(stock.getStockId());
        long version = stock.getVersion() == null ? 0L : stock.getVersion();
        log.setBusinessNo("STOCK-ADJ-" + stock.getStockId() + "-V" + version);
        log.setChangeQuantity(adjustQuantity);
        log.setBeforeQuantity(beforeQuantity);
        log.setAfterQuantity(stock.getCurrentQuantity());
        log.setCostPrice(movementUnitCost);
        log.setCostAmount(movementCost);
        applyStockLogMetadata(log, stock, request);
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        log.setRemark(reason);
        if (stockLogMapper.insertInvStockLog(log) != 1)
        {
            throw new ServiceException("库存调整流水写入失败");
        }
    }

    private boolean hasStockMetadata(InvStockAdjustRequest request)
    {
        return request.getBatchNo() != null || request.getExpiryDate() != null || request.getSerialNo() != null ||
                request.getLocationCode() != null || request.getLocationName() != null;
    }

    private void applyStockMetadata(InvStock stock, InvStockAdjustRequest request)
    {
        stock.setBatchNo(request.getBatchNo());
        stock.setExpiryDate(request.getExpiryDate());
        stock.setSerialNo(request.getSerialNo());
        stock.setLocationCode(request.getLocationCode());
        stock.setLocationName(request.getLocationName());
    }

    private void applyStockLogMetadata(InvStockLog log, InvStock stock, InvStockAdjustRequest request)
    {
        log.setBatchNo(request != null && request.getBatchNo() != null ? request.getBatchNo() : stock.getBatchNo());
        log.setExpiryDate(request != null && request.getExpiryDate() != null ? request.getExpiryDate() : stock.getExpiryDate());
        log.setSerialNo(request != null && request.getSerialNo() != null ? request.getSerialNo() : stock.getSerialNo());
        log.setLocationCode(request != null && request.getLocationCode() != null ? request.getLocationCode() : stock.getLocationCode());
        log.setLocationName(request != null && request.getLocationName() != null ? request.getLocationName() : stock.getLocationName());
    }

    private BigDecimal resolveManualAdjustmentUnitCost(InventoryItemSnapshot item, InvStock stock)
    {
        if (stock != null && stock.getCostPrice() != null)
        {
            return stock.getCostPrice();
        }
        if (item != null && item.getCostPrice() != null)
        {
            return item.getCostPrice();
        }
        Long productId = item == null ? null : item.getProductId();
        if (productId == null)
        {
            return BigDecimal.ZERO;
        }
        InvProduct product = productMapper == null ? null : productMapper.selectInvProductById(productId);
        if (product == null || product.getCostPrice() == null)
        {
            return BigDecimal.ZERO;
        }
        return product.getCostPrice();
    }

    private InventoryItemSnapshot resolveInventoryItem(String itemType, Long itemId, Long productId)
    {
        if (itemResolver != null)
        {
            return itemResolver.resolve(itemType, itemId, productId);
        }
        InventoryItemSnapshot item = new InventoryItemSnapshot();
        item.setItemType(itemType);
        item.setItemId(itemId);
        item.setProductId(productId);
        return item;
    }

    private Long resolveInventoryDeptId(Long shopDeptId, Long warehouseId, Long selectedShopDeptId)
    {
        if (warehouseId != null && warehouseId != 0)
        {
            return warehouseId;
        }
        if (shopDeptId != null && shopDeptId != 0)
        {
            return shopDeptId;
        }
        return requireSelectedShopDept(selectedShopDeptId);
    }

    @Override
    public List<InvStockLog> selectStockLogList(InvStockLog stockLog, Long selectedShopDeptId)
    {
        InvStockLog query = stockLog == null ? new InvStockLog() : stockLog;
        query.setKeyword(normalizeQueryText(query.getKeyword(), 100, "流水关键字"));
        query.setBusinessNo(normalizeQueryText(query.getBusinessNo(), 128, "业务单号"));
        query.setMovementType(normalizeQueryText(query.getMovementType(), 64, "变动类型"));
        if (query.getBeginTime() != null && query.getEndTime() != null
                && query.getBeginTime().after(query.getEndTime()))
        {
            throw new ServiceException("流水开始日期不能晚于结束日期");
        }
        appendLogShopScope(query, selectedShopDeptId);
        return stockLogMapper.selectInvStockLogList(query);
    }

    private String normalizeQueryText(String value, int maxLength, String label)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength)
        {
            throw new ServiceException(label + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private InvStock assertAndGetScopedStock(Long stockId, Long selectedShopDeptId)
    {
        InvStock db = stockMapper.selectInvStockById(stockId);
        if (db == null)
        {
            throw new ServiceException("库存记录不存在");
        }
        if (selectedShopDeptId != null && selectedShopDeptId != 0)
        {
            assertCurrentInventoryDeptVisible(selectedShopDeptId);
            if (selectedShopDeptId.equals(db.getShopDeptId()) && selectedShopDeptId.equals(resolveStockWarehouseId(db)))
            {
                return db;
            }
            if (isWarehouseDept(selectedShopDeptId) && isStockVisibleFromSelectedDept(selectedShopDeptId, db))
            {
                return db;
            }
            if (isStoreDept(selectedShopDeptId) && isStoreStockVisibleFromAuthorizedScope(db))
            {
                return db;
            }
            throw new ServiceException("无权查看该店铺库存");
        }
        if (SecurityUtils.isAdmin())
        {
            return db;
        }
        Long rootDeptId = requireSelectedShopDept(selectedShopDeptId);
        assertUserShopScope(rootDeptId);
        if (!rootDeptId.equals(db.getShopDeptId()) || !rootDeptId.equals(resolveStockWarehouseId(db)))
        {
            throw new ServiceException("无权查看该店铺库存");
        }
        return db;
    }

    private void appendShopScope(InvStock stock, Long selectedShopDeptId)
    {
        if (Boolean.TRUE.equals(stock.getTransferSource()))
        {
            applyTransferSourceScope(stock, selectedShopDeptId);
            return;
        }
        if (Boolean.TRUE.equals(stock.getOwnOnly()))
        {
            applyCurrentInventoryScope(stock, selectedShopDeptId);
            return;
        }
        Long scopeRoot = requireSelectedShopDept(selectedShopDeptId);
        if (isStoreDept(scopeRoot))
        {
            applyAuthorizedStoreInventoryScope(stock, scopeRoot);
            return;
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        assertUserShopScope(scopeRoot);
        List<Long> scopeDeptIds = resolveWarehouseVisibleScopeDeptIds(scopeRoot);
        stock.getParams().put("scopeDeptIds", scopeDeptIds);
    }

    private void applyAuthorizedStoreInventoryScope(InvStock stock, Long selectedStoreDeptId)
    {
        assertCurrentInventoryDeptVisible(selectedStoreDeptId);
        Long requestedStoreDeptId = stock.getShopDeptId();
        if (requestedStoreDeptId != null && requestedStoreDeptId != 0)
        {
            assertDeptType(requestedStoreDeptId, DEPT_TYPE_STORE, "请选择门店");
            if (!SecurityUtils.isAdmin())
            {
                assertUserShopScope(requestedStoreDeptId);
            }
            stock.setShopDeptId(requestedStoreDeptId);
            stock.setWarehouseId(requestedStoreDeptId);
            stock.getParams().remove("scopeDeptIds");
        }
        else
        {
            stock.setShopDeptId(null);
            stock.setWarehouseId(null);
            stock.getParams().put("scopeDeptIds", resolveAuthorizedStoreScopeDeptIds(selectedStoreDeptId));
        }
        if (!"warning".equals(stock.getStockScope()))
        {
            stock.setStockScope(null);
        }
    }

    private List<Long> resolveAuthorizedStoreScopeDeptIds(Long selectedStoreDeptId)
    {
        List<Long> scopeDeptIds = SecurityUtils.isAdmin()
                ? deptScopeMapper.selectAllStoreDeptIds()
                : deptScopeMapper.selectUserStoreScopeDeptIds(SecurityUtils.getUserId());
        return scopeDeptIds == null || scopeDeptIds.isEmpty() ? Collections.singletonList(selectedStoreDeptId) : scopeDeptIds;
    }

    private List<Long> resolveWarehouseVisibleScopeDeptIds(Long warehouseDeptId)
    {
        List<Long> ancestorDeptIds = deptScopeMapper.selectAncestorDeptIds(warehouseDeptId);
        Long businessRootDeptId = warehouseDeptId;
        if (ancestorDeptIds != null && !ancestorDeptIds.isEmpty())
        {
            // Ancestors are ordered nearest first; the last one is the outer business root below dept 0.
            businessRootDeptId = ancestorDeptIds.get(ancestorDeptIds.size() - 1);
        }
        List<Long> scopeDeptIds = deptScopeMapper.selectSubDeptIds(businessRootDeptId);
        return scopeDeptIds == null || scopeDeptIds.isEmpty() ? Collections.singletonList(warehouseDeptId) : scopeDeptIds;
    }

    private InvStock prepareStockQuery(InvStock stock, Long selectedShopDeptId)
    {
        InvStock query = stock == null ? new InvStock() : stock;
        query.setSelectedWarehouseId(selectedShopDeptId);
        appendShopScope(query, selectedShopDeptId);
        return query;
    }

    private void appendLogShopScope(InvStockLog stockLog, Long selectedShopDeptId)
    {
        if (selectedShopDeptId != null && selectedShopDeptId != 0)
        {
            assertCurrentInventoryDeptVisible(selectedShopDeptId);
            stockLog.setShopDeptId(selectedShopDeptId);
            stockLog.setWarehouseId(selectedShopDeptId);
            stockLog.getParams().remove("scopeDeptIds");
            return;
        }
        if (!SecurityUtils.isAdmin())
        {
            requireSelectedShopDept(selectedShopDeptId);
        }
    }

    private void applyCurrentInventoryScope(InvStock stock, Long selectedShopDeptId)
    {
        Long inventoryDeptId = requireSelectedShopDept(selectedShopDeptId);
        assertCurrentInventoryDeptVisible(inventoryDeptId);
        stock.setShopDeptId(inventoryDeptId);
        stock.setWarehouseId(inventoryDeptId);
        stock.getParams().remove("scopeDeptIds");
        if (!"warning".equals(stock.getStockScope()))
        {
            stock.setStockScope(null);
        }
    }

    private void applyTransferSourceScope(InvStock stock, Long selectedShopDeptId)
    {
        Long selectedDeptId = requireSelectedShopDept(selectedShopDeptId);
        assertCurrentInventoryDeptVisible(selectedDeptId);
        assertDeptType(selectedDeptId, DEPT_TYPE_STORE,
                "门店才能查看调拨来源库存");

        Long sourceDeptId = resolveStockWarehouseId(stock);
        if (sourceDeptId == null || sourceDeptId == 0)
        {
            throw new ServiceException("请选择调拨来源组织");
        }
        String sourceDeptType = deptScopeMapper.selectDeptTypeById(
                sourceDeptId);
        if (!DEPT_TYPE_STORE.equals(sourceDeptType)
                && !DEPT_TYPE_WAREHOUSE.equals(sourceDeptType))
        {
            throw new ServiceException("调拨来源必须为门店或仓库");
        }
        if (DEPT_TYPE_STORE.equals(sourceDeptType)
                && !SecurityUtils.isAdmin())
        {
            assertUserShopScope(sourceDeptId);
        }

        stock.setShopDeptId(sourceDeptId);
        stock.setWarehouseId(sourceDeptId);
        stock.setSelectedWarehouseId(sourceDeptId);
        stock.setStockStatus("available");
        stock.getParams().remove("scopeDeptIds");
        stock.getParams().remove("itemOwnerScopeDeptIds");
        stock.getParams().put("transferSourceItemQualified", Boolean.TRUE);
        if (DEPT_TYPE_WAREHOUSE.equals(sourceDeptType))
        {
            InvTransferSourceItemScopePolicy sourcePolicy =
                    new InvTransferSourceItemScopePolicy(deptScopeMapper);
            sourcePolicy.assertWarehouseReplenishmentRouteAllowed(
                    sourceDeptId, selectedDeptId);
            stock.getParams().put("itemOwnerScopeDeptIds",
                    sourcePolicy.resolveWarehouseOwnerScopeDeptIds(
                            sourceDeptId));
        }
        if (!"warning".equals(stock.getStockScope()))
        {
            stock.setStockScope(null);
        }
    }

    private void hideTransferSourceStockSensitiveFields(InvStock stock)
    {
        if (stock == null)
        {
            return;
        }
        stock.setCurrentQuantity(null);
        stock.setLockedQuantity(null);
        stock.setTotalCost(null);
        stock.setBatchNo(null);
        stock.setExpiryDate(null);
        stock.setSerialNo(null);
        stock.setLocationCode(null);
        stock.setLocationName(null);
        stock.setVersion(null);
        stock.setLastInTime(null);
        stock.setLastOutTime(null);
        stock.setCreateBy(null);
        stock.setCreateTime(null);
        stock.setUpdateBy(null);
        stock.setUpdateTime(null);
        stock.setRemark(null);
    }

    private void assertCurrentInventoryDeptVisible(Long selectedShopDeptId)
    {
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(selectedShopDeptId);
        }
    }

    private Long resolveStockWarehouseId(InvStock stock)
    {
        return stock.getWarehouseId() == null || stock.getWarehouseId() == 0 ? stock.getShopDeptId() : stock.getWarehouseId();
    }

    private boolean isStockVisibleFromSelectedDept(Long selectedDeptId, InvStock stock)
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        return deptScopeMapper.countDeptInScope(selectedDeptId, stock.getShopDeptId()) > 0
                || deptScopeMapper.countDeptInScope(selectedDeptId, resolveStockWarehouseId(stock)) > 0;
    }

    private boolean isStoreStockVisibleFromAuthorizedScope(InvStock stock)
    {
        Long inventoryDeptId = resolveStockWarehouseId(stock);
        if (!isStoreDept(inventoryDeptId))
        {
            return false;
        }
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        return deptScopeMapper.countUserShopScope(SecurityUtils.getUserId(), inventoryDeptId) > 0;
    }

    @Override
    protected void assertUserShopScope(Long shopDeptId)
    {
        if (deptScopeMapper.countUserShopScope(SecurityUtils.getUserId(), shopDeptId) <= 0)
        {
            throw new ServiceException("当前用户无权选择该店铺");
        }
    }
}
