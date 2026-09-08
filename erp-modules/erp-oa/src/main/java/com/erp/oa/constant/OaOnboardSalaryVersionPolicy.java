package com.erp.oa.constant;

import java.util.Locale;
import com.erp.common.core.utils.StringUtils;

/** 入职劳动合同社保口径与薪酬确认书版本的唯一映射。 */
public final class OaOnboardSalaryVersionPolicy
{
    public static final String SOCIAL_INSURED = "SOCIAL_INSURED";
    public static final String SOCIAL_UNINSURED = "SOCIAL_UNINSURED";
    public static final String VERSION_A = "A";
    public static final String VERSION_B = "B";

    private OaOnboardSalaryVersionPolicy()
    {
    }

    public static String normalizeSocialType(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        String normalized = value.trim();
        if ("有社保".equals(normalized) || SOCIAL_INSURED.equalsIgnoreCase(normalized))
        {
            return SOCIAL_INSURED;
        }
        if ("无社保".equals(normalized) || SOCIAL_UNINSURED.equalsIgnoreCase(normalized))
        {
            return SOCIAL_UNINSURED;
        }
        return null;
    }

    public static String normalizeSalaryVersion(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return VERSION_A.equals(normalized) || VERSION_B.equals(normalized)
                ? normalized : null;
    }

    /**
     * Returns the only salary-confirmation version allowed for the normalized social type.
     * Unknown or missing social facts deliberately return {@code null}; callers must fail
     * closed instead of falling back to either document version.
     */
    public static String requiredSalaryVersion(String socialType)
    {
        String normalized = normalizeSocialType(socialType);
        if (SOCIAL_INSURED.equals(normalized))
        {
            return VERSION_B;
        }
        if (SOCIAL_UNINSURED.equals(normalized))
        {
            return VERSION_A;
        }
        return null;
    }

    public static boolean matchesSocialType(String socialType, String salaryVersion)
    {
        String required = requiredSalaryVersion(socialType);
        return required != null && required.equals(normalizeSalaryVersion(salaryVersion));
    }
}
