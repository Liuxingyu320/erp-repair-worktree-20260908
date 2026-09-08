package com.erp.file.drive.domain;

/**
 * 云盘读取的实时组织目录，不复制 sys_dept 的业务所有权。
 */
public class DriveOrganization
{
    private Long deptId;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptType;
    private String status;
    private String delFlag;
    private Long activeMemberCount;

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getAncestors() { return ancestors; }
    public void setAncestors(String ancestors) { this.ancestors = ancestors; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getDeptType() { return deptType; }
    public void setDeptType(String deptType) { this.deptType = deptType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Long getActiveMemberCount() { return activeMemberCount; }
    public void setActiveMemberCount(Long activeMemberCount) { this.activeMemberCount = activeMemberCount; }
}

