package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvItemTypes;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.constant.InvTransferTypes;
import com.erp.inventory.domain.InvDeliveryNotice;
import com.erp.inventory.domain.InvDeliveryNoticeDetail;
import com.erp.inventory.domain.InvOutboundRecord;
import com.erp.inventory.domain.InvSalesDetail;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.InvTransferDetail;
import com.erp.inventory.domain.InvTransferOrder;
import com.erp.inventory.domain.dto.InvDeliverItem;
import com.erp.inventory.domain.dto.InvDeliverRequest;
import com.erp.inventory.mapper.InvDeliveryNoticeDetailMapper;
import com.erp.inventory.mapper.InvDeliveryNoticeMapper;
import com.erp.inventory.mapper.InvNumberSequenceMapper;
import com.erp.inventory.mapper.InvOutboundRecordMapper;
import com.erp.inventory.mapper.InvSalesDetailMapper;
import com.erp.inventory.mapper.InvSalesOrderMapper;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;
import com.erp.inventory.service.IInvDeliveryNoticeService;
import com.erp.inventory.service.IInvTransferService;

@Service
public class InvDeliveryNoticeServiceImpl extends InvBaseService implements IInvDeliveryNoticeService
{

    @Autowired
    private InvDeliveryNoticeMapper noticeMapper;

    @Autowired
    private InvDeliveryNoticeDetailMapper noticeDetailMapper;

    @Autowired
    private InvSalesOrderMapper salesOrderMapper;

    @Autowired
    private InvSalesDetailMapper salesDetailMapper;

    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    @Autowired
    private InvOutboundRecordMapper outboundRecordMapper;

    @Autowired
    private InvNumberSequenceMapper numberSequenceMapper;

    @Autowired
    private IInvTransferService transferService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvDeliveryNotice createNotice(Long salesOrderId, Long selectedShopDeptId)
    {
        // 行级锁防并发：先锁销售单再校验状态，避免与deliverSales并发覆盖
        InvSalesOrder salesOrder = salesOrderMapper.selectInvSalesOrderByIdForUpdate(salesOrderId);
        if (salesOrder == null)
        {
            throw new ServiceException("销售单不存在");
        }
        assertShopVisible(salesOrder.getShopDeptId(), selectedShopDeptId, "无权访问该店铺销售单");
        InvStateGuard.requireDeliveryNoticeCreatable(salesOrder.getStatus());

        List<InvDeliveryNotice> existingNotices = noticeMapper.selectInvDeliveryNoticeBySalesOrderId(salesOrderId);
        if (hasActiveNotice(existingNotices))
        {
            throw new ServiceException("该销售单已有未完成发货通知，请先处理现有通知");
        }

        List<InvSalesDetail> salesDetails = salesDetailMapper.selectInvSalesDetailByOrderId(salesOrderId);
        if (salesDetails.isEmpty())
        {
            throw new ServiceException("销售单无明细");
        }

        // 仅未出库的商品生成通知
        List<InvSalesDetail> pendingDetails = new java.util.ArrayList<>();
        for (InvSalesDetail d : salesDetails)
        {
            BigDecimal delivered = d.getDeliveredQuantity() != null ? d.getDeliveredQuantity() : BigDecimal.ZERO;
            BigDecimal remaining = d.getQuantity().subtract(delivered);
            if (remaining.compareTo(BigDecimal.ZERO) > 0)
            {
                pendingDetails.add(d);
            }
        }
        if (pendingDetails.isEmpty())
        {
            throw new ServiceException("该销售单所有商品已出库完成");
        }

        InvDeliveryNotice notice = new InvDeliveryNotice();
        notice.setNoticeNo(generateOrderNo("DN"));
        notice.setSalesOrderId(salesOrderId);
        notice.setSalesOrderNo(salesOrder.getOrderNo());
        notice.setCustomerName(resolveNoticeCustomerName(salesOrder));
        notice.setStatus(InvStatusConstants.PENDING);
        notice.setShopDeptId(salesOrder.getShopDeptId());
        notice.setWarehouseId(resolveNoticeWarehouseId(pendingDetails));
        notice.setCreateBy(SecurityUtils.getUsername());
        noticeMapper.insertInvDeliveryNotice(notice);

        List<InvDeliveryNoticeDetail> noticeDetails = new java.util.ArrayList<>();
        for (InvSalesDetail sd : pendingDetails)
        {
            if (sd.getWarehouseId() == null || sd.getWarehouseId() == 0)
            {
                throw new ServiceException("销售明细缺少发货仓库，无法冻结发货通知快照");
            }
            BigDecimal delivered = sd.getDeliveredQuantity() != null ? sd.getDeliveredQuantity() : BigDecimal.ZERO;
            BigDecimal remaining = sd.getQuantity().subtract(delivered);
            InvDeliveryNoticeDetail nd = new InvDeliveryNoticeDetail();
            nd.setNoticeId(notice.getNoticeId());
            nd.setSalesDetailId(sd.getDetailId());
            nd.setItemType(InvItemTypes.normalize(sd.getItemType()));
            nd.setItemId(InvItemTypes.resolveItemId(nd.getItemType(), sd.getItemId(), sd.getProductId()));
            nd.setItemCode(sd.getItemCode() != null ? sd.getItemCode() : sd.getSku());
            nd.setItemName(sd.getItemName() != null ? sd.getItemName() : sd.getProductName());
            nd.setProductId(sd.getProductId());
            nd.setProductName(nd.getItemName());
            nd.setNoticeQty(remaining);
            nd.setDeliveredQty(BigDecimal.ZERO);
            nd.setWarehouseId(sd.getWarehouseId());
            nd.setDeliveredCostAmount(BigDecimal.ZERO);
            noticeDetails.add(nd);
        }
        noticeDetailMapper.batchInsertInvDeliveryNoticeDetail(noticeDetails);

        // 获取锁后再更新销售单状态为已通知，不会被deliverSales覆盖
        InvSalesOrder updateSo = new InvSalesOrder();
        updateSo.setOrderId(salesOrderId);
        updateSo.setStatus(InvStatusConstants.NOTICED);
        updateSo.setUpdateBy(SecurityUtils.getUsername());
        salesOrderMapper.updateInvSalesOrder(updateSo);

        return noticeMapper.selectInvDeliveryNoticeById(notice.getNoticeId());
    }

    @Override
    public InvDeliveryNotice getNoticeDetail(Long noticeId, Long selectedShopDeptId)
    {
        InvDeliveryNotice notice = assertAndGetScopedNotice(noticeId, selectedShopDeptId);
        notice.setDetails(noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(noticeId));
        return notice;
    }

    @Override
    public List<InvDeliveryNotice> selectNoticeList(InvDeliveryNotice notice, Long selectedShopDeptId)
    {
        appendShopScope(notice, selectedShopDeptId);
        return noticeMapper.selectInvDeliveryNoticeList(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deliverNotice(Long noticeId, InvDeliverRequest request, Long selectedShopDeptId)
    {
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
        {
            throw new ServiceException("发货明细不能为空");
        }
        InvDeliveryNotice notice = getExistingNotice(noticeId);
        Long warehouseId = resolveDeliveryWarehouseId(notice, request);
        assertDeptType(warehouseId, DEPT_TYPE_WAREHOUSE, "发货仓库不合法");
        assertShopVisible(warehouseId, selectedShopDeptId, "无权操作该发货仓库");
        assertCanAccessNoticeFromSelectedContext(notice, selectedShopDeptId);
        InvStateGuard.requireDeliveryNoticeDeliverable(notice.getStatus());

        InvDeliveryNotice locked = noticeMapper.selectInvDeliveryNoticeByIdForUpdate(noticeId);
        InvStateGuard.requireDeliveryNoticeDeliverable(locked.getStatus());
        warehouseId = resolveDeliveryWarehouseId(locked, request);
        assertDeptType(warehouseId, DEPT_TYPE_WAREHOUSE, "发货仓库不合法");
        assertShopVisible(warehouseId, selectedShopDeptId, "无权操作该发货仓库");
        assertCanAccessNoticeFromSelectedContext(locked, selectedShopDeptId);

        List<InvDeliveryNoticeDetail> details = noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(noticeId);
        if (details.isEmpty())
        {
            throw new ServiceException("发货通知无明细");
        }

        java.util.Map<Long, InvDeliveryNoticeDetail> detailMap = new java.util.HashMap<>();
        for (InvDeliveryNoticeDetail d : details)
        {
            detailMap.put(d.getDetailId(), d);
        }

        // 一次性查询销售明细，构建 salesDetailId -> InvSalesDetail 映射
        List<InvSalesDetail> salesDetails = salesDetailMapper.selectInvSalesDetailByOrderId(notice.getSalesOrderId());
        java.util.Map<Long, InvSalesDetail> salesDetailMap = new java.util.HashMap<>();
        for (InvSalesDetail sd : salesDetails)
        {
            salesDetailMap.put(sd.getDetailId(), sd);
        }

        // 查询销售单获取目标门店
        InvSalesOrder salesOrder = salesOrderMapper.selectInvSalesOrderById(notice.getSalesOrderId());
        boolean crossStoreTransferPending = shouldCreateCrossStoreTransfer(notice, salesOrder);
        String transferOrderNo = null;

        boolean anyDelivered = false;
        for (InvDeliverItem item : request.getItems())
        {
            BigDecimal toDeliver = item.getDeliverQuantity();
            if (toDeliver == null || toDeliver.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            InvDeliveryNoticeDetail detail = detailMap.get(item.getDetailId());
            if (detail == null)
            {
                throw new ServiceException("明细ID不存在: " + item.getDetailId());
            }
            BigDecimal remaining = detail.getNoticeQty().subtract(
                    detail.getDeliveredQty() != null ? detail.getDeliveredQty() : BigDecimal.ZERO);
            if (toDeliver.compareTo(remaining) > 0)
            {
                throw new ServiceException("发货数量超过未发数量");
            }

            BigDecimal deliveredBefore = detail.getDeliveredQty() != null
                    ? detail.getDeliveredQty() : BigDecimal.ZERO;
            if (deliveredBefore.compareTo(BigDecimal.ZERO) > 0
                    && detail.getDeliveredCostAmount() == null)
            {
                throw new ServiceException("通知明细[" + detail.getDetailId()
                        + "]已有发货数量但成本未知，无法继续出库");
            }

            InvSalesDetail sd = salesDetailMap.get(detail.getSalesDetailId());
            if (sd == null)
            {
                throw new ServiceException("发货通知明细商品 [" + detail.getProductName()
                        + "] (salesDetailId=" + detail.getSalesDetailId()
                        + ") 在销售单(" + notice.getSalesOrderId() + ")中找不到对应明细");
            }
            assertNoticeDetailWarehouse(detail, warehouseId);

            String itemType = InvItemTypes.normalize(detail.getItemType());
            Long itemId = InvItemTypes.resolveItemId(itemType,
                    detail.getItemId(), detail.getProductId());
            if (InvItemTypes.isOe(itemType))
            {
                throw new ServiceException("OE不能用于销售发货流程");
            }
            if (itemId == null)
            {
                throw new ServiceException("发货通知明细缺少物料ID");
            }

            InvStock stock = stockMapper.selectInvStockByItemShopWarehouseForUpdate(
                    itemType, itemId, warehouseId, warehouseId);
            if (stock == null || stock.getAvailableQuantity().compareTo(toDeliver) < 0)
            {
                throw new ServiceException("物料 [" + detail.getProductName() + "] 库存不足，当前可用库存: "
                        + (stock == null ? "0" : stock.getAvailableQuantity().toString()));
            }
            if (stock.getCostPrice() == null)
            {
                throw new ServiceException("物料 [" + detail.getProductName()
                        + "] 库存成本未知，无法出库");
            }
            if (stock.getCostPrice().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("物料 [" + detail.getProductName()
                        + "] 库存成本非法，无法出库");
            }

            BigDecimal beforeQty = stock.getCurrentQuantity();
            BigDecimal currentCostPrice = stock.getCostPrice()
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal deductCost = toDeliver.multiply(currentCostPrice)
                    .setScale(2, RoundingMode.HALF_UP);

            anyDelivered = true;

            // 出库记录
            InvOutboundRecord outbound = new InvOutboundRecord();
            outbound.setSalesOrderId(notice.getSalesOrderId());
            outbound.setOrderNo(notice.getSalesOrderNo());
            outbound.setItemType(itemType);
            outbound.setItemId(itemId);
            outbound.setProductId(detail.getProductId());
            outbound.setShopDeptId(warehouseId);
            outbound.setQuantity(toDeliver);
            outbound.setCreateBy(SecurityUtils.getUsername());
            outbound.setCreateTime(new Date());
            outbound.setNoticeId(notice.getNoticeId());
            outbound.setNoticeDetailId(detail.getDetailId());
            outbound.setCostPrice(currentCostPrice);
            outbound.setCostAmount(deductCost);
            outboundRecordMapper.insertInvOutboundRecord(outbound);

            // 原子扣减库存
            int rows = stockMapper.deductInvStockWithCost(stock.getStockId(), stock.getVersion(), toDeliver, deductCost, SecurityUtils.getUsername());
            if (rows == 0)
            {
                throw new ServiceException("物料 [" + detail.getProductName() + "] 库存不足或扣减失败");
            }
            InvStock updatedStock = stockMapper.selectInvStockById(stock.getStockId());
            if (updatedStock == null || updatedStock.getCurrentQuantity() == null)
            {
                throw new ServiceException("物料 [" + detail.getProductName()
                        + "] 扣库后库存快照缺失");
            }

            // 库存变动日志
            InvStockLog log = new InvStockLog();
            log.setItemType(itemType);
            log.setItemId(itemId);
            log.setProductId(detail.getProductId());
            log.setShopDeptId(warehouseId);
            log.setWarehouseId(warehouseId);
            log.setMovementType(InvStatusConstants.MOVEMENT_SALES_OUT);
            log.setBusinessType("sales");
            log.setBusinessId(notice.getSalesOrderId());
            log.setBusinessNo(notice.getSalesOrderNo());
            log.setChangeQuantity(toDeliver.negate());
            log.setBeforeQuantity(beforeQty);
            log.setAfterQuantity(updatedStock.getCurrentQuantity());
            log.setCostPrice(currentCostPrice);
            log.setCreateBy(SecurityUtils.getUsername());
            log.setCreateTime(new Date());
            log.setRemark("销售出库-发货通知");
            stockLogMapper.insertInvStockLog(log);

            // 更新通知明细已发数量和冻结成本快照
            BigDecimal batchCost = deductCost;
            int detailRows = noticeDetailMapper.accumulateDelivery(
                    detail.getDetailId(), deliveredBefore, toDeliver, batchCost);
            if (detailRows != 1)
            {
                throw new ServiceException("发货通知明细已被其他请求修改，请刷新后重试");
            }
            detail.setDeliveredQty(deliveredBefore.add(toDeliver));
            detail.setDeliveredCostAmount((detail.getDeliveredCostAmount() == null
                    ? BigDecimal.ZERO : detail.getDeliveredCostAmount())
                    .add(batchCost));

            // 同步更新销售单明细已出库数量
            sd.setDeliveredQuantity(sd.getDeliveredQuantity() != null
                    ? sd.getDeliveredQuantity().add(toDeliver) : toDeliver);
            salesDetailMapper.updateInvSalesDetail(sd);
        }

        if (!anyDelivered)
        {
            throw new ServiceException("请至少录入一条有效发货数量");
        }

        // 判断是否全部发完
        List<InvDeliveryNoticeDetail> updatedDetails = noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(noticeId);
        boolean allDelivered = updatedDetails.stream().allMatch(d ->
        {
            BigDecimal deliveredQty = d.getDeliveredQty() != null ? d.getDeliveredQty() : BigDecimal.ZERO;
            return deliveredQty.compareTo(d.getNoticeQty()) >= 0;
        });

        // 未知成本 fail-closed
        for (InvDeliveryNoticeDetail d : updatedDetails)
        {
            BigDecimal deliveredQty = d.getDeliveredQty() == null
                    ? BigDecimal.ZERO : d.getDeliveredQty();
            if (deliveredQty.compareTo(BigDecimal.ZERO) > 0
                    && d.getDeliveredCostAmount() == null)
            {
                throw new ServiceException("通知明细[" + d.getDetailId() + "] 成本未知，无法创建调拨，fail-closed");
            }
            if (d.getDeliveredCostAmount() != null
                    && d.getDeliveredCostAmount().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("通知明细[" + d.getDetailId() + "] 冻结成本非法");
            }
        }

        InvDeliveryNotice update = new InvDeliveryNotice();
        update.setNoticeId(noticeId);
        update.setStatus(allDelivered ? InvStatusConstants.COMPLETED : InvStatusConstants.DELIVERING);
        update.setUpdateBy(SecurityUtils.getUsername());
        noticeMapper.updateInvDeliveryNotice(update);

        // 通知全部发完 → 销售单状态改为已出库
        if (allDelivered)
        {
            List<InvSalesDetail> sdList = salesDetailMapper.selectInvSalesDetailByOrderId(notice.getSalesOrderId());
            boolean salesFullyDelivered = sdList.stream().allMatch(sd ->
            {
                BigDecimal delivered = sd.getDeliveredQuantity() != null ? sd.getDeliveredQuantity() : BigDecimal.ZERO;
                return delivered.compareTo(sd.getQuantity()) >= 0;
            });
            if (salesFullyDelivered)
            {
                InvSalesOrder soUpdate = new InvSalesOrder();
                soUpdate.setOrderId(notice.getSalesOrderId());
                soUpdate.setStatus(InvStatusConstants.DELIVERED);
                soUpdate.setUpdateBy(SecurityUtils.getUsername());
                salesOrderMapper.updateInvSalesOrder(soUpdate);

                if (crossStoreTransferPending)
                {
                    transferOrderNo = createCrossStoreTransferForReceipt(
                            notice, salesOrder, updatedDetails, salesDetailMap, selectedShopDeptId);
                }
            }
        }

        return buildDeliveryResultMessage(crossStoreTransferPending, transferOrderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelNotice(Long noticeId, Long selectedShopDeptId)
    {
        // 行级锁防并发：先锁通知再校验状态
        InvDeliveryNotice notice = noticeMapper.selectInvDeliveryNoticeByIdForUpdate(noticeId);
        if (notice == null)
        {
            throw new ServiceException("发货通知不存在");
        }
        assertCanAccessNoticeFromSelectedContext(notice, selectedShopDeptId);
        if (InvStatusConstants.CANCELLED.equals(notice.getStatus()))
        {
            throw new ServiceException("发货通知已取消，请勿重复操作");
        }
        InvStateGuard.requirePendingForCancel(notice.getStatus());
        // 将通知标记为已作废
        InvDeliveryNotice update = new InvDeliveryNotice();
        update.setNoticeId(noticeId);
        update.setStatus(InvStatusConstants.CANCELLED);
        update.setUpdateBy(SecurityUtils.getUsername());
        noticeMapper.updateInvDeliveryNotice(update);
        // 重新计算销售单状态
        recalcSalesOrderStatus(notice.getSalesOrderId());
    }

    private void recalcSalesOrderStatus(Long salesOrderId)
    {
        // 行级锁防并发：锁销售单后再重算状态
        InvSalesOrder lockedSo = salesOrderMapper.selectInvSalesOrderByIdForUpdate(salesOrderId);
        if (lockedSo == null) return;

        List<InvSalesDetail> sdList = salesDetailMapper.selectInvSalesDetailByOrderId(salesOrderId);
        // 销售单无明细，回退为 submitted
        if (sdList.isEmpty())
        {
            InvSalesOrder soUpdate = new InvSalesOrder();
            soUpdate.setOrderId(salesOrderId);
            soUpdate.setStatus(InvStatusConstants.SUBMITTED);
            soUpdate.setUpdateBy(SecurityUtils.getUsername());
            salesOrderMapper.updateInvSalesOrder(soUpdate);
            return;
        }
        // 所有明细已出库 → delivered
        boolean allDelivered = sdList.stream().allMatch(sd ->
        {
            BigDecimal delivered = sd.getDeliveredQuantity() != null ? sd.getDeliveredQuantity() : BigDecimal.ZERO;
            return delivered.compareTo(sd.getQuantity()) >= 0;
        });
        if (allDelivered)
        {
            InvSalesOrder soUpdate = new InvSalesOrder();
            soUpdate.setOrderId(salesOrderId);
            soUpdate.setStatus(InvStatusConstants.DELIVERED);
            soUpdate.setUpdateBy(SecurityUtils.getUsername());
            salesOrderMapper.updateInvSalesOrder(soUpdate);
            return;
        }
        // 仍有未取消的发货通知 → noticed
        List<InvDeliveryNotice> activeNotices = noticeMapper.selectInvDeliveryNoticeBySalesOrderId(salesOrderId);
        boolean hasActiveNotice = activeNotices.stream()
                .anyMatch(n -> !InvStatusConstants.CANCELLED.equals(n.getStatus()));
        String newStatus = hasActiveNotice ? InvStatusConstants.NOTICED : InvStatusConstants.SUBMITTED;
        InvSalesOrder soUpdate = new InvSalesOrder();
        soUpdate.setOrderId(salesOrderId);
        soUpdate.setStatus(newStatus);
        soUpdate.setUpdateBy(SecurityUtils.getUsername());
        salesOrderMapper.updateInvSalesOrder(soUpdate);
    }

    private InvDeliveryNotice assertAndGetScopedNotice(Long noticeId, Long selectedShopDeptId)
    {
        InvDeliveryNotice db = getExistingNotice(noticeId);
        assertCanAccessNoticeFromSelectedContext(db, selectedShopDeptId);
        return db;
    }

    private InvDeliveryNotice getExistingNotice(Long noticeId)
    {
        InvDeliveryNotice db = noticeMapper.selectInvDeliveryNoticeById(noticeId);
        if (db == null)
        {
            throw new ServiceException("发货通知不存在");
        }
        return db;
    }

    private void assertCanAccessNoticeFromSelectedContext(InvDeliveryNotice notice, Long selectedShopDeptId)
    {
        Long selectedDeptId = requireSelectedShopDept(selectedShopDeptId);
        if (!SecurityUtils.isAdmin())
        {
            assertUserShopScope(selectedDeptId);
        }
        if (isDeptVisibleFromScope(selectedDeptId, notice.getShopDeptId())
                || isDeptVisibleFromScope(selectedDeptId, notice.getWarehouseId())
                || hasNoticeWarehouseInScope(notice, selectedDeptId))
        {
            return;
        }
        throw new ServiceException("无权访问该店铺发货通知");
    }

    private boolean hasNoticeWarehouseInScope(InvDeliveryNotice notice,
            Long selectedDeptId)
    {
        if (!isWarehouseDept(selectedDeptId))
        {
            return false;
        }
        List<InvDeliveryNoticeDetail> noticeDetails =
                noticeDetailMapper.selectInvDeliveryNoticeDetailByNoticeId(notice.getNoticeId());
        if (noticeDetails == null || noticeDetails.isEmpty())
        {
            return false;
        }
        for (InvDeliveryNoticeDetail detail : noticeDetails)
        {
            if (isDeptVisibleFromScope(selectedDeptId,
                    detail.getWarehouseId()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isDeptVisibleFromScope(Long scopeDeptId, Long targetDeptId)
    {
        return scopeDeptId != null && targetDeptId != null
                && deptScopeMapper.countDeptInScope(scopeDeptId, targetDeptId) > 0;
    }

    private void assertNoticeDetailWarehouse(
            InvDeliveryNoticeDetail noticeDetail, Long warehouseId)
    {
        Long expectedWarehouseId = noticeDetail.getWarehouseId();
        if (expectedWarehouseId == null || expectedWarehouseId == 0)
        {
            throw new ServiceException("发货通知明细商品 ["
                    + noticeDetail.getProductName() + "] 缺少冻结仓库");
        }
        if (!expectedWarehouseId.equals(warehouseId))
        {
            throw new ServiceException("发货通知明细商品 [" + noticeDetail.getProductName()
                    + "] 不属于当前发货仓库");
        }
    }

    private String resolveNoticeCustomerName(InvSalesOrder salesOrder)
    {
        if (salesOrder.getCustomerName() != null && !salesOrder.getCustomerName().isEmpty())
        {
            return salesOrder.getCustomerName();
        }
        return salesOrder.getTargetDeptName();
    }

    private boolean shouldCreateCrossStoreTransfer(InvDeliveryNotice notice, InvSalesOrder salesOrder)
    {
        Long targetDeptId = salesOrder == null ? null : salesOrder.getTargetDeptId();
        return targetDeptId != null && !targetDeptId.equals(notice.getShopDeptId());
    }

    String createCrossStoreTransferForReceipt(InvDeliveryNotice notice, InvSalesOrder salesOrder,
            List<InvDeliveryNoticeDetail> deliveredDetails, Map<Long, InvSalesDetail> salesDetailMap,
            Long selectedShopDeptId)
    {
        if (deliveredDetails == null || deliveredDetails.isEmpty())
        {
            return "";
        }

        Map<Long, List<InvDeliveryNoticeDetail>> byWarehouse = new LinkedHashMap<>();
        for (InvDeliveryNoticeDetail detail : deliveredDetails)
        {
            BigDecimal deliveredQty = detail.getDeliveredQty() != null ? detail.getDeliveredQty() : BigDecimal.ZERO;
            if (deliveredQty.compareTo(BigDecimal.ZERO) <= 0)
            {
                continue;
            }
            if (detail.getWarehouseId() == null || detail.getWarehouseId() == 0)
            {
                throw new ServiceException("通知明细[" + detail.getDetailId()
                        + "]缺少冻结仓库");
            }
            if (detail.getDeliveredCostAmount() == null)
            {
                throw new ServiceException("通知明细[" + detail.getDetailId()
                        + "]成本未知，无法创建调拨");
            }
            if (detail.getDeliveredCostAmount().compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("通知明细[" + detail.getDetailId()
                        + "]冻结成本非法");
            }
            byWarehouse.computeIfAbsent(detail.getWarehouseId(), k -> new java.util.ArrayList<>()).add(detail);
        }

        List<String> transferOrderNos = new ArrayList<>();
        for (Map.Entry<Long, List<InvDeliveryNoticeDetail>> entry : byWarehouse.entrySet())
        {
            Long fromWarehouseId = entry.getKey();
            List<InvDeliveryNoticeDetail> groupDetails = entry.getValue();
            if (groupDetails.isEmpty())
            {
                continue;
            }

            InvTransferOrder order = new InvTransferOrder();
            order.setPurchaseId(null); // 按要求 purchase_id=NULL
            order.setFromDeptId(notice.getShopDeptId());
            order.setFromWarehouseId(fromWarehouseId);
            order.setToDeptId(salesOrder.getTargetDeptId());
            order.setToDeptName(salesOrder.getTargetDeptName());
            order.setToWarehouseId(salesOrder.getTargetDeptId());
            order.setTransferType(InvTransferTypes.CROSS_STORE);
            order.setSourceBusinessType(InvTransferTypes.SOURCE_SALES_DELIVERY_NOTICE);
            order.setSourceBusinessId(notice.getNoticeId());
            order.setRemark("销售发货通知 " + notice.getNoticeNo() + " / 销售单 "
                    + notice.getSalesOrderNo() + " 跨店发货生成-源仓" + fromWarehouseId);

            List<InvTransferDetail> transferDetails = new ArrayList<>();
            for (InvDeliveryNoticeDetail detail : groupDetails)
            {
                InvSalesDetail salesDetail = salesDetailMap.get(detail.getSalesDetailId());
                InvTransferDetail transferDetail = new InvTransferDetail();
                String itemType = InvItemTypes.normalize(detail.getItemType());
                Long itemId = InvItemTypes.resolveItemId(itemType,
                        detail.getItemId(), detail.getProductId());
                transferDetail.setItemType(itemType);
                transferDetail.setItemId(itemId);
                transferDetail.setItemName(detail.getItemName());
                transferDetail.setItemCode(detail.getItemCode());
                transferDetail.setProductId(detail.getProductId());
                transferDetail.setProductName(transferDetail.getItemName() != null ? transferDetail.getItemName() : detail.getProductName());
                transferDetail.setProductCode(transferDetail.getItemCode() != null ? transferDetail.getItemCode()
                        : (salesDetail != null && salesDetail.getSku() != null ? salesDetail.getSku() : ""));
                transferDetail.setSpec(salesDetail != null && salesDetail.getSpec() != null ? salesDetail.getSpec() : "");
                transferDetail.setUnit(salesDetail != null && salesDetail.getUnit() != null ? salesDetail.getUnit() : "");
                BigDecimal deliveredQty = detail.getDeliveredQty();
                BigDecimal deliveredCostAmount = detail.getDeliveredCostAmount()
                        .setScale(2, RoundingMode.HALF_UP);
                transferDetail.setQuantity(deliveredQty);
                transferDetail.setCostPrice(deliveredCostAmount.divide(
                        deliveredQty, 2, RoundingMode.HALF_UP));
                transferDetail.setAmount(deliveredCostAmount);
                transferDetails.add(transferDetail);
            }
            if (transferDetails.isEmpty())
            {
                continue;
            }

            InvTransferOrder transferOrder = transferService.createDeliveredCrossStoreTransfer(
                    order, transferDetails, selectedShopDeptId);
            if (transferOrder != null && transferOrder.getOrderNo() != null && !transferOrder.getOrderNo().isEmpty())
            {
                transferOrderNos.add(transferOrder.getOrderNo());
            }
        }

        if (transferOrderNos.isEmpty())
        {
            throw new ServiceException("跨店发货调拨单创建失败");
        }
        return String.join("、", transferOrderNos);
    }

    static String buildDeliveryResultMessage(boolean crossStoreTransferPending, String transferOrderNo)
    {
        if (transferOrderNo != null && !transferOrderNo.isEmpty())
        {
            return "发货成功；该销售单为跨店发货，已生成调拨单号：" + transferOrderNo;
        }
        if (crossStoreTransferPending)
        {
            return "发货成功；该销售单为跨店发货，销售单全部发完后将生成调拨单。";
        }
        return "发货成功";
    }

    static Long resolveDeliveryWarehouseId(InvDeliveryNotice notice, InvDeliverRequest request)
    {
        Long requestWarehouseId = request == null ? null : request.getWarehouseId();
        Long warehouseId = requestWarehouseId != null ? requestWarehouseId : notice.getWarehouseId();
        if (warehouseId == null || warehouseId == 0)
        {
            throw new ServiceException("发货仓库不能为空");
        }
        return warehouseId;
    }

    static Long resolveNoticeWarehouseId(List<InvSalesDetail> pendingDetails)
    {
        if (pendingDetails == null || pendingDetails.isEmpty())
        {
            return null;
        }
        Long warehouseId = null;
        for (InvSalesDetail detail : pendingDetails)
        {
            Long detailWarehouseId = detail.getWarehouseId();
            if (detailWarehouseId == null || detailWarehouseId == 0)
            {
                return null;
            }
            if (warehouseId == null)
            {
                warehouseId = detailWarehouseId;
                continue;
            }
            if (!warehouseId.equals(detailWarehouseId))
            {
                return null;
            }
        }
        return warehouseId;
    }

    static boolean hasActiveNotice(List<InvDeliveryNotice> notices)
    {
        if (notices == null || notices.isEmpty())
        {
            return false;
        }
        return notices.stream().anyMatch(n ->
                InvStatusConstants.PENDING.equals(n.getStatus())
                        || InvStatusConstants.DELIVERING.equals(n.getStatus()));
    }

    private String generateOrderNo(String prefix)
    {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        numberSequenceMapper.insertOrUpdateSequence(prefix, dateStr, 0, prefix);
        numberSequenceMapper.incrementAndGetSequence(prefix, dateStr);
        Long seq = numberSequenceMapper.selectLastInsertId();
        return prefix + dateStr + String.format("%04d", seq);
    }
}
