package com.erp.system.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.domain.dto.SysSortChangeItem;
import com.erp.system.domain.dto.SysSortChangeRequest;

/** Defense-in-depth validation and stable lock ordering for optimistic sort requests. */
final class SysSortChangeSupport
{
    private SysSortChangeSupport()
    {
    }

    static List<SysSortChangeItem> normalize(SysSortChangeRequest request)
    {
        if (request == null || request.getChanges() == null || request.getChanges().isEmpty())
        {
            throw new ServiceException("排序变更不能为空");
        }
        if (request.getChanges().size() > SysSortChangeRequest.MAX_CHANGES)
        {
            throw new ServiceException("单次排序最多修改500项");
        }
        List<SysSortChangeItem> changes = new ArrayList<SysSortChangeItem>(request.getChanges());
        Set<Long> ids = new HashSet<Long>();
        for (SysSortChangeItem change : changes)
        {
            if (change == null || change.getId() == null || change.getId() <= 0)
            {
                throw new ServiceException("排序对象ID必须为正整数");
            }
            validateOrderNum(change.getExpectedOrderNum(), "原排序值");
            validateOrderNum(change.getNewOrderNum(), "新排序值");
            if (!ids.add(change.getId()))
            {
                throw new ServiceException("排序对象ID不能重复");
            }
            if (change.getExpectedOrderNum().equals(change.getNewOrderNum()))
            {
                throw new ServiceException("排序变更包含未修改项");
            }
        }
        changes.sort(Comparator.comparing(SysSortChangeItem::getId));
        return changes;
    }

    private static void validateOrderNum(Integer value, String label)
    {
        if (value == null || value < 0 || value > SysSortChangeItem.MAX_ORDER_NUM)
        {
            throw new ServiceException(label + "必须在0到999999之间");
        }
    }
}
