package com.erp.system.domain.vo;

/** Stable non-PII detail contract used by the account-management dialog. */
public class SysUserManageDetailVo extends SysUserListVo
{
    private String remark;
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

