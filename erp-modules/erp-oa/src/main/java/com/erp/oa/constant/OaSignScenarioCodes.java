package com.erp.oa.constant;

import java.util.Locale;
import java.util.Set;

/** Canonical scenario codes shared by task orchestration and legacy package configuration. */
public final class OaSignScenarioCodes
{
    public static final String ONBOARD = "ONBOARD";
    public static final String RENEWAL = "RENEWAL";
    public static final String TRANSFER = "TRANSFER";
    public static final String REGULARIZE = "REGULARIZE";
    public static final String OFFBOARD = "OFFBOARD";

    private static final Set<String> SUPPORTED = Set.of(
            ONBOARD, RENEWAL, TRANSFER, REGULARIZE, OFFBOARD);

    private OaSignScenarioCodes()
    {
    }

    public static String normalizeTaskScenario(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        return "RENEW".equals(normalized) ? RENEWAL : normalized;
    }

    public static String normalizePackageScenario(String value)
    {
        String normalized = normalizeTaskScenario(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public static boolean isRenewal(String value)
    {
        return RENEWAL.equals(normalizeTaskScenario(value));
    }

    public static boolean isSupported(String value)
    {
        String normalized = normalizeTaskScenario(value);
        return normalized != null && SUPPORTED.contains(normalized);
    }
}
