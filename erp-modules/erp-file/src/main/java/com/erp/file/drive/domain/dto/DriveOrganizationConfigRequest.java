package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class DriveOrganizationConfigRequest
{
    @NotNull
    private Boolean enabled;
    @NotNull @Positive
    private Long quotaBytes;
    @PositiveOrZero
    private Long treeBudgetBytes;
    @NotBlank
    private String memberWriteMode;
    @NotBlank
    private String lifecycleStatus;
    @NotNull @Min(0)
    private Integer version;
    @NotBlank @Size(max = 500)
    private String reason;
    @NotBlank
    private String impactHash;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean value) { this.enabled = value; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long value) { this.quotaBytes = value; }
    public Long getTreeBudgetBytes() { return treeBudgetBytes; }
    public void setTreeBudgetBytes(Long value) { this.treeBudgetBytes = value; }
    public String getMemberWriteMode() { return memberWriteMode; }
    public void setMemberWriteMode(String value) { this.memberWriteMode = value; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String value) { this.lifecycleStatus = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String value) { this.impactHash = value; }
}
