package com.erp.common.core.domain.todo;

import java.util.List;

/**
 * Shared values used by unified todo providers.
 */
public final class TodoConstants
{
    public static final String SOURCE_INVENTORY = "inventory";
    public static final String SOURCE_OA = "oa";
    public static final String SOURCE_SYSTEM = "system";

    public static final String CATEGORY_APPROVAL = "approval";
    public static final String CATEGORY_EXECUTION = "execution";
    public static final String CATEGORY_RETURNED = "returned";
    public static final String CATEGORY_RISK = "risk";
    public static final String CATEGORY_PERSONAL = "personal";

    public static final String PRIORITY_URGENT = "urgent";
    public static final String PRIORITY_IMPORTANT = "important";
    public static final String PRIORITY_NORMAL = "normal";

    public static final String SCOPE_ACTIONABLE = "actionable";
    /** @deprecated accepted temporarily for older clients and normalized by providers. */
    @Deprecated
    public static final String SCOPE_CURRENT_ORG = "current_org";
    /** @deprecated accepted temporarily for older clients and normalized by providers. */
    @Deprecated
    public static final String SCOPE_ALL_AUTHORIZED = "all_authorized";

    public static final List<String> CATEGORIES = List.of(
            CATEGORY_APPROVAL,
            CATEGORY_EXECUTION,
            CATEGORY_RETURNED,
            CATEGORY_RISK,
            CATEGORY_PERSONAL);

    public static final List<String> PRIORITIES = List.of(
            PRIORITY_URGENT,
            PRIORITY_IMPORTANT,
            PRIORITY_NORMAL);

    private TodoConstants()
    {
    }
}
