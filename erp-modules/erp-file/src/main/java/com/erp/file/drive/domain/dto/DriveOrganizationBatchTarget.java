package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.NotNull;

/** 批量修改的组织和当前配置版本。 */
public class DriveOrganizationBatchTarget
{
    @NotNull
    private Long deptId;
    @NotNull
    private Integer version;

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
