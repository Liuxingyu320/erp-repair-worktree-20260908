package com.erp.file.drive.domain.vo;

import java.util.List;

/** 一次组织盘幂等同步的可安全返回摘要。 */
public record DriveOrganizationReconcileVo(int scanned, int succeeded,
        int failed, List<Long> failedDeptIds, int budgetViolationCount,
        int budgetBlockedCount, List<Long> budgetBlockedDeptIds,
        List<DriveOrganizationBudgetViolationVo> budgetViolations)
{
    public DriveOrganizationReconcileVo
    {
        failedDeptIds = failedDeptIds == null ? List.of() : List.copyOf(failedDeptIds);
        budgetBlockedDeptIds = budgetBlockedDeptIds == null
                ? List.of() : List.copyOf(budgetBlockedDeptIds);
        budgetViolations = budgetViolations == null
                ? List.of() : List.copyOf(budgetViolations);
    }

    public DriveOrganizationReconcileVo(int scanned, int succeeded,
            int failed, List<Long> failedDeptIds)
    {
        this(scanned, succeeded, failed, failedDeptIds,
                0, 0, List.of(), List.of());
    }
}
