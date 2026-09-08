package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvProduct;
import com.erp.inventory.domain.InvCustomer;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.vo.InventoryItemSnapshot;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvCustomerMapper;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
import com.erp.inventory.mapper.InvProductMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvSalesReturnDetailMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.mapper.InvTransferDetailMapper;
import com.erp.inventory.mapper.InvTransferOrderMapper;
import com.erp.inventory.mapper.InvTransferStatusLogMapper;
import com.erp.inventory.service.IInvSalesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvOutboundRecord;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.InvTransferStatusLog;

@Service
public class InvSalesServiceImpl extends InvBaseService implements IInvSalesService
{

    @Autowired
    private InvSalesOrderMapper salesOrderMapper;

    @Autowired
    private InvCustomerMapper customerMapper;

    @Autowired
    private InvSalesDetailMapper salesDetailMapper;

    @Autowired
    private InvSalesReturnDetailMapper salesReturnDetailMapper;

    @Autowired
    private InvProductMapper productMapper;

    @Autowired
    private InventoryItemResolver itemResolver;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InvOutboundRecordMapper outboundRecordMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Autowired
    private InvTransferOrderMapper transferOrderMapper;

    @Autowired
    private InvTransferDetailMapper transferDetailMapper;

    @Autowired
    private InvTransferStatusLogMapper transferStatusLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSalesOrder saveDraft(InvSalesOrder order, List<InvSalesDetail> details, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        rejectUnsupportedTargetStore(order, shopDeptId);
        applyCustomerSnapshot(order, shopDeptId);
        applyCatalogItemsForDraft(order, details, selectedShopDeptId);
        if (order.getOrderId() == null)
        {
            order.setShopDeptId(shopDeptId);
            order.setApplicantId(SecurityUtils.getUserId());
            order.setApplicantName(SecurityUtils.getUsername());
            order.setApplicantDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId());
            order.setCreateBy(SecurityUtils.getUsername());
            order.setStatus(InvStatusConstants.DRAFT);
            order.setOrderNo(generateOrderNo("OB"));
            salesOrderMapper.insertInvSalesOrder(order);
            if (details != null)
            {
                for (InvSalesDetail d : details)
                {
                    d.setOrderId(order.getOrderId());
                }
                salesDetailMapper.batchInsertInvSalesDetail(details);
            }
        }
        else
        {
            InvSalesOrder db = assertAndGetScopedSales(order.getOrderId(), selectedShopDeptId);
            InvStateGuard.requireDraftForEdit(db.getStatus());
            order.setStatus(InvStatusConstants.DRAFT);
            order.setUpdateBy(SecurityUtils.getUsername());
            salesOrderMapper.updateInvSalesOrder(order);
            if (details != null)
            {
                salesDetailMapper.deleteInvSalesDetailByOrderId(order.getOrderId());
                for (InvSalesDetail d : details)
                {
                    d.setOrderId(order.getOrderId());
                }
                salesDetailMapper.batchInsertInvSalesDetail(details);
            }
        }
        return salesOrderMapper.selectInvSalesOrderById(order.getOrderId());
    }

    private void rejectUnsupportedTargetStore(InvSalesOrder order, Long shopDeptId)
    {
        if (order == null || order.getTargetDeptId() == null || order.getTargetDeptId().equals(shopDeptId))
        {
            return;
        }
        throw new ServiceException("销售单暂不支持跨门店销售收货，请使用调拨单处理跨门店流转");
    }

    private void applyCustomerSnapshot(InvSalesOrder order, Long shopDeptId)
    {
        if (order == null)
        {
            throw new ServiceException("销售单不能为空");
        }
        if (order.getCustomerId() == null)
        {
            throw new ServiceException("请选择客户档案");
        }
        InvCustomer customer = customerMapper.selectInvCustomerById(order.getCustomerId());
        if (customer == null || !"0".equals(customer.getStatus()))
        {
            throw new ServiceException("所选客户不存在或已停用");
        }
        assertShopVisible(customer.getShopDeptId(), shopDeptId, "无权使用所选客户");
        order.setCustomerName(customer.getCustomerName());
    }

    static void applyCatalogProductsForDraft(InvSalesOrder order, List<InvSalesDetail> details,
            Map<Long, InvProduct> productsById)
    {
        if (order == null)
        {
            throw new ServiceException("销售单不能为空");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请添加至少一条销售明细");
        }
        if (order.getOrderDate() == null)
        {
            order.setOrderDate(new Date());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<Long, InvProduct> productMap = productsById == null ? new HashMap<>() : productsById;
        for (InvSalesDetail detail : details)
        {
            if (detail == null || detail.getProductId() == null)
            {
                throw new ServiceException("请选择销售商品");
            }
            InvProduct product = productMap.get(detail.getProductId());
            if (product == null)
            {
                throw new ServiceException("商品不存在: " + detail.getProductId());
            }
            if (!"0".equals(product.getStatus()))
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 已停用");
            }

            detail.setProductName(product.getProductName());
            detail.setSku(product.getSku());
            detail.setSpec(product.getSpec());
            detail.setUnit(product.getUnit());

            BigDecimal quantity = detail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 销售数量必须大于0");
            }
            BigDecimal unitPrice = detail.getUnitPrice();
            if (unitPrice == null)
            {
                unitPrice = product.getSalesPrice() != null ? product.getSalesPrice() : BigDecimal.ZERO;
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + product.getProductName() + "] 售价必须大于0，请先维护商品售价");
            }
            BigDecimal amount = quantity.multiply(unitPrice);
            detail.setUnitPrice(unitPrice);
            detail.setAmount(amount);
            detail.setDeliveredQuantity(BigDecimal.ZERO);
            totalAmount = totalAmount.add(amount);
        }
        order.setTotalAmount(totalAmount);
    }

    private void applyCatalogItemsForDraft(InvSalesOrder order, List<InvSalesDetail> details, Long selectedShopDeptId)
    {
        if (order == null)
        {
            throw new ServiceException("销售单不能为空");
        }
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("请添加至少一条销售明细");
        }
        if (order.getOrderDate() == null)
        {
            order.setOrderDate(new Date());
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvSalesDetail detail : details)
        {
            InventoryItemSnapshot item = resolveSalesItem(detail, selectedShopDeptId);
            applyItemSnapshot(detail, item);
            if (detail.getWarehouseId() != null)
            {
                assertDeptType(detail.getWarehouseId(), DEPT_TYPE_WAREHOUSE, "出库仓库不合法");
                assertShopVisible(detail.getWarehouseId(), selectedShopDeptId, "无权使用所选出库仓库");
            }

            BigDecimal quantity = detail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("物料 [" + item.getItemName() + "] 销售数量必须大于0");
            }
            BigDecimal unitPrice = detail.getUnitPrice();
            if (unitPrice == null)
            {
                unitPrice = item.getSalesPrice() != null ? item.getSalesPrice() : BigDecimal.ZERO;
            }
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("物料 [" + item.getItemName() + "] 售价必须大于0，请先维护物料售价");
            }
            BigDecimal amount = quantity.multiply(unitPrice);
            detail.setUnitPrice(unitPrice);
            detail.setAmount(amount);
            detail.setDeliveredQuantity(BigDecimal.ZERO);
            totalAmount = totalAmount.add(amount);
        }
        order.setTotalAmount(totalAmount);
    }

    private InventoryItemSnapshot resolveSalesItem(InvSalesDetail detail, Long selectedShopDeptId)
    {
        if (detail == null)
        {
            throw new ServiceException("请选择销售物料");
        }
        String itemType = InvItemTypes.normalize(detail.getItemType());
        if (InvItemTypes.OE.equals(itemType))
        {
            throw new ServiceException("OE不能用于销售流程");
        }
        Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(), detail.getProductId());
        if (itemId == null)
        {
            throw new ServiceException("请选择销售物料");
        }
        InventoryItemSnapshot item = itemResolver.resolve(itemType, itemId, detail.getProductId());
        if (!"0".equals(item.getStatus()))
        {
            throw new ServiceException("物料 [" + item.getItemName() + "] 已停用");
        }
        if (InvItemTypes.PRODUCT.equals(item.getItemType()) && item.getOwnerDeptId() != null)
        {
            assertRelatedShopVisible(item.getOwnerDeptId(), selectedShopDeptId, productScopeError(item, selectedShopDeptId));
        }
        return item;
    }

    private void applyItemSnapshot(InvSalesDetail detail, InventoryItemSnapshot item)
    {
        detail.setItemType(item.getItemType());
        detail.setItemId(item.getItemId());
        detail.setItemCode(item.getItemCode());
        detail.setItemName(item.getItemName());
        detail.setProductId(item.getProductId());
        detail.setProductName(item.getItemName());
        detail.setSku(item.getItemCode());
        detail.setSpec(item.getSpec());
        detail.setUnit(item.getUnit());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSalesOrder submitSales(InvSalesOrder order, List<InvSalesDetail> details, Long selectedShopDeptId)
    {
        InvSalesOrder saved = saveDraft(order, details, selectedShopDeptId);
        InvSalesOrder update = new InvSalesOrder();
        update.setOrderId(saved.getOrderId());
        update.setStatus(InvStatusConstants.SUBMITTED);
        update.setUpdateBy(SecurityUtils.getUsername());
        salesOrderMapper.updateInvSalesOrder(update);
        return salesOrderMapper.selectInvSalesOrderById(saved.getOrderId());
    }

    @Override
    public InvSalesOrder getSalesDetail(Long orderId, Long selectedShopDeptId)
    {
        InvSalesOrder order = assertAndGetScopedSales(orderId, selectedShopDeptId);
        List<InvSalesDetail> details = salesDetailMapper.selectInvSalesDetailByOrderId(orderId);
        Map<Long, BigDecimal> historicalReturnedByDetailId = new HashMap<>();
        for (InvSalesDetail detail : details)
        {
            if (detail.getDetailId() == null)
            {
                continue;
            }
            BigDecimal historicalReturned = salesReturnDetailMapper
                    .sumHistoricalReturnQuantityBySalesDetailId(orderId, detail.getDetailId(), null);
            historicalReturnedByDetailId.put(detail.getDetailId(),
                    historicalReturned == null ? BigDecimal.ZERO : historicalReturned);
        }
        applyReturnableQuantities(details, historicalReturnedByDetailId);
        order.setDetails(details);
        return order;
    }

    static void applyReturnableQuantities(List<InvSalesDetail> details,
            Map<Long, BigDecimal> historicalReturnedByDetailId)
    {
        if (details == null)
        {
            return;
        }
        Map<Long, BigDecimal> historicalByDetail = historicalReturnedByDetailId == null
                ? Map.of() : historicalReturnedByDetailId;
        for (InvSalesDetail detail : details)
        {
            BigDecimal delivered = detail.getDeliveredQuantity() == null
                    ? BigDecimal.ZERO : detail.getDeliveredQuantity();
            BigDecimal historicalReturned = detail.getDetailId() == null
                    ? BigDecimal.ZERO
                    : historicalByDetail.getOrDefault(detail.getDetailId(), BigDecimal.ZERO);
            if (historicalReturned == null || historicalReturned.compareTo(BigDecimal.ZERO) < 0)
            {
                historicalReturned = BigDecimal.ZERO;
            }
            detail.setHistoricalReturnedQuantity(historicalReturned);
            detail.setReturnableQuantity(delivered.subtract(historicalReturned).max(BigDecimal.ZERO));
        }
    }

    @Override
    public List<InvSalesOrder> selectSalesList(InvSalesOrder order, Long selectedShopDeptId)
    {
        appendShopScope(order, selectedShopDeptId);
        return salesOrderMapper.selectInvSalesOrderList(order);
    }

    @Override
    public List<InvSalesOrder> selectMySales(InvSalesOrder order, Long selectedShopDeptId)
    {
        order.setApplicantId(SecurityUtils.getUserId());
        appendShopScope(order, selectedShopDeptId);
        return salesOrderMapper.selectMyInvSalesOrderList(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSales(Long orderId, Long selectedShopDeptId)
    {
        InvSalesOrder order = assertAndGetScopedSales(orderId, selectedShopDeptId);
        InvStateGuard.requireCancelableDocument(order.getStatus());
        InvSalesOrder update = new InvSalesOrder();
        update.setOrderId(orderId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setUpdateBy(SecurityUtils.getUsername());
        salesOrderMapper.updateInvSalesOrder(update);
    }

    private InvSalesOrder assertAndGetScopedSales(Long orderId, Long selectedShopDeptId)
    {
        InvSalesOrder db = salesOrderMapper.selectInvSalesOrderById(orderId);
        if (db == null)
        {
            throw new ServiceException("销售单不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺销售单");
        return db;
    }

    private Map<Long, InvProduct> selectProductsByDetailIds(List<InvSalesDetail> details, Long selectedShopDeptId)
    {
        Map<Long, InvProduct> productsById = new HashMap<>();
        if (details == null)
        {
            return productsById;
        }
        for (InvSalesDetail detail : details)
        {
            if (detail == null || detail.getProductId() == null || productsById.containsKey(detail.getProductId()))
            {
                continue;
            }
            InvProduct product = productMapper.selectInvProductById(detail.getProductId());
            if (product == null)
            {
                throw new ServiceException("商品不存在: " + detail.getProductId());
            }
            if (product.getShopDeptId() != null)
            {
                assertRelatedShopVisible(product.getShopDeptId(), selectedShopDeptId, productScopeError(product, selectedShopDeptId));
            }
            productsById.put(detail.getProductId(), product);
        }
        return productsById;
    }

    private String productScopeError(InvProduct product, Long selectedShopDeptId)
    {
        String productName = product.getProductName() == null || product.getProductName().isEmpty()
                ? String.valueOf(product.getProductId()) : product.getProductName();
        String ownerName = deptScopeMapper.selectDeptNameById(product.getShopDeptId());
        String currentName = deptScopeMapper.selectDeptNameById(selectedShopDeptId);
        return "无权使用商品「" + productName + "」；商品所属组织：" + displayDept(ownerName, product.getShopDeptId())
                + "，当前组织：" + displayDept(currentName, selectedShopDeptId);
    }

    private String productScopeError(InventoryItemSnapshot item, Long selectedShopDeptId)
    {
        String productName = item.getItemName() == null || item.getItemName().isEmpty()
                ? String.valueOf(item.getItemId()) : item.getItemName();
        String ownerName = deptScopeMapper.selectDeptNameById(item.getOwnerDeptId());
        String currentName = deptScopeMapper.selectDeptNameById(selectedShopDeptId);
        return "无权使用商品「" + productName + "」；商品所属组织：" + displayDept(ownerName, item.getOwnerDeptId())
                + "，当前组织：" + displayDept(currentName, selectedShopDeptId);
    }

    private String displayDept(String deptName, Long deptId)
    {
        if (deptName != null && !deptName.isEmpty())
        {
            return deptName;
        }
        return deptId == null ? "-" : String.valueOf(deptId);
    }

    private String generateOrderNo(String prefix)
    {
        String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }
}
