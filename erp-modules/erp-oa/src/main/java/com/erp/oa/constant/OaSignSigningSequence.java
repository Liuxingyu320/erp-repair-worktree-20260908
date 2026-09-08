package com.erp.oa.constant;

import java.util.Locale;
import com.erp.common.core.exception.ServiceException;

/**
 * Frozen order in which a package obtains the company snapshot and the employee signature.
 */
public final class OaSignSigningSequence
{
    public static final String COMPANY_FIRST = "COMPANY_FIRST";
    public static final String SIGNATURE_FIRST = "SIGNATURE_FIRST";

    private OaSignSigningSequence() {}

    public static String normalize(String value)
    {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isEmpty())
        {
            return COMPANY_FIRST;
        }
        if (COMPANY_FIRST.equals(normalized) || SIGNATURE_FIRST.equals(normalized))
        {
            return normalized;
        }
        throw new ServiceException("签署顺序无效");
    }

    public static boolean signatureFirst(String value)
    {
        return SIGNATURE_FIRST.equals(normalize(value));
    }
}
