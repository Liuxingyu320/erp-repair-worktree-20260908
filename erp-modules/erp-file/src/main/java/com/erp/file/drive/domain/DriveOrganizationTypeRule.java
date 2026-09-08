package com.erp.file.drive.domain;

import com.erp.common.core.web.domain.BaseEntity;

/**
 * 按 sys_dept.dept_type 定义的新组织建盘默认值。
 */
public class DriveOrganizationTypeRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String deptType;
    private Boolean autoEnable;
    private Boolean requireActiveMember;
    private Long defaultQuotaBytes;
    private String status;
    private Integer version;

    public String getDeptType() { return deptType; }
    public void setDeptType(String deptType) { this.deptType = deptType; }
    public Boolean getAutoEnable() { return autoEnable; }
    public void setAutoEnable(Boolean autoEnable) { this.autoEnable = autoEnable; }
    public Boolean getRequireActiveMember() { return requireActiveMember; }
    public void setRequireActiveMember(Boolean requireActiveMember) { this.requireActiveMember = requireActiveMember; }
    public Long getDefaultQuotaBytes() { return defaultQuotaBytes; }
    public void setDefaultQuotaBytes(Long defaultQuotaBytes) { this.defaultQuotaBytes = defaultQuotaBytes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

