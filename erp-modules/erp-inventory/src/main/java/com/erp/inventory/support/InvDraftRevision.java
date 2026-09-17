package com.erp.inventory.support;

import com.erp.common.core.exception.ServiceException;

/** Reject stale edits before changing either the header or its details. */
public final class InvDraftRevision
{
    private InvDraftRevision() { }
    public static void requireCurrent(Long expected, Long actual)
    {
        if (expected == null || actual == null || expected < 0 || !expected.equals(actual))
            throw new ServiceException("草稿已被修改或缺少版本，请保留当前输入并重新打开最新草稿核对");
    }
}
