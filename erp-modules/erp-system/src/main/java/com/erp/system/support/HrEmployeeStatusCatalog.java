package com.erp.system.support;

import java.util.List;
import com.erp.common.core.exception.ServiceException;

public final class HrEmployeeStatusCatalog
{
    private static final List<String> VALUES = List.of("待入职", "试用", "正式", "待离职", "离职", "停薪留职");

    private HrEmployeeStatusCatalog() { }

    public static List<String> values() { return VALUES; }

    public static String normalizeForRead(String value)
    {
        String normalized = trim(value);
        return "在职".equals(normalized) ? "正式" : normalized;
    }

    public static String normalizeForWrite(String value)
    {
        String normalized = normalizeForRead(value);
        if (normalized == null || VALUES.contains(normalized)) return normalized;
        throw new ServiceException("员工状态不受支持: " + normalized);
    }

    private static String trim(String value)
    {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
