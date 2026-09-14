package com.erp.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvStock;

/** Allocates a deduction from the inventory row already held FOR UPDATE. */
final class InvStockCostAllocator
{
    private InvStockCostAllocator() { }

    static Allocation allocate(InvStock stock, BigDecimal quantity)
    {
        if (stock == null || stock.getCurrentQuantity() == null
                || stock.getCurrentQuantity().signum() <= 0 || quantity == null
                || quantity.signum() <= 0 || quantity.compareTo(stock.getCurrentQuantity()) > 0)
        {
            throw new ServiceException("库存扣减数量非法，请核对库存");
        }
        BigDecimal unitCost = knownMoney(stock.getCostPrice());
        BigDecimal remainingCost = knownMoney(stock.getTotalCost());
        // Exhausting available quantity alone must not consume cost belonging to locked stock.
        BigDecimal amount = quantity.compareTo(stock.getCurrentQuantity()) == 0
                ? remainingCost : quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(remainingCost) > 0)
        {
            throw new ServiceException("库存剩余成本不足，无法出库，请核对成本数据");
        }
        return new Allocation(unitCost, amount);
    }

    private static BigDecimal knownMoney(BigDecimal value)
    {
        if (value == null)
        {
            throw new ServiceException("库存成本未知，无法出库");
        }
        if (value.signum() < 0)
        {
            throw new ServiceException("库存成本非法，无法出库");
        }
        try
        {
            // Persisted inventory and movement money use two decimals; never silently lose a tail.
            return value.setScale(2, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException invalidPrecision)
        {
            throw new ServiceException("库存成本精度非法，无法出库");
        }
    }

    record Allocation(BigDecimal unitCost, BigDecimal amount) { }
}
