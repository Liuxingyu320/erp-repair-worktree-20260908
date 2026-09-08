package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvSalesReturnDetailMapper;
import com.erp.inventory.mapper.InvSalesReturnMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvSalesReturnService;

@Service
public class InvSalesReturnServiceImpl extends InvBaseService implements IInvSalesReturnService
{

    @Autowired
    private InvSalesReturnMapper salesReturnMapper;

    @Autowired
    private InvSalesReturnDetailMapper salesReturnDetailMapper;

    @Autowired
    private InvSalesDetailMapper salesDetailMapper;

    @Autowired
    private InvSalesOrderMapper salesOrderMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSalesReturn saveDraft(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        if (salesReturn.getReturnId() == null)
        {
            applySourceOrderSnapshot(salesReturn, shopDeptId);
            normalizeReturnDetails(salesReturn, details);
            salesReturn.setShopDeptId(shopDeptId);
            salesReturn.setApplicantId(SecurityUtils.getUserId());
            salesReturn.setApplicantName(SecurityUtils.getUsername());
            salesReturn.setApplicantDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId());
            salesReturn.setCreateBy(SecurityUtils.getUsername());
            salesReturn.setStatus(InvStatusConstants.DRAFT);
            salesReturn.setReturnNo(generateReturnNo("SR"));
            salesReturnMapper.insertInvSalesReturn(salesReturn);
            if (details != null)
            {
                for (InvSalesReturnDetail d : details)
                {
                    d.setReturnId(salesReturn.getReturnId());
                }
                salesReturnDetailMapper.batchInsertInvSalesReturnDetail(details);
            }
        }
        else
        {
            InvSalesReturn db = assertAndGetScopedReturn(salesReturn.getReturnId(), selectedShopDeptId);
            InvStateGuard.requireDraftForEdit(db.getStatus());
            if (salesReturn.getSalesOrderId() == null)
            {
                salesReturn.setSalesOrderId(db.getSalesOrderId());
            }
            applySourceOrderSnapshot(salesReturn, shopDeptId);
            normalizeReturnDetails(salesReturn, details);
            salesReturn.setStatus(InvStatusConstants.DRAFT);
            salesReturn.setUpdateBy(SecurityUtils.getUsername());
            salesReturnMapper.updateInvSalesReturn(salesReturn);
            if (details != null)
            {
                salesReturnDetailMapper.deleteInvSalesReturnDetailByReturnId(salesReturn.getReturnId());
                for (InvSalesReturnDetail d : details)
                {
                    d.setReturnId(salesReturn.getReturnId());
                }
                salesReturnDetailMapper.batchInsertInvSalesReturnDetail(details);
            }
        }
        return salesReturnMapper.selectInvSalesReturnById(salesReturn.getReturnId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSalesReturn submitReturn(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details, Long selectedShopDeptId)
    {
        InvSalesReturn saved = saveDraft(salesReturn, details, selectedShopDeptId);
        validateReturnQuantity(saved, saved.getReturnId());
        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(saved.getReturnId());
        update.setStatus(InvStatusConstants.SUBMITTED);
        update.setUpdateBy(SecurityUtils.getUsername());
        salesReturnMapper.updateInvSalesReturn(update);
        return salesReturnMapper.selectInvSalesReturnById(saved.getReturnId());
    }

    @Override
    public InvSalesReturn getReturnDetail(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn salesReturn = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        salesReturn.setDetails(salesReturnDetailMapper.selectInvSalesReturnDetailByReturnId(returnId));
        return salesReturn;
    }

    @Override
    public List<InvSalesReturn> selectReturnList(InvSalesReturn salesReturn, Long selectedShopDeptId)
    {
        appendShopScope(salesReturn, selectedShopDeptId);
        return salesReturnMapper.selectInvSalesReturnList(salesReturn);
    }

    @Override
    public List<InvSalesReturn> selectMyReturns(InvSalesReturn salesReturn, Long selectedShopDeptId)
    {
        salesReturn.setApplicantId(SecurityUtils.getUserId());
        appendShopScope(salesReturn, selectedShopDeptId);
        return salesReturnMapper.selectMyInvSalesReturnList(salesReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn salesReturn = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        InvStateGuard.requireSubmittedForReturnConfirm(salesReturn.getStatus());
        InvSalesReturn locked = salesReturnMapper.selectInvSalesReturnByIdForUpdate(returnId);
        InvStateGuard.requireSubmittedForReturnConfirm(locked.getStatus());
        validateReturnQuantity(salesReturn, returnId);
        List<InvSalesReturnDetail> details = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnIdForUpdate(returnId);
        if (details.isEmpty())
        {
            throw new ServiceException("退货单无明细");
        }
        for (InvSalesReturnDetail detail : details)
        {
            BigDecimal toReturn = detail.getQuantity().subtract(
                    detail.getReturnedQuantity() != null ? detail.getReturnedQuantity() : BigDecimal.ZERO);
            if (toReturn.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvStock stock = stockMapper.selectInvStockByProductAndShopForUpdate(detail.getProductId(), salesReturn.getShopDeptId());
            BigDecimal unitCost = resolveReturnUnitCost(
                    salesReturn.getSalesOrderId(), detail.getProductId(), stock);
            BigDecimal incomingCost = toReturn.multiply(unitCost);
            BigDecimal beforeQty = BigDecimal.ZERO;
            if (stock == null)
            {
                stock = new InvStock();
                stock.setProductId(detail.getProductId());
                stock.setShopDeptId(salesReturn.getShopDeptId());
                stock.setWarehouseId(salesReturn.getShopDeptId());
                stock.setCurrentQuantity(toReturn);
                stock.setAvailableQuantity(toReturn);
                stock.setCostPrice(unitCost);
                stock.setTotalCost(incomingCost);
                stock.setLastInTime(new Date());
                stock.setCreateBy(SecurityUtils.getUsername());
                stockMapper.insertInvStock(stock);
            }
            else
            {
                beforeQty = stock.getCurrentQuantity();
                stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(), toReturn, incomingCost, SecurityUtils.getUsername());
                stock = stockMapper.selectInvStockById(stock.getStockId());
            }

            // 库存变动日志
            InvStockLog log = new InvStockLog();
            log.setProductId(detail.getProductId());
            log.setShopDeptId(salesReturn.getShopDeptId());
            log.setWarehouseId(salesReturn.getShopDeptId());
            log.setMovementType(InvStatusConstants.MOVEMENT_SALES_RETURN_IN);
            log.setBusinessType("sales_return");
            log.setBusinessId(returnId);
            log.setBusinessNo(salesReturn.getReturnNo());
            log.setChangeQuantity(toReturn);
            log.setBeforeQuantity(beforeQty);
            log.setAfterQuantity(stock.getCurrentQuantity());
            log.setCostPrice(unitCost);
            log.setCreateBy(SecurityUtils.getUsername());
            log.setCreateTime(new Date());
            log.setRemark("销售退货入库");
            stockLogMapper.insertInvStockLog(log);

            // 更新明细已退数量
            detail.setReturnedQuantity(detail.getReturnedQuantity() != null
                    ? detail.getReturnedQuantity().add(toReturn) : toReturn);
            salesReturnDetailMapper.updateInvSalesReturnDetail(detail);
        }

        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(returnId);
        update.setStatus(InvStatusConstants.RETURNED);
        update.setUpdateBy(SecurityUtils.getUsername());
        salesReturnMapper.updateInvSalesReturn(update);
    }

    private BigDecimal resolveReturnStockCost(InvStock stock)
    {
        if (stock == null || stock.getCostPrice() == null)
        {
            return BigDecimal.ZERO;
        }
        return stock.getCostPrice();
    }

    /**
     * 销售退货优先冲回原销售单的实际出库成本，避免用当前库存均价造成退货成本失真。
     * 历史数据没有出库日志时，才回退到当前库存成本。
     */
    private BigDecimal resolveReturnUnitCost(Long salesOrderId, Long productId, InvStock stock)
    {
        BigDecimal originalOutboundCost = resolveOriginalOutboundCost(salesOrderId, productId);
        return originalOutboundCost != null ? originalOutboundCost : resolveReturnStockCost(stock);
    }

    private BigDecimal resolveOriginalOutboundCost(Long salesOrderId, Long productId)
    {
        if (salesOrderId == null || productId == null)
        {
            return null;
        }

        InvStockLog query = new InvStockLog();
        query.setBusinessId(salesOrderId);
        query.setProductId(productId);
        query.setMovementType(InvStatusConstants.MOVEMENT_SALES_OUT);
        List<InvStockLog> outboundLogs = stockLogMapper.selectInvStockLogList(query);
        if (outboundLogs == null || outboundLogs.isEmpty())
        {
            return null;
        }

        BigDecimal outboundQuantity = BigDecimal.ZERO;
        BigDecimal outboundCost = BigDecimal.ZERO;
        for (InvStockLog log : outboundLogs)
        {
            String businessType = log.getBusinessType();
            BigDecimal changeQuantity = log.getChangeQuantity();
            BigDecimal costPrice = log.getCostPrice();
            if (!("sales".equals(businessType) || "outbound".equals(businessType))
                    || changeQuantity == null || changeQuantity.compareTo(BigDecimal.ZERO) >= 0
                    || costPrice == null || costPrice.compareTo(BigDecimal.ZERO) < 0)
            {
                continue;
            }
            BigDecimal quantity = changeQuantity.abs();
            outboundQuantity = outboundQuantity.add(quantity);
            outboundCost = outboundCost.add(quantity.multiply(costPrice));
        }
        if (outboundQuantity.compareTo(BigDecimal.ZERO) <= 0)
        {
            return null;
        }
        return outboundCost.divide(outboundQuantity, 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReturn(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn salesReturn = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        InvStateGuard.requireCancelableDocument(salesReturn.getStatus());
        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(returnId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setUpdateBy(SecurityUtils.getUsername());
        salesReturnMapper.updateInvSalesReturn(update);
    }

    private void validateReturnQuantity(InvSalesReturn salesReturn, Long excludeReturnId)
    {
        if (salesReturn.getSalesOrderId() == null)
        {
            throw new ServiceException("销售退货单未关联销售单");
        }
        List<InvSalesDetail> salesDetails = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        if (salesDetails.isEmpty())
        {
            throw new ServiceException("原销售单无明细");
        }
        List<InvSalesReturnDetail> returnDetails = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnId(excludeReturnId);
        if (returnDetails == null || returnDetails.isEmpty())
        {
            throw new ServiceException("退货单无明细");
        }
        Map<Long, InvSalesDetail> originalDetailById = new HashMap<>();
        Map<Long, InvSalesDetail> uniqueDetailByProduct = new HashMap<>();
        Map<Long, Integer> detailCountByProduct = new HashMap<>();
        Map<Long, BigDecimal> deliveredQtyByProduct = new HashMap<>();
        Map<Long, String> originalNameByProduct = new HashMap<>();
        for (InvSalesDetail sd : salesDetails)
        {
            if (sd.getProductId() == null)
            {
                continue;
            }
            if (sd.getDetailId() != null)
            {
                originalDetailById.put(sd.getDetailId(), sd);
            }
            uniqueDetailByProduct.putIfAbsent(sd.getProductId(), sd);
            detailCountByProduct.merge(sd.getProductId(), 1, Integer::sum);
            deliveredQtyByProduct.merge(sd.getProductId(),
                    sd.getDeliveredQuantity() != null ? sd.getDeliveredQuantity() : BigDecimal.ZERO,
                    BigDecimal::add);
            originalNameByProduct.putIfAbsent(sd.getProductId(), sd.getProductName());
        }
        Map<Long, BigDecimal> currentQtyByProduct = new HashMap<>();
        Map<Long, BigDecimal> currentQtyByDetail = new HashMap<>();
        Map<Long, InvSalesDetail> currentOriginalByDetail = new HashMap<>();
        Map<Long, String> returnNameByProduct = new HashMap<>();
        for (InvSalesReturnDetail rd : returnDetails)
        {
            InvSalesDetail originalDetail = resolveSalesDetailForReturn(rd, originalDetailById,
                    uniqueDetailByProduct, detailCountByProduct);
            if (rd.getQuantity() == null || rd.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量必须大于0");
            }
            currentQtyByProduct.merge(originalDetail.getProductId(), rd.getQuantity(), BigDecimal::add);
            returnNameByProduct.putIfAbsent(originalDetail.getProductId(), originalDetail.getProductName());
            if (originalDetail.getDetailId() != null)
            {
                currentQtyByDetail.merge(originalDetail.getDetailId(), rd.getQuantity(), BigDecimal::add);
                currentOriginalByDetail.putIfAbsent(originalDetail.getDetailId(), originalDetail);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : currentQtyByDetail.entrySet())
        {
            Long salesDetailId = entry.getKey();
            InvSalesDetail originalDetail = currentOriginalByDetail.get(salesDetailId);
            BigDecimal deliveredQty = originalDetail.getDeliveredQuantity() != null
                    ? originalDetail.getDeliveredQuantity() : BigDecimal.ZERO;
            BigDecimal historicalReturned = salesReturnDetailMapper.sumHistoricalReturnQuantityBySalesDetailId(
                    salesReturn.getSalesOrderId(), salesDetailId, excludeReturnId);
            if (historicalReturned == null)
            {
                historicalReturned = BigDecimal.ZERO;
            }
            BigDecimal totalReturned = entry.getValue().add(historicalReturned);
            if (totalReturned.compareTo(deliveredQty) > 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量(" + totalReturned
                        + ")超过原销售明细已出库数量(" + deliveredQty + ")，历史已退(" + historicalReturned + ")");
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : currentQtyByProduct.entrySet())
        {
            Long productId = entry.getKey();
            BigDecimal deliveredQty = deliveredQtyByProduct.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal historicalReturned = salesReturnDetailMapper.sumHistoricalReturnQuantity(
                    salesReturn.getSalesOrderId(), productId, excludeReturnId);
            if (historicalReturned == null)
            {
                historicalReturned = BigDecimal.ZERO;
            }
            BigDecimal totalReturned = entry.getValue().add(historicalReturned);
            if (totalReturned.compareTo(deliveredQty) > 0)
            {
                String productName = returnNameByProduct.getOrDefault(productId, originalNameByProduct.get(productId));
                throw new ServiceException("商品 [" + productName + "] 退货数量(" + totalReturned
                        + ")超过原销售已出库数量(" + deliveredQty + ")，历史已退(" + historicalReturned + ")");
            }
        }
    }

    private void normalizeReturnDetails(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details)
    {
        if (details == null)
        {
            return;
        }
        if (details.isEmpty())
        {
            salesReturn.setTotalAmount(BigDecimal.ZERO);
            return;
        }
        if (salesReturn.getSalesOrderId() == null)
        {
            throw new ServiceException("销售退货单未关联销售单");
        }

        List<InvSalesDetail> salesDetails = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        if (salesDetails.isEmpty())
        {
            throw new ServiceException("原销售单无明细");
        }
        Map<Long, InvSalesDetail> originalDetailById = new HashMap<>();
        Map<Long, InvSalesDetail> uniqueDetailByProduct = new HashMap<>();
        Map<Long, Integer> detailCountByProduct = new HashMap<>();
        for (InvSalesDetail salesDetail : salesDetails)
        {
            if (salesDetail.getProductId() != null)
            {
                uniqueDetailByProduct.putIfAbsent(salesDetail.getProductId(), salesDetail);
                detailCountByProduct.merge(salesDetail.getProductId(), 1, Integer::sum);
            }
            if (salesDetail.getDetailId() != null)
            {
                originalDetailById.put(salesDetail.getDetailId(), salesDetail);
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvSalesReturnDetail returnDetail : details)
        {
            InvSalesDetail originalDetail = resolveSalesDetailForReturn(returnDetail, originalDetailById,
                    uniqueDetailByProduct, detailCountByProduct);
            BigDecimal quantity = returnDetail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量必须大于0");
            }
            BigDecimal unitPrice = originalDetail.getUnitPrice() != null ? originalDetail.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal amount = quantity.multiply(unitPrice);

            returnDetail.setSalesDetailId(originalDetail.getDetailId());
            returnDetail.setProductId(originalDetail.getProductId());
            returnDetail.setProductName(originalDetail.getProductName());
            returnDetail.setSku(originalDetail.getSku());
            returnDetail.setSpec(originalDetail.getSpec());
            returnDetail.setUnit(originalDetail.getUnit());
            returnDetail.setUnitPrice(unitPrice);
            returnDetail.setAmount(amount);
            totalAmount = totalAmount.add(amount);
        }
        salesReturn.setTotalAmount(totalAmount);
    }

    private void applySourceOrderSnapshot(InvSalesReturn salesReturn, Long shopDeptId)
    {
        if (salesReturn == null || salesReturn.getSalesOrderId() == null)
        {
            throw new ServiceException("销售退货单未关联销售单");
        }
        InvSalesOrder sourceOrder = salesOrderMapper.selectInvSalesOrderById(salesReturn.getSalesOrderId());
        if (sourceOrder == null)
        {
            throw new ServiceException("原销售单不存在");
        }
        if (!shopDeptId.equals(sourceOrder.getShopDeptId()))
        {
            throw new ServiceException("原销售单不属于当前门店");
        }
        salesReturn.setSalesOrderNo(sourceOrder.getOrderNo());
        salesReturn.setCustomerName(sourceOrder.getCustomerName());
        if (salesReturn.getReturnDate() == null)
        {
            salesReturn.setReturnDate(new Date());
        }
    }

    private InvSalesDetail resolveSalesDetailForReturn(InvSalesReturnDetail returnDetail,
            Map<Long, InvSalesDetail> originalDetailById,
            Map<Long, InvSalesDetail> uniqueDetailByProduct,
            Map<Long, Integer> detailCountByProduct)
    {
        if (returnDetail.getSalesDetailId() != null)
        {
            InvSalesDetail originalDetail = originalDetailById.get(returnDetail.getSalesDetailId());
            if (originalDetail == null)
            {
                throw new ServiceException("原销售明细不存在，请重新选择原销售明细");
            }
            if (returnDetail.getProductId() != null && !returnDetail.getProductId().equals(originalDetail.getProductId()))
            {
                throw new ServiceException("退货商品与原销售明细不一致，请重新选择原销售明细");
            }
            return originalDetail;
        }
        if (returnDetail.getProductId() == null)
        {
            throw new ServiceException("退货商品不能为空");
        }
        Integer detailCount = detailCountByProduct.get(returnDetail.getProductId());
        if (detailCount == null)
        {
            throw new ServiceException("商品 [" + returnDetail.getProductName() + "] 不在原销售单中");
        }
        if (detailCount > 1)
        {
            throw new ServiceException("商品 [" + returnDetail.getProductName() + "] 在原销售单中存在多行，请选择原销售明细");
        }
        return uniqueDetailByProduct.get(returnDetail.getProductId());
    }

    private InvSalesReturn assertAndGetScopedReturn(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn db = salesReturnMapper.selectInvSalesReturnById(returnId);
        if (db == null)
        {
            throw new ServiceException("销售退货单不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺销售退货单");
        return db;
    }

    private String generateReturnNo(String prefix)
    {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }
}
