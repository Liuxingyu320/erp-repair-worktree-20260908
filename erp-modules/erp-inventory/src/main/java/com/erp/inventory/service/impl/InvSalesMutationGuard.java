package com.erp.inventory.service.impl;

import com.erp.common.core.exception.ServiceException;
import com.erp.inventory.domain.InvSalesOrder;
import com.erp.inventory.mapper.InvSalesOrderMapper;

/** Mutations hold the original sales row before child documents, details or stock. */
final class InvSalesMutationGuard
{
    private InvSalesMutationGuard() { }

    static long version(InvSalesOrder order)
    {
        return order.getVersion() == null ? 0L : order.getVersion();
    }

    static void requireVersion(Long expected, InvSalesOrder locked)
    {
        if (expected == null || expected.longValue() != version(locked))
        {
            throw new ServiceException("销售单已变化，请刷新后核对；本次输入未保存");
        }
    }

    static void update(InvSalesOrderMapper mapper, InvSalesOrder update, InvSalesOrder locked)
    {
        long currentVersion = version(locked);
        update.setVersion(currentVersion);
        update.getParams().put("expectedStatus", locked.getStatus());
        if (mapper.updateInvSalesOrder(update) != 1)
        {
            throw new ServiceException("销售单状态或版本已变化，请刷新后核对");
        }
        locked.setVersion(currentVersion + 1);
        if (update.getStatus() != null) locked.setStatus(update.getStatus());
    }
}
