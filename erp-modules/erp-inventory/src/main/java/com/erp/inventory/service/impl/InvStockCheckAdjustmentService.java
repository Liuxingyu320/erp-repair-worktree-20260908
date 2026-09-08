package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.inventory.constant.InvStatusConstants;
import com.erp.inventory.domain.InvStock;
import com.erp.inventory.domain.InvStockCheck;
import com.erp.inventory.domain.InvStockCheckDetail;
import com.erp.inventory.domain.InvStockLog;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentResult;
import com.erp.inventory.domain.vo.InvStockCheckAdjustmentEntry;
import com.erp.inventory.domain.vo.InvStockCheckSnapshotChange;
import com.erp.inventory.mapper.InvStockLogMapper;
import com.erp.inventory.mapper.InvStockMapper;

@Service
public class InvStockCheckAdjustmentService
{
    @Autowired
    private InvStockMapper stockMapper;

    @Autowired
    private InvStockLogMapper stockLogMapper;

    public InvStockCheckAdjustmentResult evaluate(InvStockCheck check,
            List<InvStockCheckDetail> details, boolean apply)
    {
        return evaluate(check, details, apply, SecurityUtils.getUsername());
    }

    public InvStockCheckAdjustmentResult evaluate(InvStockCheck check,
            List<InvStockCheckDetail> details, boolean apply,
            String operatorName)
    {
        InvStockCheckAdjustmentResult result = new InvStockCheckAdjustmentResult();
        if (details == null)
        {
            return result;
        }
        Map<Long, InvStock> lockedStocks = new LinkedHashMap<>();
        for (InvStockCheckDetail detail : details)
        {
            InvStock stock = stockMapper.selectInvStockByProductShopWarehouseForUpdate(
                    detail.getProductId(), check.getShopDeptId(), check.getWarehouseId());
            lockedStocks.put(detail.getDetailId(), stock);
            BigDecimal currentQuantity = stock == null || stock.getCurrentQuantity() == null
                    ? BigDecimal.ZERO : stock.getCurrentQuantity();
            BigDecimal bookQuantity = detail.getBookQty() == null ? BigDecimal.ZERO : detail.getBookQty();
            if (currentQuantity.compareTo(bookQuantity) != 0)
            {
                InvStockCheckSnapshotChange change = new InvStockCheckSnapshotChange();
                change.setDetailId(detail.getDetailId());
                change.setProductId(detail.getProductId());
                change.setProductName(detail.getProductName());
                change.setBookQuantity(bookQuantity);
                change.setCurrentQuantity(currentQuantity);
                result.getSnapshotChanges().add(change);
            }
        }
        if (result.hasSnapshotChanges() || !apply)
        {
            return result;
        }
        for (InvStockCheckDetail detail : details)
        {
            InvStockCheckAdjustmentEntry adjustment =
                    applyDifference(check, detail,
                            lockedStocks.get(detail.getDetailId()),
                            operatorName);
            if (adjustment != null)
            {
                result.getAdjustments().add(adjustment);
            }
        }
        return result;
    }

    private InvStockCheckAdjustmentEntry applyDifference(InvStockCheck check,
            InvStockCheckDetail detail, InvStock stock, String operatorName)
    {
        BigDecimal diffQuantity = detail.getDiffQty() == null ? BigDecimal.ZERO : detail.getDiffQty();
        if (diffQuantity.compareTo(BigDecimal.ZERO) == 0)
        {
            return null;
        }
        if (stock == null)
        {
            throw new ServiceException("商品 [" + detail.getProductName() + "] 库存记录不存在，请重新盘点");
        }
        BigDecimal beforeQuantity = safe(stock.getCurrentQuantity());
        BigDecimal costPrice = stock.getCostPrice() == null
                ? safe(detail.getCostPrice()) : stock.getCostPrice();
        int rows;
        String movementType;
        String remark;
        if (diffQuantity.compareTo(BigDecimal.ZERO) > 0)
        {
            rows = stockMapper.addInvStockWithCost(stock.getStockId(), stock.getVersion(), diffQuantity,
                    diffQuantity.multiply(costPrice), operatorName);
            movementType = InvStatusConstants.MOVEMENT_STOCK_CHECK_PROFIT;
            remark = "盘点审批-盘盈";
        }
        else
        {
            BigDecimal deduction = diffQuantity.abs();
            if (safe(stock.getAvailableQuantity()).compareTo(deduction) < 0)
            {
                throw new ServiceException("商品 [" + detail.getProductName() + "] 可用库存不足，无法盘亏");
            }
            rows = stockMapper.deductInvStockWithCost(stock.getStockId(), stock.getVersion(), deduction,
                    deduction.multiply(costPrice), operatorName);
            movementType = InvStatusConstants.MOVEMENT_STOCK_CHECK_LOSS;
            remark = "盘点审批-盘亏";
        }
        if (rows == 0)
        {
            throw new ServiceException("商品 [" + detail.getProductName() + "] 库存已发生变化，请重新盘点");
        }
        InvStock updated = stockMapper.selectInvStockById(stock.getStockId());
        InvStockLog log = new InvStockLog();
        log.setProductId(detail.getProductId());
        log.setShopDeptId(check.getShopDeptId());
        log.setWarehouseId(check.getWarehouseId());
        log.setMovementType(movementType);
        log.setBusinessType("stock_check");
        log.setBusinessId(check.getCheckId());
        log.setBusinessNo(check.getCheckNo());
        log.setChangeQuantity(diffQuantity);
        log.setBeforeQuantity(beforeQuantity);
        log.setAfterQuantity(updated.getCurrentQuantity());
        log.setCostPrice(updated.getCostPrice());
        log.setCreateBy(operatorName);
        log.setCreateTime(new Date());
        log.setRemark(remark);
        stockLogMapper.insertInvStockLog(log);

        InvStockCheckAdjustmentEntry adjustment = new InvStockCheckAdjustmentEntry();
        adjustment.setDetailId(detail.getDetailId());
        adjustment.setProductId(detail.getProductId());
        adjustment.setProductName(detail.getProductName());
        adjustment.setBeforeQuantity(beforeQuantity);
        adjustment.setChangeQuantity(diffQuantity);
        adjustment.setAfterQuantity(updated.getCurrentQuantity());
        adjustment.setMovementType(movementType);
        return adjustment;
    }

    private static BigDecimal safe(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }
}
