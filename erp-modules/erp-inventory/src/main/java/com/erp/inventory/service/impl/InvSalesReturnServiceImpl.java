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
import com.erp.inventory.domain.vo.InvSpecialistReadVo;
import com.erp.inventory.domain.vo.InvSpecialistActionContext;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.dto.InvSalesReturnSourceQuery;
import com.erp.inventory.domain.vo.InvSalesReturnSourceOrderVo;
import com.github.pagehelper.PageHelper;
import com.erp.inventory.domain.InvOutboundRecord;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvSalesReturn;
import com.erp.inventory.domain.InvSalesReturnDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
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
    private InvOutboundRecordMapper outboundRecordMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Override
    public InvSpecialistActionContext getActionContext(Long returnId, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        InvSalesReturn order = assertAndGetScopedReturn(returnId, shopDeptId);
        if (!shopDeptId.equals(order.getShopDeptId())) throw new ServiceException("退货单不属于当前门店");
        return InvSpecialistActionContext.salesReturn(order);
    }

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
            salesReturn.setVersion(0L);
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
            InvSalesReturn db = lockScopedReturn(salesReturn.getReturnId(), selectedShopDeptId, salesReturn.getSalesOrderId());
            InvStateGuard.requireDraftForEdit(db.getStatus());
            com.erp.inventory.support.InvDraftRevision.requireCurrent(salesReturn.getVersion(), db.getVersion());
            if (salesReturn.getSalesOrderId() == null)
            {
                salesReturn.setSalesOrderId(db.getSalesOrderId());
            }
            applySourceOrderSnapshot(salesReturn, shopDeptId);
            normalizeReturnDetails(salesReturn, details);
            salesReturn.setStatus(InvStatusConstants.DRAFT);
            salesReturn.setUpdateBy(SecurityUtils.getUsername());
            salesReturn.getParams().put("updateContent", true);
            salesReturn.getParams().put("expectedStatus", InvStatusConstants.DRAFT);
            if (salesReturnMapper.updateInvSalesReturn(salesReturn) != 1)
                throw new ServiceException("退货草稿已变化，请保留输入并核对最新版本");
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
        updateState(update, InvStatusConstants.DRAFT);
        return salesReturnMapper.selectInvSalesReturnById(saved.getReturnId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvSalesReturn submitSavedReturn(Long returnId, Long version, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        InvSalesReturn draft = lockScopedReturn(returnId, shopDeptId);
        if (!shopDeptId.equals(draft.getShopDeptId())) throw new ServiceException("退货单不属于当前门店");
        InvStateGuard.requireDraftForEdit(draft.getStatus());
        com.erp.inventory.support.InvDraftRevision.requireCurrent(version, draft.getVersion());
        if (draft.getReturnTitle() == null || draft.getReturnTitle().isBlank() || draft.getReturnTitle().length() > 128
                || draft.getCustomerName() == null || draft.getCustomerName().isBlank())
            throw new ServiceException("退货草稿主题或客户资料不完整，请先编辑保存");
        InvSalesOrder source = salesOrderMapper.selectInvSalesOrderByIdForUpdate(draft.getSalesOrderId());
        requireReturnableSourceHeader(source, shopDeptId);
        List<InvSalesDetail> sourceDetails = salesDetailMapper.selectInvSalesDetailByOrderIdForUpdate(draft.getSalesOrderId());
        List<InvSalesReturnDetail> details = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnIdForUpdate(returnId);
        validateLockedReturnQuantity(draft, returnId, sourceDetails, details);
        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(returnId); update.setStatus(InvStatusConstants.SUBMITTED);
        update.setUpdateBy(SecurityUtils.getUsername());
        updateState(update, InvStatusConstants.DRAFT);
        // Return the locked persisted draft; no body fields or stale consistent reads can replace its content.
        draft.setStatus(InvStatusConstants.SUBMITTED); draft.setVersion(draft.getVersion() + 1); draft.setDetails(details);
        return InvSpecialistReadVo.salesReturn(draft);
    }

    @Override
    public List<InvSalesOrder> selectReturnableSourceOrders(InvSalesReturnSourceQuery query, Long selectedShopDeptId)
    {
        if (query == null) query = new InvSalesReturnSourceQuery();
        query.validate();
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        // Resolve scope before starting pagination so directory lookups cannot consume the page request.
        try
        {
            var page = PageHelper.startPage(query.getPageNum(), query.getPageSize()).setReasonable(false);
            // PageHelper 6.1 calculates offset with int multiplication; keep valid large pages in long arithmetic.
            long offset = ((long) query.getPageNum() - 1L) * query.getPageSize();
            page.setStartRow(offset).setEndRow(offset + query.getPageSize());
            List<InvSalesOrder> rows = salesOrderMapper.selectReturnableSalesOrderList(query, shopDeptId);
            for (int i = 0; i < rows.size(); i++) rows.set(i, InvSalesReturnSourceOrderVo.from(rows.get(i)));
            return rows;
        }
        finally { PageHelper.clearPage(); }
    }

    @Override
    public InvSalesOrder getReturnableSourceOrder(Long orderId, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        InvSalesOrder source = salesOrderMapper.selectInvSalesOrderById(orderId);
        requireReturnableSourceHeader(source, shopDeptId);
        List<InvSalesDetail> details = salesDetailMapper.selectInvSalesDetailByOrderId(orderId);
        boolean hasReturnable = false;
        for (InvSalesDetail detail : details)
        {
            BigDecimal used = zero(salesReturnDetailMapper.sumHistoricalReturnQuantityBySalesDetailId(orderId, detail.getDetailId(), null));
            BigDecimal itemDelivered = details.stream().filter(row -> itemKey(row).equals(itemKey(detail)))
                    .map(row -> zero(row.getDeliveredQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal itemUsed = zero(salesReturnDetailMapper.sumHistoricalReturnQuantityByItem(orderId,
                    itemType(detail.getItemType()), itemId(detail.getItemId(), detail.getProductId()), null));
            BigDecimal remaining = zero(detail.getDeliveredQuantity()).subtract(used)
                    .min(itemDelivered.subtract(itemUsed)).max(BigDecimal.ZERO);
            if (detail.getDetailId() == null || itemId(detail.getItemId(), detail.getProductId()) == null
                    || !("product".equals(itemType(detail.getItemType())) || "gift".equals(itemType(detail.getItemType()))))
                remaining = BigDecimal.ZERO;
            detail.setHistoricalReturnedQuantity(used); detail.setReturnableQuantity(remaining);
            hasReturnable |= remaining.signum() > 0;
        }
        if (!hasReturnable) throw new ServiceException("原销售单暂无可退明细");
        source.setDetails(details);
        return InvSalesReturnSourceOrderVo.from(source);
    }

    private void requireReturnableSourceHeader(InvSalesOrder source, Long shopDeptId)
    {
        if (source == null) throw new ServiceException("原销售单不存在");
        if (!shopDeptId.equals(source.getShopDeptId())) throw new ServiceException("原销售单不属于当前门店");
        if (InvStatusConstants.DRAFT.equals(source.getStatus()) || InvStatusConstants.CANCELLED.equals(source.getStatus()))
            throw new ServiceException("原销售单当前状态不可退货");
    }

    @Override
    public InvSalesReturn getReturnDraft(Long returnId, Long selectedShopDeptId)
    {
        Long shopDeptId = requireStoreContext(selectedShopDeptId, "请选择门店");
        InvSalesReturn draft = getReturnDetail(returnId, shopDeptId);
        if (!shopDeptId.equals(draft.getShopDeptId())) throw new ServiceException("退货单不属于当前门店");
        InvStateGuard.requireDraftForEdit(draft.getStatus());
        return InvSpecialistReadVo.salesReturn(draft);
    }

    @Override
    public InvSalesReturn getReturnDetail(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn salesReturn = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        List<InvSalesReturnDetail> details = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnId(returnId);
        List<InvSalesDetail> source = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        for (InvSalesReturnDetail detail : details)
        {
            InvSalesDetail original;
            try
            {
                original = resolveSalesDetailForReturn(detail, source);
            }
            catch (ServiceException ambiguousHistory)
            {
                // Historical evidence remains readable; editing must reselect an unambiguous source row.
                detail.setReturnableQuantity(BigDecimal.ZERO);
                continue;
            }
            applyItemSnapshot(detail, original);
            BigDecimal used = zero(salesReturnDetailMapper.sumHistoricalReturnQuantityBySalesDetailId(
                    salesReturn.getSalesOrderId(), original.getDetailId(), returnId));
            detail.setReturnableQuantity(zero(original.getDeliveredQuantity()).subtract(used).max(BigDecimal.ZERO));
        }
        salesReturn.setDetails(details);
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
        InvSalesReturn salesReturn = lockScopedReturn(returnId, selectedShopDeptId);
        InvStateGuard.requireSubmittedForReturnConfirm(salesReturn.getStatus());
        validateReturnQuantity(salesReturn, returnId);
        List<InvSalesReturnDetail> details = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnIdForUpdate(returnId);
        if (details.isEmpty())
        {
            throw new ServiceException("退货单无明细");
        }
        List<InvSalesDetail> sourceDetails = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        for (InvSalesReturnDetail detail : details)
        {
            InvSalesDetail original = resolveSalesDetailForReturn(detail, sourceDetails);
            applyItemSnapshot(detail, original);
            BigDecimal toReturn = detail.getQuantity().subtract(
                    detail.getReturnedQuantity() != null ? detail.getReturnedQuantity() : BigDecimal.ZERO);
            if (toReturn.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                    itemType(detail.getItemType()), itemId(detail.getItemId(), detail.getProductId()),
                    salesReturn.getShopDeptId(), salesReturn.getShopDeptId());
            BigDecimal incomingCost = resolveReturnCostAmount(
                    salesReturn.getSalesOrderId(), detail, original, sourceDetails, toReturn);
            BigDecimal unitCost = incomingCost.divide(toReturn, 2, RoundingMode.HALF_UP);
            BigDecimal beforeQty = BigDecimal.ZERO;
            if (stock == null)
            {
                stock = new InvStock();
                stock.setItemType(itemType(detail.getItemType()));
                stock.setItemId(itemId(detail.getItemId(), detail.getProductId()));
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
                if (stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(), toReturn, incomingCost, SecurityUtils.getUsername()) != 1)
                {
                    throw new ServiceException("库存已变化，请刷新后重试");
                }
                stock = stockMapper.selectInvStockById(stock.getStockId());
            }

            // 库存变动日志
            InvStockLog log = new InvStockLog();
            log.setItemType(itemType(detail.getItemType()));
            log.setItemId(itemId(detail.getItemId(), detail.getProductId()));
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
            log.setCostAmount(incomingCost);
            log.setCreateBy(SecurityUtils.getUsername());
            log.setCreateTime(new Date());
            log.setRemark("销售退货入库");
            stockLogMapper.insertInvStockLog(log);

            // 更新明细已退数量
            detail.setReturnedQuantity(detail.getReturnedQuantity() != null
                    ? detail.getReturnedQuantity().add(toReturn) : toReturn);
            detail.setReturnedCostAmount(zero(detail.getReturnedCostAmount()).add(incomingCost));
            if (salesReturnDetailMapper.updateInvSalesReturnDetail(detail) != 1)
                throw new ServiceException("退货明细已变化，请刷新后重试");
        }

        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(returnId);
        update.setStatus(InvStatusConstants.RETURNED);
        update.setUpdateBy(SecurityUtils.getUsername());
        updateState(update, InvStatusConstants.SUBMITTED);
    }

    /** Allocate from the unreturned cost balance and settle rounding on the final quantity. */
    private BigDecimal resolveReturnCostAmount(Long orderId, InvSalesReturnDetail detail,
            InvSalesDetail original, List<InvSalesDetail> sourceDetails, BigDecimal toReturn)
    {
        ReturnCostBasis basis = resolveReturnCostBasis(orderId, detail, original, sourceDetails);
        BigDecimal returnedQuantity = BigDecimal.ZERO;
        BigDecimal returnedCost = BigDecimal.ZERO;
        List<InvSalesReturnDetail> facts = salesReturnDetailMapper.selectReturnedCostFacts(
                orderId, original.getDetailId(), itemType(detail.getItemType()), detail.getItemId());
        if (facts != null)
        {
            for (InvSalesReturnDetail fact : facts)
            {
                // A whole-return log cannot prove how much cost belonged to each original line.
                if (!java.util.Objects.equals(original.getDetailId(), fact.getSalesDetailId())
                        || !itemType(detail.getItemType()).equals(itemType(fact.getItemType()))
                        || !java.util.Objects.equals(detail.getItemId(), itemId(fact.getItemId(), fact.getProductId()))
                        || fact.getReturnedQuantity() == null || fact.getReturnedQuantity().signum() <= 0
                        || fact.getReturnedCostAmount() == null || fact.getReturnedCostAmount().signum() < 0)
                    throw new ServiceException("历史退货明细成本证据不完整，无法确认退货，请先核对历史退货记录");
                returnedQuantity = returnedQuantity.add(fact.getReturnedQuantity());
                returnedCost = returnedCost.add(fact.getReturnedCostAmount());
            }
        }
        BigDecimal remainingQuantity = basis.quantity.subtract(returnedQuantity);
        BigDecimal remainingCost = basis.amount.subtract(returnedCost);
        if (toReturn == null || toReturn.signum() <= 0 || remainingCost.signum() < 0
                || toReturn.compareTo(remainingQuantity) > 0)
            throw new ServiceException("原销售明细剩余可退数量或成本异常，请核对出库及历史退货记录");
        if (toReturn.compareTo(remainingQuantity) == 0) return remainingCost;
        return remainingCost.multiply(toReturn).divide(remainingQuantity, 2, RoundingMode.HALF_UP);
    }

    private static class ReturnCostBasis
    {
        private final BigDecimal quantity;
        private final BigDecimal amount;
        private ReturnCostBasis(BigDecimal quantity, BigDecimal amount)
        {
            this.quantity = quantity;
            this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** Prefer the selected source line's frozen outbound amount; never borrow another line's cost. */
    private ReturnCostBasis resolveReturnCostBasis(Long orderId, InvSalesReturnDetail detail,
            InvSalesDetail original, List<InvSalesDetail> sourceDetails)
    {
        BigDecimal delivered = zero(original.getDeliveredQuantity());
        List<InvOutboundRecord> records = outboundRecordMapper.selectInvOutboundRecordBySalesDetailId(
                orderId, original.getDetailId());
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        boolean completeCosts = records != null && !records.isEmpty();
        if (records != null)
        {
            for (InvOutboundRecord record : records)
            {
                if (!itemType(detail.getItemType()).equals(itemType(record.getItemType()))
                        || !java.util.Objects.equals(detail.getItemId(), itemId(record.getItemId(), record.getProductId())))
                    throw new ServiceException("原销售出库物料与退货明细不一致，请核对出库记录");
                if (record.getQuantity() == null || record.getQuantity().signum() <= 0)
                    throw new ServiceException("原销售出库数量异常，请核对出库记录");
                BigDecimal cost = record.getCostAmount();
                if (cost == null && record.getCostPrice() != null)
                    cost = record.getQuantity().multiply(record.getCostPrice());
                if (cost == null) completeCosts = false;
                else if (cost.signum() < 0) throw new ServiceException("原销售出库成本异常，请核对出库记录");
                else amount = amount.add(cost);
                quantity = quantity.add(record.getQuantity());
            }
        }
        if (completeCosts && quantity.signum() > 0 && quantity.compareTo(delivered) == 0)
            return new ReturnCostBasis(quantity, amount);

        // Old logs have only order + material identity. They are sufficient only when
        // that material has one delivered source line and all delivered quantity is accounted for.
        long deliveredLines = sourceDetails.stream().filter(row -> itemKey(row).equals(itemKey(original))
                && zero(row.getDeliveredQuantity()).signum() > 0).count();
        if (deliveredLines != 1) throw unknownReturnCost();
        InvSalesOrder order = salesOrderMapper.selectInvSalesOrderById(orderId);
        InvStockLog query = new InvStockLog();
        query.setBusinessId(orderId);
        query.setItemType(itemType(detail.getItemType()));
        query.setItemId(itemId(detail.getItemId(), detail.getProductId()));
        query.setProductId(detail.getProductId());
        query.setMovementType(InvStatusConstants.MOVEMENT_SALES_OUT);
        List<InvStockLog> logs = stockLogMapper.selectInvStockLogList(query);
        quantity = BigDecimal.ZERO;
        amount = BigDecimal.ZERO;
        if (logs != null)
        {
            for (InvStockLog log : logs)
            {
                boolean knownOrder = "sales".equals(log.getBusinessType())
                        || ("outbound".equals(log.getBusinessType()) && order != null
                            && order.getOrderNo() != null && order.getOrderNo().equals(log.getBusinessNo()));
                if (!knownOrder || log.getChangeQuantity() == null || log.getChangeQuantity().signum() >= 0)
                    continue;
                if (log.getCostPrice() == null || log.getCostPrice().signum() < 0) throw unknownReturnCost();
                BigDecimal delta = log.getChangeQuantity().abs();
                quantity = quantity.add(delta);
                amount = amount.add(delta.multiply(log.getCostPrice()));
            }
        }
        if (quantity.signum() <= 0 || quantity.compareTo(delivered) != 0) throw unknownReturnCost();
        return new ReturnCostBasis(quantity, amount);
    }

    private ServiceException unknownReturnCost()
    {
        return new ServiceException("原销售明细成本证据不完整，无法确认退货，请先核对原出库记录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReturn(Long returnId, Long selectedShopDeptId)
    {
        InvSalesReturn salesReturn = lockScopedReturn(returnId, selectedShopDeptId);
        InvStateGuard.requireCancelableDocument(salesReturn.getStatus());
        InvSalesReturn update = new InvSalesReturn();
        update.setReturnId(returnId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setUpdateBy(SecurityUtils.getUsername());
        updateState(update, salesReturn.getStatus());
    }

    private void validateReturnQuantity(InvSalesReturn salesReturn, Long excludeReturnId)
    {
        if (salesReturn.getSalesOrderId() == null) throw new ServiceException("销售退货单未关联销售单");
        // Serialize reservations across different return documents for the same sales order.
        if (salesOrderMapper.selectInvSalesOrderByIdForUpdate(salesReturn.getSalesOrderId()) == null)
            throw new ServiceException("原销售单不存在");
        List<InvSalesDetail> source = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        List<InvSalesReturnDetail> details = salesReturnDetailMapper.selectInvSalesReturnDetailByReturnId(excludeReturnId);
        validateLockedReturnQuantity(salesReturn, excludeReturnId, source, details);
    }

    private void validateLockedReturnQuantity(InvSalesReturn salesReturn, Long excludeReturnId,
            List<InvSalesDetail> source, List<InvSalesReturnDetail> details)
    {
        if (details == null || details.isEmpty()) throw new ServiceException("退货单无明细");
        Map<Long, BigDecimal> quantities = new HashMap<>();
        Map<String, BigDecimal> itemQuantities = new HashMap<>();
        for (InvSalesReturnDetail detail : details)
        {
            InvSalesDetail original = resolveSalesDetailForReturn(detail, source);
            if (detail.getQuantity() == null || detail.getQuantity().signum() <= 0)
                throw new ServiceException("退货数量必须大于0");
            quantities.merge(original.getDetailId(), detail.getQuantity(), BigDecimal::add);
            itemQuantities.merge(itemKey(original), detail.getQuantity(), BigDecimal::add);
        }
        for (InvSalesDetail original : source)
        {
            BigDecimal quantity = quantities.get(original.getDetailId());
            if (quantity == null) continue;
            BigDecimal historical = zero(salesReturnDetailMapper.sumHistoricalReturnQuantityBySalesDetailId(
                    salesReturn.getSalesOrderId(), original.getDetailId(), excludeReturnId));
            if (quantity.add(historical).compareTo(zero(original.getDeliveredQuantity())) > 0)
                throw new ServiceException("物料 [" + original.getProductName() + "] 退货数量超过原销售明细已出库数量，历史已退(" + historical + ")");
        }
        // Legacy rows without a source detail still consume the material's total allowance.
        for (Map.Entry<String, BigDecimal> entry : itemQuantities.entrySet())
        {
            InvSalesDetail original = source.stream().filter(row -> itemKey(row).equals(entry.getKey())).findFirst().orElseThrow();
            BigDecimal delivered = source.stream().filter(row -> itemKey(row).equals(entry.getKey()))
                    .map(row -> zero(row.getDeliveredQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal historical = zero(salesReturnDetailMapper.sumHistoricalReturnQuantityByItem(
                    salesReturn.getSalesOrderId(), itemType(original.getItemType()),
                    itemId(original.getItemId(), original.getProductId()), excludeReturnId));
            if (entry.getValue().add(historical).compareTo(delivered) > 0)
                throw new ServiceException("物料 [" + original.getProductName() + "] 退货数量超过原销售已出库数量，历史已退(" + historical + ")");
        }
    }

    private void normalizeReturnDetails(InvSalesReturn salesReturn, List<InvSalesReturnDetail> details)
    {
        if (details == null) return;
        List<InvSalesDetail> source = salesDetailMapper.selectInvSalesDetailByOrderId(salesReturn.getSalesOrderId());
        BigDecimal total = BigDecimal.ZERO;
        for (InvSalesReturnDetail detail : details)
        {
            InvSalesDetail original = resolveSalesDetailForReturn(detail, source);
            if (detail.getQuantity() == null || detail.getQuantity().signum() <= 0)
                throw new ServiceException("退货数量必须大于0");
            applyItemSnapshot(detail, original);
            detail.setUnitPrice(zero(original.getUnitPrice()));
            detail.setAmount(detail.getQuantity().multiply(detail.getUnitPrice()));
            total = total.add(detail.getAmount());
        }
        salesReturn.setTotalAmount(total);
    }

    private void applyItemSnapshot(InvSalesReturnDetail detail, InvSalesDetail original)
    {
        String type = itemType(original.getItemType());
        Long id = itemId(original.getItemId(), original.getProductId());
        if (!("product".equals(type) || "gift".equals(type)) || id == null)
            throw new ServiceException("原销售明细物料身份无效，请核对原单");
        detail.setSalesDetailId(original.getDetailId());
        detail.setItemType(type);
        detail.setItemId(id);
        detail.setItemCode(original.getItemCode() != null && !original.getItemCode().isBlank() ? original.getItemCode() : original.getSku());
        detail.setItemName(original.getItemName() != null && !original.getItemName().isBlank() ? original.getItemName() : original.getProductName());
        detail.setProductId("product".equals(type) ? id : null);
        detail.setProductName(detail.getItemName());
        detail.setSku(original.getSku());
        detail.setSpec(original.getSpec());
        detail.setUnit(original.getUnit());
    }

    private static String itemType(String type) { return type == null || type.isBlank() ? "product" : type; }
    private static Long itemId(Long id, Long productId) { return id != null ? id : productId; }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String itemKey(InvSalesDetail row)
    { return itemType(row.getItemType()) + ":" + itemId(row.getItemId(), row.getProductId()); }

    private void applySourceOrderSnapshot(InvSalesReturn salesReturn, Long shopDeptId)
    {
        if (salesReturn == null || salesReturn.getSalesOrderId() == null)
        {
            throw new ServiceException("销售退货单未关联销售单");
        }
        InvSalesOrder sourceOrder = salesOrderMapper.selectInvSalesOrderByIdForUpdate(salesReturn.getSalesOrderId());
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

    private InvSalesDetail resolveSalesDetailForReturn(InvSalesReturnDetail detail, List<InvSalesDetail> source)
    {
        List<InvSalesDetail> matches = source.stream().filter(row -> detail.getSalesDetailId() != null
                ? detail.getSalesDetailId().equals(row.getDetailId())
                : (itemType(detail.getItemType()).equals(itemType(row.getItemType()))
                    && java.util.Objects.equals(itemId(detail.getItemId(), detail.getProductId()),
                            itemId(row.getItemId(), row.getProductId())))).toList();
        if (matches.isEmpty()) throw new ServiceException("原销售明细不存在，请重新选择原销售明细");
        if (matches.size() != 1) throw new ServiceException("原销售单中存在多行相同物料，请选择原销售明细");
        InvSalesDetail original = matches.get(0);
        if ((detail.getItemType() != null && !itemType(detail.getItemType()).equals(itemType(original.getItemType())))
                || (detail.getItemId() != null && !detail.getItemId().equals(itemId(original.getItemId(), original.getProductId())))
                || (detail.getProductId() != null && !("product".equals(itemType(original.getItemType()))
                    && detail.getProductId().equals(itemId(original.getItemId(), original.getProductId())))))
            throw new ServiceException("退货物料与原销售明细不一致，请重新选择原销售明细");
        return original;
    }

    private InvSalesReturn lockScopedReturn(Long returnId, Long selectedShopDeptId)
    {
        return lockScopedReturn(returnId, selectedShopDeptId, null);
    }

    private InvSalesReturn lockScopedReturn(Long returnId, Long selectedShopDeptId, Long requestedSourceId)
    {
        InvSalesReturn snapshot = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        // Lock source orders before return rows so concurrent reservations share one lock order.
        java.util.SortedSet<Long> sourceIds = new java.util.TreeSet<>();
        if (snapshot.getSalesOrderId() != null) sourceIds.add(snapshot.getSalesOrderId());
        if (requestedSourceId != null) sourceIds.add(requestedSourceId);
        for (Long sourceId : sourceIds)
        {
            if (salesOrderMapper.selectInvSalesOrderByIdForUpdate(sourceId) == null)
                throw new ServiceException("原销售单不存在");
        }
        InvSalesReturn locked = salesReturnMapper.selectInvSalesReturnByIdForUpdate(returnId);
        if (locked == null) throw new ServiceException("销售退货单不存在");
        assertShopVisible(locked.getShopDeptId(), selectedShopDeptId, "无权访问该店铺销售退货单");
        if (!java.util.Objects.equals(snapshot.getSalesOrderId(), locked.getSalesOrderId()))
            throw new ServiceException("退货原单已变化，请刷新后重试");
        return locked;
    }

    private void updateState(InvSalesReturn update, String expectedStatus)
    {
        update.getParams().put("expectedStatus", expectedStatus);
        if (salesReturnMapper.updateInvSalesReturn(update) != 1)
            throw new ServiceException("退货单状态已变化，请刷新后重试");
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
