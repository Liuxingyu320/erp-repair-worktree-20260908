package com.erp.file.drive.domain;

import com.erp.common.core.web.domain.BaseEntity;

/**
 * 云盘物理容量、安全保留和三个逻辑额度池。
 */
public class DriveCapacityConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;
    private Long physicalCapacityBytes;
    private Integer reservePercent;
    private Long publicPoolBytes;
    private Long personalPoolBytes;
    private Long organizationPoolBytes;
    private String enforcementMode;
    private Integer version;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
    public Long getPhysicalCapacityBytes() { return physicalCapacityBytes; }
    public void setPhysicalCapacityBytes(Long physicalCapacityBytes) { this.physicalCapacityBytes = physicalCapacityBytes; }
    public Integer getReservePercent() { return reservePercent; }
    public void setReservePercent(Integer reservePercent) { this.reservePercent = reservePercent; }
    public Long getPublicPoolBytes() { return publicPoolBytes; }
    public void setPublicPoolBytes(Long publicPoolBytes) { this.publicPoolBytes = publicPoolBytes; }
    public Long getPersonalPoolBytes() { return personalPoolBytes; }
    public void setPersonalPoolBytes(Long personalPoolBytes) { this.personalPoolBytes = personalPoolBytes; }
    public Long getOrganizationPoolBytes() { return organizationPoolBytes; }
    public void setOrganizationPoolBytes(Long organizationPoolBytes) { this.organizationPoolBytes = organizationPoolBytes; }
    public String getEnforcementMode() { return enforcementMode; }
    public void setEnforcementMode(String enforcementMode) { this.enforcementMode = enforcementMode; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

