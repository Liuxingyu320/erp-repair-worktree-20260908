package com.erp.file.drive.domain.vo;

import java.util.List;

/** 组织树预算漂移的可安全展示摘要。 */
public record DriveOrganizationBudgetViolationVo(Long budgetDeptId,
        String budgetDeptName, long budgetBytes, long allocatedBytes,
        long exceededBytes, List<Long> affectedDeptIds,
        boolean hierarchyInvalid, String violationMessage)
{
    public DriveOrganizationBudgetViolationVo(Long budgetDeptId,
            String budgetDeptName, long budgetBytes, long allocatedBytes,
            long exceededBytes, List<Long> affectedDeptIds)
    {
        this(budgetDeptId, budgetDeptName, budgetBytes, allocatedBytes,
                exceededBytes, affectedDeptIds, false, null);
    }

    public DriveOrganizationBudgetViolationVo
    {
        affectedDeptIds = affectedDeptIds == null
                ? List.of() : List.copyOf(affectedDeptIds);
    }
}
