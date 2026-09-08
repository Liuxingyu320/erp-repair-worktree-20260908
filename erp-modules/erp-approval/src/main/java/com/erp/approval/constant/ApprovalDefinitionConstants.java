package com.erp.approval.constant;

/** Closed vocabularies used by approval definitions. */
public final class ApprovalDefinitionConstants
{
    public static final String ENGINE_NATIVE = "NATIVE";
    public static final String ENGINE_LEGACY = "LEGACY";

    public static final String DEFINITION_FIXED = "FIXED";
    public static final String DEFINITION_LIMITED = "LIMITED";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String VERSION_DRAFT = "DRAFT";
    public static final String VERSION_PUBLISHED = "PUBLISHED";
    public static final String VERSION_RETIRED = "RETIRED";

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_AREA = "AREA";
    public static final String SCOPE_STORE = "STORE";

    public static final String STRATEGY_BUSINESS = "BUSINESS_STRATEGY";
    public static final String STRATEGY_RESPONSIBILITY_POST =
            "RESPONSIBILITY_POST";
    public static final String STRATEGY_ORG_LEADER = "ORG_LEADER";
    public static final String STRATEGY_FIXED_USERS = "FIXED_USERS";

    public static final String APPROVAL_UNIQUE_BEST = "UNIQUE_BEST";
    public static final String APPROVAL_ANY_ONE = "ANY_ONE";
    public static final String APPROVAL_ALL = "ALL";

    public static final String MISSING_BLOCK = "BLOCK";
    public static final String MISSING_SKIP_WARN = "SKIP_WARN";

    public static final String SELF_BLOCK = "BLOCK";
    public static final String SELF_SKIP = "SKIP";
    public static final String SELF_ALLOW = "ALLOW";
    public static final String SELF_SKIP_THROUGH = "SKIP_SELF_AND_LOWER";
    public static final String SELF_SKIP_THROUGH_LEGACY = "SKIP_THROUGH";

    public static final String TRANSFER_LEVEL4 = "TRANSFER_LEVEL4_MANAGER";
    public static final String TRANSFER_LEVEL3 = "TRANSFER_LEVEL3_MANAGER";
    public static final String TRANSFER_OPERATIONS_DIRECTOR =
            "TRANSFER_OPERATIONS_DIRECTOR";
    public static final String TRANSFER_GENERAL_MANAGER =
            "TRANSFER_GENERAL_MANAGER";
    public static final String PERMISSION_HOLDER = "PERMISSION_HOLDER";

    public static final String STORE_MANAGER_POST = "dz";
    public static final String STORE_ASSISTANT_POST = "dzzy";
    public static final String OPERATIONS_DIRECTOR_POST = "yyzj";
    public static final String GENERAL_MANAGER_POST = "zjl";

    private ApprovalDefinitionConstants()
    {
    }
}
