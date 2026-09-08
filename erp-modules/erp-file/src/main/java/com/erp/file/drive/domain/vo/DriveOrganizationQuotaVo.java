package com.erp.file.drive.domain.vo;

/** 额度管理中心使用的实时组织和组织盘合并视图。 */
public record DriveOrganizationQuotaVo(Long deptId, Long parentId, String ancestors,
        String deptName, String deptType, String directoryStatus, String delFlag,
        long activeMemberCount, boolean enabled, long quotaBytes,
        Long treeBudgetBytes, String memberWriteMode, String lifecycleStatus,
        String configSource, int version, Long spaceId, long usedBytes,
        boolean overQuota, long overQuotaBytes, boolean canManage,
        boolean budgetBlocked, DriveOrganizationBudgetViolationVo budgetViolation)
{
}
