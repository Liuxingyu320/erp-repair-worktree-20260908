package com.erp.oa.constant;

import java.util.Objects;

/**
 * Scope semantics for HR-maintained signing plans.
 *
 * <p>Signing plans are a single HR-owned catalog. The database keeps the
 * non-null {@code shop_dept_id} snapshot used by published versions, so the
 * reserved value {@code 0} represents that global catalog. Operational data
 * such as tasks and packages must continue to use a real organization ID.</p>
 */
public final class OaSignPlanScope
{
    public static final Long GLOBAL_SHOP_DEPT_ID = 0L;

    private OaSignPlanScope()
    {
    }

    public static boolean isGlobal(Long shopDeptId)
    {
        return Objects.equals(GLOBAL_SHOP_DEPT_ID, shopDeptId);
    }

    /**
     * Global versions apply everywhere; exact legacy versions remain usable
     * for already-bound historical tasks.
     */
    public static boolean appliesTo(Long planShopDeptId, Long operationalShopDeptId)
    {
        if (operationalShopDeptId == null || operationalShopDeptId <= 0L)
        {
            return false;
        }
        return isGlobal(planShopDeptId) || Objects.equals(planShopDeptId, operationalShopDeptId);
    }
}
