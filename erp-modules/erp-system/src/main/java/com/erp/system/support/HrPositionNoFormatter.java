package com.erp.system.support;

import java.util.Locale;
import java.util.regex.Pattern;
import com.erp.common.core.exception.ServiceException;

/** Builds the mutable position number from a stable employee number and post code. */
public final class HrPositionNoFormatter
{
    private static final Pattern POST_CODE = Pattern.compile("[A-Za-z0-9]{2,16}");
    private static final Pattern EMPLOYEE_NO = Pattern.compile("[A-Za-z][A-Za-z0-9-]{1,63}");

    private HrPositionNoFormatter()
    {
    }

    public static String format(String employeeNo, String postCode)
    {
        String normalizedEmployeeNo = normalize(employeeNo);
        String normalizedPostCode = normalize(postCode);
        if (normalizedEmployeeNo == null || !EMPLOYEE_NO.matcher(normalizedEmployeeNo).matches())
        {
            throw new ServiceException("员工号格式不正确，无法生成岗位工号");
        }
        if (normalizedPostCode == null || !POST_CODE.matcher(normalizedPostCode).matches())
        {
            throw new ServiceException("岗位编码必须为2至16位字母或数字，无法生成岗位工号");
        }
        return normalizedPostCode + "-" + normalizedEmployeeNo;
    }

    private static String normalize(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
