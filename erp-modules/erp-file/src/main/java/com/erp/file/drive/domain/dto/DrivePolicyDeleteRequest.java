package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DrivePolicyDeleteRequest
{
    @NotNull @Min(0)
    private Integer version;
    @NotBlank @Size(max = 500)
    private String reason;
    @NotBlank
    private String impactHash;

    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String value) { this.impactHash = value; }
}
