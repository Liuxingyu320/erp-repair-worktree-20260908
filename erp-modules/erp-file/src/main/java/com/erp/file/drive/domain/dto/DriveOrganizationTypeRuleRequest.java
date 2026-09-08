package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 修改组织类型自动建盘规则。 */
public class DriveOrganizationTypeRuleRequest
{
    @NotNull
    private Boolean autoEnable;
    @NotNull
    private Boolean requireActiveMember;
    @NotNull
    private Long defaultQuotaBytes;
    @NotBlank
    private String status;
    @NotNull
    private Integer version;
    @NotBlank
    private String impactHash;
    @NotBlank @Size(max = 500)
    private String reason;

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
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String impactHash) { this.impactHash = impactHash; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
