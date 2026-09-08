package com.erp.system.domain.vo;

/**
 * Aggregated account-setup health for the current administrator data scope.
 */
public class SysUserSetupSummaryVo
{
    private long totalCount;
    private long activeCount;
    private long disabledCount;
    private long missingRoleCount;
    private long missingShopScopeCount;
    private long completeCount;

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getActiveCount() { return activeCount; }
    public void setActiveCount(long activeCount) { this.activeCount = activeCount; }
    public long getDisabledCount() { return disabledCount; }
    public void setDisabledCount(long disabledCount) { this.disabledCount = disabledCount; }
    public long getMissingRoleCount() { return missingRoleCount; }
    public void setMissingRoleCount(long missingRoleCount) { this.missingRoleCount = missingRoleCount; }
    public long getMissingShopScopeCount() { return missingShopScopeCount; }
    public void setMissingShopScopeCount(long missingShopScopeCount) { this.missingShopScopeCount = missingShopScopeCount; }
    public long getCompleteCount() { return completeCount; }
    public void setCompleteCount(long completeCount) { this.completeCount = completeCount; }
}
