package com.erp.inventory.constant;

import java.util.Set;

/** Stable roles and post codes for the transfer approval chain. */
public final class InvTransferApprovalNodeRoles
{
    public static final String LEVEL4_HIGHEST = "level4_highest";
    public static final String LEVEL3_HIGHEST = "level3_highest";
    public static final String OPERATIONS_DIRECTOR = "operations_director";
    public static final String GENERAL_MANAGER = "general_manager";

    public static final String STORE_MANAGER_POST = "dz";
    public static final String STORE_ASSISTANT_POST = "dzzy";
    public static final String OPERATIONS_DIRECTOR_POST = "yyzj";
    public static final String GENERAL_MANAGER_POST = "zjl";

    private static final Set<String> SYSTEM_ROLES = Set.of(
            LEVEL4_HIGHEST,
            LEVEL3_HIGHEST,
            OPERATIONS_DIRECTOR,
            GENERAL_MANAGER);

    private InvTransferApprovalNodeRoles()
    {
    }

    public static boolean isSystemRole(String role)
    {
        return SYSTEM_ROLES.contains(role);
    }
}
