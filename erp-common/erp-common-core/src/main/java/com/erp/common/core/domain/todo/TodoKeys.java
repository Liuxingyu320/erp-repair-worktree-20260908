package com.erp.common.core.domain.todo;

/**
 * Builds stable keys for unified todo items.
 */
public final class TodoKeys
{
    private TodoKeys()
    {
    }

    public static String build(String source, String type, Long businessId, String action)
    {
        String normalizedSource = requireSegment("source", source);
        String normalizedType = requireSegment("type", type);
        String normalizedAction = requireSegment("action", action);
        if (businessId == null || businessId <= 0)
        {
            throw new IllegalArgumentException("businessId must be greater than zero");
        }
        return normalizedSource + ":" + normalizedType + ":" + businessId + ":" + normalizedAction;
    }

    private static String requireSegment(String name, String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (normalized.indexOf(':') >= 0)
        {
            throw new IllegalArgumentException(name + " must not contain ':'");
        }
        return normalized;
    }
}
