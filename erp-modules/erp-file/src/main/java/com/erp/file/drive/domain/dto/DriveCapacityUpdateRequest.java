package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class DriveCapacityUpdateRequest
{
    private Long physicalCapacityBytes;
    @NotNull @Min(0) @Max(90)
    private Integer reservePercent;
    @NotNull @PositiveOrZero
    private Long publicPoolBytes;
    @NotNull @PositiveOrZero
    private Long personalPoolBytes;
    @NotNull @PositiveOrZero
    private Long organizationPoolBytes;
    @NotBlank
    private String enforcementMode;
    @NotNull @Min(0)
    private Integer version;
    @NotBlank @Size(max = 500)
    private String reason;
    @NotBlank
    private String impactHash;

    public Long getPhysicalCapacityBytes() { return physicalCapacityBytes; }
    public void setPhysicalCapacityBytes(Long value) { this.physicalCapacityBytes = value; }
    public Integer getReservePercent() { return reservePercent; }
    public void setReservePercent(Integer value) { this.reservePercent = value; }
    public Long getPublicPoolBytes() { return publicPoolBytes; }
    public void setPublicPoolBytes(Long value) { this.publicPoolBytes = value; }
    public Long getPersonalPoolBytes() { return personalPoolBytes; }
    public void setPersonalPoolBytes(Long value) { this.personalPoolBytes = value; }
    public Long getOrganizationPoolBytes() { return organizationPoolBytes; }
    public void setOrganizationPoolBytes(Long value) { this.organizationPoolBytes = value; }
    public String getEnforcementMode() { return enforcementMode; }
    public void setEnforcementMode(String value) { this.enforcementMode = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String value) { this.impactHash = value; }
}
