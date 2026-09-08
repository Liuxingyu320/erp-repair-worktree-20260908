package com.erp.file.drive.constant;

/**
 * 云盘稳定类型、状态与权限常量。
 */
public final class DriveConstants
{
    public static final long ROOT_PARENT_ID = 0L;

    public static final String SPACE_PERSONAL = "PERSONAL";
    public static final String SPACE_COMPANY = "COMPANY";
    public static final String SPACE_DEPARTMENT = "DEPARTMENT";
    public static final String COMPANY_SPACE_KEY = "COMPANY:ROOT";

    public static final String NODE_FILE = "FILE";
    public static final String NODE_FOLDER = "FOLDER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_READ_ONLY = "READ_ONLY";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_TRASHED = "TRASHED";
    public static final String STATUS_PURGING = "PURGING";
    public static final String STATUS_PURGE_FAILED = "PURGE_FAILED";

    public static final String PERMISSION_ACCESS = "drive:access";
    public static final String PERMISSION_COMPANY_MANAGE = "drive:company:manage";
    public static final String PERMISSION_DEPARTMENT_MANAGE = "drive:department:manage";
    public static final String PERMISSION_QUOTA_MANAGE = "drive:quota:manage";

    public static final String QUOTA_SUBJECT_GLOBAL = "GLOBAL";
    public static final String QUOTA_SUBJECT_POST = "POST";
    public static final String QUOTA_SUBJECT_USER = "USER";

    public static final String QUOTA_SOURCE_LEGACY = "LEGACY";
    public static final String QUOTA_SOURCE_GLOBAL = "GLOBAL";
    public static final String QUOTA_SOURCE_POST = "POST";
    public static final String QUOTA_SOURCE_USER = "USER";
    public static final String QUOTA_SOURCE_ORG_TYPE = "ORG_TYPE";
    public static final String QUOTA_SOURCE_ORG_OVERRIDE = "ORG_OVERRIDE";
    public static final String QUOTA_SOURCE_COMPANY = "COMPANY";

    public static final String ORG_WRITE_PERMISSION_ONLY = "PERMISSION_ONLY";
    public static final String ORG_WRITE_ALL_DIRECT_MEMBERS = "ALL_DIRECT_MEMBERS";
    public static final String ORG_WRITE_READ_ONLY = "READ_ONLY";

    public static final String CONFIG_SOURCE_TYPE_DEFAULT = "TYPE_DEFAULT";
    public static final String CONFIG_SOURCE_MANUAL = "MANUAL";
    public static final String CONFIG_SOURCE_MIGRATED = "MIGRATED";

    public static final String CAPACITY_WARN = "WARN";
    public static final String CAPACITY_BLOCK = "BLOCK";

    public static final String RESERVATION_RESERVED = "RESERVED";
    public static final String RESERVATION_CLEANING = "CLEANING";
    public static final String RESERVATION_CLEANUP_FAILED = "CLEANUP_FAILED";

    public static final String IMPACT_PERSONAL_POLICY = "PERSONAL_POLICY";
    public static final String IMPACT_ORGANIZATION = "ORGANIZATION";
    public static final String IMPACT_ORGANIZATION_BATCH = "ORGANIZATION_BATCH";
    public static final String IMPACT_ORGANIZATION_TYPE_RULE = "ORGANIZATION_TYPE_RULE";
    public static final String IMPACT_CAPACITY = "CAPACITY";

    public static final String ACTION_CREATE_FOLDER = "CREATE_FOLDER";
    public static final String ACTION_UPLOAD = "UPLOAD";
    public static final String ACTION_OPEN = "OPEN";
    public static final String ACTION_PREVIEW = "PREVIEW";
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    public static final String ACTION_RENAME = "RENAME";
    public static final String ACTION_MOVE = "MOVE";
    public static final String ACTION_TRASH = "TRASH";
    public static final String ACTION_RESTORE = "RESTORE";
    public static final String ACTION_PURGE = "PURGE";
    public static final String ACTION_CLEANUP = "CLEANUP";
    public static final String ACTION_QUOTA_POLICY_UPDATE = "QUOTA_POLICY_UPDATE";
    public static final String ACTION_QUOTA_POLICY_DELETE = "QUOTA_POLICY_DELETE";
    public static final String ACTION_ORG_CONFIG_UPDATE = "ORG_CONFIG_UPDATE";
    public static final String ACTION_ORG_CONFIG_BATCH = "ORG_CONFIG_BATCH";
    public static final String ACTION_ORG_TYPE_RULE_UPDATE = "ORG_TYPE_RULE_UPDATE";
    public static final String ACTION_ORG_RECONCILE = "ORG_RECONCILE";
    public static final String ACTION_CAPACITY_UPDATE = "CAPACITY_UPDATE";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";

    private DriveConstants()
    {
    }
}
