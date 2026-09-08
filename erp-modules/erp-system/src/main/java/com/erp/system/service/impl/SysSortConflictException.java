package com.erp.system.service.impl;

import java.util.Collections;
import java.util.List;

/** Raised when another administrator changed an order value after the current list was loaded. */
public class SysSortConflictException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final List<Long> conflictIds;

    public SysSortConflictException(List<Long> conflictIds)
    {
        super("排序已被其他管理员修改，请保留本地调整并刷新基线后重试");
        this.conflictIds = conflictIds == null ? Collections.emptyList() : List.copyOf(conflictIds);
    }

    public List<Long> getConflictIds()
    {
        return conflictIds;
    }
}
