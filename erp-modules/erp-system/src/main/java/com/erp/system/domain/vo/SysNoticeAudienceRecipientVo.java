package com.erp.system.domain.vo;

/**
 * 受众解析后的最小用户行，仅用于去重计数和组织分布。
 */
public class SysNoticeAudienceRecipientVo
{
    private Long userId;
    private Long deptId;
    private String deptName;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
