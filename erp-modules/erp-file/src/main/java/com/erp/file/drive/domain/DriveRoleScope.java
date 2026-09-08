package com.erp.file.drive.domain;

/**
 * 当前用户角色的数据范围行；自定义范围可能对应多条组织记录。
 */
public class DriveRoleScope
{
    private Long roleId;
    private String dataScope;
    private Long deptId;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
}

