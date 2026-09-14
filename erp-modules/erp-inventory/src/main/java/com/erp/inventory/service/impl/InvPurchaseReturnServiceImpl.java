package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.vo.InvSpecialistReadVo;
import com.erp.inventory.domain.vo.InvSpecialistActionContext;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.domain.InvPurchaseDetail;
import com.erp.inventory.domain.InvInboundRecord;
import com.erp.inventory.domain.InvPurchaseOrder;
import com.erp.inventory.domain.InvPurchaseReturn;
import com.erp.inventory.domain.InvPurchaseReturnDetail;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvInboundRecordMapper;
import com.erp.inventory.mapper.InvPurchaseDetailMapper;
import com.erp.inventory.mapper.InvPurchaseOrderMapper;
import com.erp.inventory.mapper.InvPurchaseReturnDetailMapper;
import com.erp.inventory.mapper.InvPurchaseReturnMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvPurchaseReturnService;

@Service
public class InvPurchaseReturnServiceImpl extends InvBaseService implements IInvPurchaseReturnService
{
    private static final String PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE = "请选择仓库";
    private static final Set<String> RESPONSIBILITY_VALUES = Set.of(
            "supplier", "warehouse", "transport", "other");
    private static final int MAX_RETURN_DETAILS = 200;
    private static final int MAX_ATTACHMENTS = 5;

    @Autowired
    private InvPurchaseReturnMapper purchaseReturnMapper;

    @Autowired
    private InvPurchaseReturnDetailMapper purchaseReturnDetailMapper;

    @Autowired
    private InvPurchaseDetailMapper purchaseDetailMapper;

    @Autowired
    private InvPurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvInboundRecordMapper inboundRecordMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Override
    public InvSpecialistActionContext getActionContext(Long returnId, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseReturn order = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        assertReturnBelongsToSelectedWarehouse(order, warehouseId);
        return InvSpecialistActionContext.purchaseReturn(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseReturn saveDraft(InvPurchaseReturn purchaseReturn, List<InvPurchaseReturnDetail> details, Long selectedShopDeptId)
    {
        Long shopDeptId = requireWarehouseContext(selectedShopDeptId, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        validateAndNormalizeReturnHeader(purchaseReturn);
        requireReturnDetails(details);
        if (purchaseReturn.getReturnId() == null)
        {
            applySourceOrderSnapshot(purchaseReturn, shopDeptId);
            normalizeReturnDetails(purchaseReturn, details);
            purchaseReturn.setShopDeptId(shopDeptId);
            purchaseReturn.setApplicantId(SecurityUtils.getUserId());
            purchaseReturn.setApplicantName(SecurityUtils.getUsername());
            purchaseReturn.setApplicantDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId());
            purchaseReturn.setCreateBy(SecurityUtils.getUsername());
            purchaseReturn.setStatus(InvStatusConstants.DRAFT);
            purchaseReturn.setReturnNo(generateReturnNo("PR"));
            int inserted = purchaseReturnMapper.insertInvPurchaseReturn(purchaseReturn);
            if (inserted != 1 || purchaseReturn.getReturnId() == null)
            {
                throw new ServiceException("采购退货草稿创建失败，请重试");
            }
            applyReturnId(details, purchaseReturn.getReturnId());
            if (purchaseReturnDetailMapper.batchInsertInvPurchaseReturnDetail(details) != details.size())
            {
                throw new ServiceException("采购退货明细保存不完整，请重试");
            }
        }
        else
        {
            InvPurchaseReturn db = requireLockedScopedReturn(purchaseReturn.getReturnId(), shopDeptId);
            InvStateGuard.requireDraftForEdit(db.getStatus());
            if (purchaseReturn.getPurchaseOrderId() == null)
            {
                purchaseReturn.setPurchaseOrderId(db.getPurchaseOrderId());
            }
            else if (!purchaseReturn.getPurchaseOrderId().equals(db.getPurchaseOrderId()))
            {
                throw new ServiceException("已保存退货草稿不能更换原采购单");
            }
            applySourceOrderSnapshot(purchaseReturn, shopDeptId);
            normalizeReturnDetails(purchaseReturn, details);
            purchaseReturn.setStatus(InvStatusConstants.DRAFT);
            purchaseReturn.setUpdateBy(SecurityUtils.getUsername());
            if (purchaseReturnMapper.updateInvPurchaseReturn(purchaseReturn) != 1)
            {
                throw new ServiceException("采购退货草稿已变化，请刷新后重试");
            }
            purchaseReturnDetailMapper.deleteInvPurchaseReturnDetailByReturnId(purchaseReturn.getReturnId());
            applyReturnId(details, purchaseReturn.getReturnId());
            if (purchaseReturnDetailMapper.batchInsertInvPurchaseReturnDetail(details) != details.size())
            {
                throw new ServiceException("采购退货明细保存不完整，请重试");
            }
        }
        return getReturnDetail(purchaseReturn.getReturnId(), shopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseReturn submitReturn(InvPurchaseReturn purchaseReturn, List<InvPurchaseReturnDetail> details, Long selectedShopDeptId)
    {
        InvPurchaseReturn saved = saveDraft(purchaseReturn, details, selectedShopDeptId);
        validateReturnQuantity(saved, saved.getReturnId());
        updateReturnStatus(saved.getReturnId(), InvStatusConstants.SUBMITTED,
                "采购退货草稿已变化，请刷新后重试");
        return getReturnDetail(saved.getReturnId(), selectedShopDeptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvPurchaseReturn submitSavedReturn(Long returnId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId,
                PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseReturn locked = requireLockedScopedReturn(returnId, selectedWarehouseId);
        InvStateGuard.requireDraftForEdit(locked.getStatus());
        validateAndNormalizeReturnHeader(locked);
        requireReturnDetails(purchaseReturnDetailMapper
                .selectInvPurchaseReturnDetailByReturnIdForUpdate(returnId));
        validateReturnQuantity(locked, returnId);
        updateReturnStatus(returnId, InvStatusConstants.SUBMITTED,
                "采购退货草稿已变化，请刷新后重试");
        return getReturnDetail(returnId, selectedWarehouseId);
    }

    @Override
    public InvPurchaseReturn getReturnDraft(Long returnId, Long selectedShopDeptId)
    {
        InvPurchaseReturn draft = getReturnDetail(returnId, selectedShopDeptId);
        InvStateGuard.requireDraftForEdit(draft.getStatus());
        return InvSpecialistReadVo.purchaseReturn(draft);
    }

    @Override
    public InvPurchaseReturn getReturnDetail(Long returnId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseReturn purchaseReturn = assertAndGetScopedReturn(returnId, selectedShopDeptId);
        assertReturnBelongsToSelectedWarehouse(purchaseReturn, selectedWarehouseId);
        purchaseReturn.setDetails(purchaseReturnDetailMapper.selectInvPurchaseReturnDetailByReturnId(returnId));
        applyReturnableQuantities(purchaseReturn);
        return purchaseReturn;
    }

    @Override
    public List<InvPurchaseReturn> selectReturnList(InvPurchaseReturn purchaseReturn, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId,
                PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        purchaseReturn.setShopDeptId(warehouseId);
        return purchaseReturnMapper.selectInvPurchaseReturnList(purchaseReturn);
    }

    @Override
    public List<InvPurchaseReturn> selectMyReturns(InvPurchaseReturn purchaseReturn, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId,
                PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        purchaseReturn.setApplicantId(SecurityUtils.getUserId());
        purchaseReturn.setShopDeptId(warehouseId);
        return purchaseReturnMapper.selectMyInvPurchaseReturnList(purchaseReturn);
    }

    @Override
    public List<InvPurchaseOrder> selectReturnableSourceOrders(InvPurchaseOrder purchaseOrder,
            Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId,
                PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        purchaseOrder.setShopDeptId(warehouseId);
        return purchaseOrderMapper.selectReturnablePurchaseOrderList(purchaseOrder);
    }

    @Override
    public InvPurchaseOrder getReturnableSourceOrder(Long orderId, Long selectedShopDeptId)
    {
        Long warehouseId = requireWarehouseContext(selectedShopDeptId,
                PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseOrder sourceOrder = purchaseOrderMapper.selectInvPurchaseOrderById(orderId);
        if (sourceOrder == null)
        {
            throw new ServiceException("原采购单不存在");
        }
        if (!warehouseId.equals(sourceOrder.getShopDeptId()))
        {
            throw new ServiceException("原采购单不属于当前仓库");
        }
        if (InvStatusConstants.DRAFT.equals(sourceOrder.getStatus())
                || InvStatusConstants.CANCELLED.equals(sourceOrder.getStatus()))
        {
            throw new ServiceException("原采购单当前状态不可退货");
        }
        List<InvPurchaseDetail> details = purchaseDetailMapper
                .selectInvPurchaseDetailByOrderId(orderId);
        sourceOrder.setDetails(details);
        boolean hasReturnable = applySourceReturnableQuantities(sourceOrder);
        if (!hasReturnable)
        {
            throw new ServiceException("原采购单暂无可退明细");
        }
        return sourceOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long returnId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseReturn locked = requireLockedScopedReturn(returnId, selectedWarehouseId);
        InvStateGuard.requireSubmittedForReturnConfirm(locked.getStatus());
        validateReturnQuantity(locked, returnId);
        List<InvPurchaseReturnDetail> details = purchaseReturnDetailMapper.selectInvPurchaseReturnDetailByReturnIdForUpdate(returnId);
        if (details.isEmpty())
        {
            throw new ServiceException("退货单无明细");
        }
        for (InvPurchaseReturnDetail detail : details)
        {
            BigDecimal toReturn = detail.getQuantity().subtract(
                    detail.getReturnedQuantity() != null ? detail.getReturnedQuantity() : BigDecimal.ZERO);
            if (toReturn.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            // 校验库存（FOR UPDATE 防并发读旧成本）
            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType, detail.getItemId(), detail.getProductId());
            if (itemId == null)
            {
                throw new ServiceException("退货明细缺少物料ID");
            }
            InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                    itemType, itemId, locked.getShopDeptId(), locked.getShopDeptId());
            if (stock == null || stock.getAvailableQuantity().compareTo(toReturn) < 0)
            {
                throw new ServiceException("商品 [" + detail.getProductName() + "] 库存不足，当前可用库存: "
                        + (stock == null ? "0" : stock.getAvailableQuantity().toString()));
            }
            // 扣减库存（按移动加权平均成本扣减）
            BigDecimal beforeQty = stock.getCurrentQuantity();
            InvStockCostAllocator.Allocation allocation = InvStockCostAllocator.allocate(stock, toReturn);
            BigDecimal currentCostPrice = allocation.unitCost();
            BigDecimal deductCost = allocation.amount();
            int rows = stockMapper.deductInvStockWithCost(stock.getStockId(), stock.getVersion(), toReturn, deductCost, SecurityUtils.getUsername());
            if (rows == 0)
            {
                throw new ServiceException("商品 [" + detail.getProductName() + "] 库存不足或扣减失败");
            }
            stock = stockMapper.selectInvStockById(stock.getStockId());

            // 库存变动日志
            InvStockLog log = new InvStockLog();
            log.setItemType(itemType);
            log.setItemId(itemId);
            log.setProductId(detail.getProductId());
            log.setShopDeptId(locked.getShopDeptId());
            log.setWarehouseId(locked.getShopDeptId());
            log.setMovementType(InvStatusConstants.MOVEMENT_PURCHASE_RETURN_OUT);
            log.setBusinessType("purchase_return");
            log.setBusinessId(returnId);
            log.setBusinessNo(locked.getReturnNo());
            log.setChangeQuantity(toReturn.negate());
            log.setBeforeQuantity(beforeQty);
            log.setAfterQuantity(stock.getCurrentQuantity());
            log.setCostPrice(currentCostPrice);
            log.setCostAmount(deductCost);
            log.setCreateBy(SecurityUtils.getUsername());
            log.setCreateTime(new Date());
            log.setRemark("采购退货出库");
            if (stockLogMapper.insertInvStockLog(log) != 1)
            {
                throw new ServiceException("采购退货库存日志写入失败");
            }

            // 更新明细已退数量
            detail.setReturnedQuantity(detail.getReturnedQuantity() != null
                    ? detail.getReturnedQuantity().add(toReturn) : toReturn);
            if (purchaseReturnDetailMapper.updateInvPurchaseReturnDetail(detail) != 1)
            {
                throw new ServiceException("采购退货明细已变化，请刷新后重试");
            }
        }

        updateReturnStatus(returnId, InvStatusConstants.RETURNED,
                "采购退货单已变化，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReturn(Long returnId, Long selectedShopDeptId)
    {
        Long selectedWarehouseId = requireWarehouseContext(selectedShopDeptId, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        InvPurchaseReturn locked = requireLockedScopedReturn(returnId, selectedWarehouseId);
        InvStateGuard.requireCancelableDocument(locked.getStatus());
        updateReturnStatus(returnId, InvStatusConstants.CANCELLED,
                "采购退货单已变化，请刷新后重试");
    }

    private void validateAndNormalizeReturnHeader(InvPurchaseReturn purchaseReturn)
    {
        if (purchaseReturn == null)
        {
            throw new ServiceException("采购退货请求不能为空");
        }
        if (purchaseReturn.getPurchaseOrderId() == null
                || purchaseReturn.getPurchaseOrderId() <= 0)
        {
            throw new ServiceException("请选择原采购单");
        }
        purchaseReturn.setReturnTitle(requireText(purchaseReturn.getReturnTitle(),
                128, "退货主题不能为空", "退货主题不能超过128个字符"));
        if (purchaseReturn.getReturnDate() == null)
        {
            throw new ServiceException("请选择退货日期");
        }
        purchaseReturn.setReturnReason(requireText(purchaseReturn.getReturnReason(),
                500, "请填写退货原因", "退货原因不能超过500个字符"));
        String responsibility = purchaseReturn.getResponsibility() == null
                ? null : purchaseReturn.getResponsibility().trim();
        if (responsibility == null || !RESPONSIBILITY_VALUES.contains(responsibility))
        {
            throw new ServiceException("责任归属无效，请重新选择");
        }
        purchaseReturn.setResponsibility(responsibility);
        purchaseReturn.setAttachmentUrls(normalizeAttachmentUrls(
                purchaseReturn.getAttachmentUrls()));
        if (purchaseReturn.getRemark() != null)
        {
            String remark = purchaseReturn.getRemark().trim();
            if (remark.length() > 500)
            {
                throw new ServiceException("备注不能超过500个字符");
            }
            purchaseReturn.setRemark(remark.isEmpty() ? null : remark);
        }
    }

    private void requireReturnDetails(List<InvPurchaseReturnDetail> details)
    {
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("至少一条退货明细不能为空");
        }
        if (details.size() > MAX_RETURN_DETAILS)
        {
            throw new ServiceException("退货明细不能超过" + MAX_RETURN_DETAILS + "条");
        }
        Set<Long> sourceDetailIds = new HashSet<>();
        for (InvPurchaseReturnDetail detail : details)
        {
            if (detail == null)
            {
                throw new ServiceException("退货明细不能为空");
            }
            if (detail.getPurchaseDetailId() != null
                    && !sourceDetailIds.add(detail.getPurchaseDetailId()))
            {
                throw new ServiceException("原采购明细不能重复退货");
            }
        }
    }

    private static void applyReturnId(List<InvPurchaseReturnDetail> details,
            Long returnId)
    {
        for (InvPurchaseReturnDetail detail : details)
        {
            detail.setReturnId(returnId);
        }
    }

    private static String requireText(String value, int maxLength,
            String requiredMessage, String lengthMessage)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty())
        {
            throw new ServiceException(requiredMessage);
        }
        if (normalized.length() > maxLength)
        {
            throw new ServiceException(lengthMessage);
        }
        return normalized;
    }

    private static String normalizeAttachmentUrls(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        String[] entries = value.split(",", -1);
        if (entries.length > MAX_ATTACHMENTS)
        {
            throw new ServiceException("采购退货附件不能超过" + MAX_ATTACHMENTS + "个");
        }
        StringBuilder normalized = new StringBuilder();
        Set<String> uniqueUrls = new HashSet<>();
        for (String entry : entries)
        {
            String url = entry.trim();
            if (url.isEmpty() || url.length() > 2048 || url.indexOf('\r') >= 0
                    || url.indexOf('\n') >= 0 || !isSafeAttachmentUrl(url))
            {
                throw new ServiceException("采购退货附件地址无效");
            }
            if (!uniqueUrls.add(url))
            {
                throw new ServiceException("采购退货附件不能重复");
            }
            if (normalized.length() > 0)
            {
                normalized.append(',');
            }
            normalized.append(url);
        }
        return normalized.toString();
    }

    private static boolean isSafeAttachmentUrl(String value)
    {
        try
        {
            URI uri = new URI(value);
            if (uri.getFragment() != null || uri.getUserInfo() != null)
            {
                return false;
            }
            String path = uri.getPath();
            String rawPath = uri.getRawPath();
            String lowerRawPath = rawPath == null ? "" : rawPath.toLowerCase();
            if (path == null || !path.equals(uri.normalize().getPath())
                    || lowerRawPath.contains("%2e") || lowerRawPath.contains("%2f")
                    || lowerRawPath.contains("%5c"))
            {
                return false;
            }
            if (uri.isAbsolute())
            {
                String scheme = uri.getScheme();
                return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                        && uri.getHost() != null;
            }
            return value.startsWith("/") && !value.startsWith("//");
        }
        catch (URISyntaxException exception)
        {
            return false;
        }
    }

    private InvPurchaseOrder requireLockedSourceOrder(Long orderId, Long warehouseId)
    {
        if (orderId == null) throw new ServiceException("采购退货单未关联采购单");
        InvPurchaseOrder source = purchaseOrderMapper.selectInvPurchaseOrderByIdForUpdate(orderId);
        if (source == null) throw new ServiceException("原采购单不存在");
        if (!warehouseId.equals(source.getShopDeptId()))
            throw new ServiceException("原采购单不属于当前仓库");
        return source;
    }

    /** Every return mutation locks the original order before any return or detail row. */
    private InvPurchaseReturn requireLockedScopedReturn(Long returnId, Long warehouseId)
    {
        InvPurchaseReturn visible = assertAndGetScopedReturn(returnId, warehouseId);
        assertReturnBelongsToSelectedWarehouse(visible, warehouseId);
        InvPurchaseOrder source = requireLockedSourceOrder(visible.getPurchaseOrderId(), warehouseId);
        InvPurchaseReturn locked = requireLockedReturn(returnId);
        assertReturnBelongsToSelectedWarehouse(locked, warehouseId);
        if (!Objects.equals(source.getOrderId(), locked.getPurchaseOrderId()))
            throw new ServiceException("退货单原采购单已变化，请刷新后重试");
        return locked;
    }

    private InvPurchaseReturn requireLockedReturn(Long returnId)
    {
        InvPurchaseReturn locked = purchaseReturnMapper
                .selectInvPurchaseReturnByIdForUpdate(returnId);
        if (locked == null)
        {
            throw new ServiceException("采购退货单不存在或已被删除");
        }
        return locked;
    }

    private void updateReturnStatus(Long returnId, String status, String failureMessage)
    {
        if (purchaseReturnMapper.updateInvPurchaseReturnStatus(returnId, status,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException(failureMessage);
        }
    }

    private boolean applySourceReturnableQuantities(InvPurchaseOrder sourceOrder)
    {
        if (sourceOrder == null || sourceOrder.getOrderId() == null
                || sourceOrder.getDetails() == null)
        {
            return false;
        }
        boolean hasReturnable = false;
        for (InvPurchaseDetail detail : sourceOrder.getDetails())
        {
            BigDecimal received = detail.getStockedQuantity() == null
                    ? BigDecimal.ZERO : detail.getStockedQuantity();
            BigDecimal historical = detail.getDetailId() == null
                    ? BigDecimal.ZERO
                    : purchaseReturnDetailMapper
                            .sumHistoricalReturnQuantityByPurchaseDetailId(
                                    sourceOrder.getOrderId(), detail.getDetailId(), null);
            historical = historical == null ? BigDecimal.ZERO : historical;
            BigDecimal returnable = received.subtract(historical)
                    .max(BigDecimal.ZERO);
            detail.setHistoricalReturnedQuantity(historical);
            detail.setReturnableQuantity(returnable);
            hasReturnable |= returnable.compareTo(BigDecimal.ZERO) > 0;
        }
        return hasReturnable;
    }

    private void validateReturnQuantity(InvPurchaseReturn purchaseReturn, Long excludeReturnId)
    {
        if (purchaseReturn.getPurchaseOrderId() == null)
        {
            throw new ServiceException("采购退货单未关联采购单");
        }
        List<InvPurchaseDetail> purchaseDetails = purchaseDetailMapper.selectInvPurchaseDetailByOrderIdForUpdate(purchaseReturn.getPurchaseOrderId());
        if (purchaseDetails.isEmpty())
        {
            throw new ServiceException("原采购单无明细");
        }
        List<InvPurchaseReturnDetail> returnDetails = purchaseReturnDetailMapper.selectInvPurchaseReturnDetailByReturnIdForUpdate(excludeReturnId);
        if (returnDetails == null || returnDetails.isEmpty())
        {
            throw new ServiceException("退货单无明细");
        }
        // The purchase detail query contains non-locking subqueries; recompute accepted facts from current rows.
        applyCurrentStockedQuantities(purchaseDetails, inboundRecordMapper
                .selectByOrderIdForUpdate(purchaseReturn.getPurchaseOrderId()));
        Map<Long, BigDecimal> reservedByDetail = new HashMap<>();
        Map<Long, BigDecimal> reservedByProduct = new HashMap<>();
        for (InvPurchaseReturnDetail reserved : purchaseReturnDetailMapper.selectReservedDetailsForUpdate(
                purchaseReturn.getPurchaseOrderId(), excludeReturnId))
        {
            BigDecimal quantity = reserved.getQuantity() == null ? BigDecimal.ZERO : reserved.getQuantity();
            if (reserved.getPurchaseDetailId() != null)
                reservedByDetail.merge(reserved.getPurchaseDetailId(), quantity, BigDecimal::add);
            if (reserved.getProductId() != null)
                reservedByProduct.merge(reserved.getProductId(), quantity, BigDecimal::add);
        }
        Map<Long, InvPurchaseDetail> originalDetailById = new HashMap<>();
        Map<Long, InvPurchaseDetail> uniqueDetailByProduct = new HashMap<>();
        Map<Long, Integer> detailCountByProduct = new HashMap<>();
        Map<Long, BigDecimal> receivedQtyByProduct = new HashMap<>();
        Map<Long, String> originalNameByProduct = new HashMap<>();
        for (InvPurchaseDetail pd : purchaseDetails)
        {
            if (pd.getDetailId() != null)
            {
                originalDetailById.put(pd.getDetailId(), pd);
            }
            if (pd.getProductId() == null)
            {
                continue;
            }
            uniqueDetailByProduct.putIfAbsent(pd.getProductId(), pd);
            detailCountByProduct.merge(pd.getProductId(), 1, Integer::sum);
            receivedQtyByProduct.merge(pd.getProductId(),
                    pd.getStockedQuantity() != null ? pd.getStockedQuantity() : BigDecimal.ZERO,
                    BigDecimal::add);
            originalNameByProduct.putIfAbsent(pd.getProductId(), pd.getProductName());
        }
        Map<Long, BigDecimal> currentQtyByProduct = new HashMap<>();
        Map<Long, BigDecimal> currentQtyByDetail = new HashMap<>();
        Map<Long, InvPurchaseDetail> currentOriginalByDetail = new HashMap<>();
        Map<Long, String> returnNameByProduct = new HashMap<>();
        for (InvPurchaseReturnDetail rd : returnDetails)
        {
            InvPurchaseDetail originalDetail = resolvePurchaseDetailForReturn(rd, originalDetailById,
                    uniqueDetailByProduct, detailCountByProduct);
            if (rd.getQuantity() == null || rd.getQuantity().compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量必须大于0");
            }
            if (originalDetail.getProductId() != null)
            {
                currentQtyByProduct.merge(originalDetail.getProductId(), rd.getQuantity(), BigDecimal::add);
                returnNameByProduct.putIfAbsent(originalDetail.getProductId(), originalDetail.getProductName());
            }
            if (originalDetail.getDetailId() != null)
            {
                currentQtyByDetail.merge(originalDetail.getDetailId(), rd.getQuantity(), BigDecimal::add);
                currentOriginalByDetail.putIfAbsent(originalDetail.getDetailId(), originalDetail);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : currentQtyByDetail.entrySet())
        {
            Long purchaseDetailId = entry.getKey();
            InvPurchaseDetail originalDetail = currentOriginalByDetail.get(purchaseDetailId);
            BigDecimal receivedQty = originalDetail.getStockedQuantity() != null
                    ? originalDetail.getStockedQuantity() : BigDecimal.ZERO;
            BigDecimal historicalReturned = reservedByDetail.getOrDefault(purchaseDetailId, BigDecimal.ZERO);
            if (historicalReturned == null)
            {
                historicalReturned = BigDecimal.ZERO;
            }
            BigDecimal totalReturned = entry.getValue().add(historicalReturned);
            if (totalReturned.compareTo(receivedQty) > 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量(" + totalReturned
                        + ")超过原采购明细已合格或让步入库数量(" + receivedQty + ")，历史已退(" + historicalReturned + ")");
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : currentQtyByProduct.entrySet())
        {
            Long productId = entry.getKey();
            BigDecimal receivedQty = receivedQtyByProduct.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal historicalReturned = reservedByProduct.getOrDefault(productId, BigDecimal.ZERO);
            if (historicalReturned == null)
            {
                historicalReturned = BigDecimal.ZERO;
            }
            BigDecimal totalReturned = entry.getValue().add(historicalReturned);
            if (totalReturned.compareTo(receivedQty) > 0)
            {
                String productName = returnNameByProduct.getOrDefault(productId, originalNameByProduct.get(productId));
                throw new ServiceException("商品 [" + productName + "] 退货数量(" + totalReturned
                        + ")超过原采购已合格或让步入库数量(" + receivedQty + ")，历史已退(" + historicalReturned + ")");
            }
        }
    }

    private void applyCurrentStockedQuantities(List<InvPurchaseDetail> details, List<InvInboundRecord> inbounds)
    {
        Map<Long, Integer> productCounts = new HashMap<>();
        for (InvPurchaseDetail detail : details)
            if (detail.getProductId() != null) productCounts.merge(detail.getProductId(), 1, Integer::sum);
        for (InvPurchaseDetail detail : details)
        {
            BigDecimal stocked = BigDecimal.ZERO;
            for (InvInboundRecord inbound : inbounds)
            {
                boolean matches = inbound.getPurchaseDetailId() != null
                        ? Objects.equals(inbound.getPurchaseDetailId(), detail.getDetailId())
                        : detail.getProductId() != null
                            && Objects.equals(inbound.getProductId(), detail.getProductId())
                            && productCounts.getOrDefault(detail.getProductId(), 0) == 1;
                if (!matches) continue;
                if (inbound.getReceiptBatchDetailId() != null)
                {
                    stocked = stocked.add(inbound.getAcceptedQuantity() == null ? BigDecimal.ZERO : inbound.getAcceptedQuantity())
                            .add(inbound.getConcessionQuantity() == null ? BigDecimal.ZERO : inbound.getConcessionQuantity());
                }
                else if ("passed".equals(inbound.getQcResult()) || "concession".equals(inbound.getQcResult()))
                {
                    stocked = stocked.add(inbound.getQuantity() == null ? BigDecimal.ZERO : inbound.getQuantity());
                }
            }
            detail.setStockedQuantity(stocked);
        }
    }

    private void normalizeReturnDetails(InvPurchaseReturn purchaseReturn, List<InvPurchaseReturnDetail> details)
    {
        if (details == null || details.isEmpty())
        {
            throw new ServiceException("至少一条退货明细不能为空");
        }
        if (purchaseReturn.getPurchaseOrderId() == null)
        {
            throw new ServiceException("采购退货单未关联采购单");
        }

        List<InvPurchaseDetail> purchaseDetails = purchaseDetailMapper.selectInvPurchaseDetailByOrderIdForUpdate(purchaseReturn.getPurchaseOrderId());
        if (purchaseDetails.isEmpty())
        {
            throw new ServiceException("原采购单无明细");
        }
        Map<Long, InvPurchaseDetail> originalDetailById = new HashMap<>();
        Map<Long, InvPurchaseDetail> uniqueDetailByProduct = new HashMap<>();
        Map<Long, Integer> detailCountByProduct = new HashMap<>();
        for (InvPurchaseDetail purchaseDetail : purchaseDetails)
        {
            if (purchaseDetail.getProductId() != null)
            {
                uniqueDetailByProduct.putIfAbsent(purchaseDetail.getProductId(), purchaseDetail);
                detailCountByProduct.merge(purchaseDetail.getProductId(), 1, Integer::sum);
            }
            if (purchaseDetail.getDetailId() != null)
            {
                originalDetailById.put(purchaseDetail.getDetailId(), purchaseDetail);
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvPurchaseReturnDetail returnDetail : details)
        {
            InvPurchaseDetail originalDetail = resolvePurchaseDetailForReturn(returnDetail, originalDetailById,
                    uniqueDetailByProduct, detailCountByProduct);
            BigDecimal quantity = returnDetail.getQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            {
                throw new ServiceException("商品 [" + originalDetail.getProductName() + "] 退货数量必须大于0");
            }
            BigDecimal unitPrice = originalDetail.getUnitPrice() != null ? originalDetail.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal amount = quantity.multiply(unitPrice);

            returnDetail.setPurchaseDetailId(originalDetail.getDetailId());
            String itemType = InvItemTypes.normalize(originalDetail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    originalDetail.getItemId(), originalDetail.getProductId());
            returnDetail.setItemType(itemType);
            returnDetail.setItemId(itemId);
            returnDetail.setItemCode(originalDetail.getItemCode());
            returnDetail.setItemName(originalDetail.getItemName());
            returnDetail.setProductId(originalDetail.getProductId());
            returnDetail.setProductName(originalDetail.getProductName());
            returnDetail.setSku(originalDetail.getSku());
            returnDetail.setSpec(originalDetail.getSpec());
            returnDetail.setUnit(originalDetail.getUnit());
            returnDetail.setUnitPrice(unitPrice);
            returnDetail.setAmount(amount);
            totalAmount = totalAmount.add(amount);
        }
        purchaseReturn.setTotalAmount(totalAmount);
    }

    private void applySourceOrderSnapshot(InvPurchaseReturn purchaseReturn, Long warehouseId)
    {
        if (purchaseReturn == null || purchaseReturn.getPurchaseOrderId() == null)
        {
            throw new ServiceException("采购退货单未关联采购单");
        }
        InvPurchaseOrder sourceOrder = requireLockedSourceOrder(purchaseReturn.getPurchaseOrderId(), warehouseId);
        purchaseReturn.setPurchaseOrderNo(sourceOrder.getOrderNo());
        purchaseReturn.setSupplierName(sourceOrder.getSupplierName());
    }

    private void applyReturnableQuantities(InvPurchaseReturn purchaseReturn)
    {
        if (purchaseReturn == null || purchaseReturn.getPurchaseOrderId() == null
                || purchaseReturn.getDetails() == null)
        {
            return;
        }
        Map<Long, InvPurchaseDetail> originals = new HashMap<>();
        for (InvPurchaseDetail detail : purchaseDetailMapper
                .selectInvPurchaseDetailByOrderId(purchaseReturn.getPurchaseOrderId()))
        {
            if (detail.getDetailId() != null)
            {
                originals.put(detail.getDetailId(), detail);
            }
        }
        for (InvPurchaseReturnDetail detail : purchaseReturn.getDetails())
        {
            InvPurchaseDetail original = originals.get(detail.getPurchaseDetailId());
            if (original == null)
            {
                detail.setReturnableQuantity(BigDecimal.ZERO);
                continue;
            }
            BigDecimal received = original.getStockedQuantity() == null
                    ? BigDecimal.ZERO : original.getStockedQuantity();
            BigDecimal historical = purchaseReturnDetailMapper
                    .sumHistoricalReturnQuantityByPurchaseDetailId(
                            purchaseReturn.getPurchaseOrderId(), original.getDetailId(),
                            purchaseReturn.getReturnId());
            historical = historical == null ? BigDecimal.ZERO : historical;
            detail.setReturnableQuantity(received.subtract(historical).max(BigDecimal.ZERO));
            if (detail.getItemId() == null)
            {
                String itemType = InvItemTypes.normalize(original.getItemType());
                detail.setItemType(itemType);
                detail.setItemId(InvItemTypes.resolveItemId(itemType,
                        original.getItemId(), original.getProductId()));
                detail.setItemCode(original.getItemCode());
                detail.setItemName(original.getItemName());
            }
        }
    }

    private InvPurchaseDetail resolvePurchaseDetailForReturn(InvPurchaseReturnDetail returnDetail,
            Map<Long, InvPurchaseDetail> originalDetailById,
            Map<Long, InvPurchaseDetail> uniqueDetailByProduct,
            Map<Long, Integer> detailCountByProduct)
    {
        if (returnDetail.getPurchaseDetailId() != null)
        {
            InvPurchaseDetail originalDetail = originalDetailById.get(returnDetail.getPurchaseDetailId());
            if (originalDetail == null)
            {
                throw new ServiceException("原采购明细不存在，请重新选择原采购明细");
            }
            String originalType = InvItemTypes.normalize(originalDetail.getItemType());
            Long originalItemId = InvItemTypes.resolveItemId(originalType,
                    originalDetail.getItemId(), originalDetail.getProductId());
            String returnType = InvItemTypes.normalize(returnDetail.getItemType());
            Long returnItemId = InvItemTypes.resolveItemId(returnType,
                    returnDetail.getItemId(), returnDetail.getProductId());
            if (returnItemId != null
                    && (!returnType.equals(originalType) || !returnItemId.equals(originalItemId)))
            {
                throw new ServiceException("退货物料与原采购明细不一致，请重新选择原采购明细");
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
            throw new ServiceException("商品 [" + returnDetail.getProductName() + "] 不在原采购单中");
        }
        if (detailCount > 1)
        {
            throw new ServiceException("商品 [" + returnDetail.getProductName() + "] 在原采购单中存在多行，请选择原采购明细");
        }
        return uniqueDetailByProduct.get(returnDetail.getProductId());
    }

    private InvPurchaseReturn assertAndGetScopedReturn(Long returnId, Long selectedShopDeptId)
    {
        InvPurchaseReturn db = purchaseReturnMapper.selectInvPurchaseReturnById(returnId);
        if (db == null)
        {
            throw new ServiceException("采购退货单不存在");
        }
        assertShopVisible(db.getShopDeptId(), selectedShopDeptId, "无权访问该店铺采购退货单");
        return db;
    }

    private void assertReturnBelongsToSelectedWarehouse(InvPurchaseReturn purchaseReturn, Long selectedWarehouseId)
    {
        assertDeptType(purchaseReturn.getShopDeptId(), DEPT_TYPE_WAREHOUSE, PURCHASE_RETURN_WAREHOUSE_CONTEXT_MESSAGE);
        if (!selectedWarehouseId.equals(purchaseReturn.getShopDeptId()))
        {
            throw new ServiceException("只能操作当前仓库采购退货单");
        }
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
