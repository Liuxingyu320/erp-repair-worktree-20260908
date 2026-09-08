package com.erp.file.drive.domain;

import com.erp.common.core.web.domain.BaseEntity;

/**
 * 单个组织的建盘、额度、组织树预算和成员写入配置。
 */
public class DriveOrganizationSpaceConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;
    private Long deptId;
    private Boolean enabled;
    private Long quotaBytes;
    private Long treeBudgetBytes;
    private String memberWriteMode;
    private String lifecycleStatus;
    private String configSource;
    private Integer version;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Long getTreeBudgetBytes() { return treeBudgetBytes; }
    public void setTreeBudgetBytes(Long treeBudgetBytes) { this.treeBudgetBytes = treeBudgetBytes; }
    public String getMemberWriteMode() { return memberWriteMode; }
    public void setMemberWriteMode(String memberWriteMode) { this.memberWriteMode = memberWriteMode; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getConfigSource() { return configSource; }
    public void setConfigSource(String configSource) { this.configSource = configSource; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

