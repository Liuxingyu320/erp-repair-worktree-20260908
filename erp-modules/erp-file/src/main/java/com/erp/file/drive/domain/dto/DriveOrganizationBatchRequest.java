package com.erp.file.drive.domain.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 将同一组安全配置应用到明确勾选的组织。 */
public class DriveOrganizationBatchRequest
{
    @Valid
    @NotEmpty
    @Size(max = 200)
    private List<DriveOrganizationBatchTarget> targets;
    @NotNull
    private Boolean enabled;
    @NotNull
    private Long quotaBytes;
    @NotBlank
    private String memberWriteMode;
    @NotBlank
    private String lifecycleStatus;
    @NotBlank
    private String impactHash;
    @NotBlank @Size(max = 500)
    private String reason;

    public List<DriveOrganizationBatchTarget> getTargets() { return targets; }
    public void setTargets(List<DriveOrganizationBatchTarget> targets) { this.targets = targets; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public String getMemberWriteMode() { return memberWriteMode; }
    public void setMemberWriteMode(String memberWriteMode) { this.memberWriteMode = memberWriteMode; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String impactHash) { this.impactHash = impactHash; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
