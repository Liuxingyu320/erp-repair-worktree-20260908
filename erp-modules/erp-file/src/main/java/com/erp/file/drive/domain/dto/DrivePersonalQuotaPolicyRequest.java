package com.erp.file.drive.domain.dto;

import java.util.Date;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class DrivePersonalQuotaPolicyRequest
{
    @NotNull @Positive
    private Long quotaBytes;
    @NotNull @Min(0) @Max(1000)
    private Integer priority;
    private Date expireTime;
    @NotNull @Min(0)
    private Integer version;
    @NotBlank @Size(max = 500)
    private String reason;
    @NotBlank
    private String impactHash;

    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long value) { this.quotaBytes = value; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer value) { this.priority = value; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date value) { this.expireTime = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getImpactHash() { return impactHash; }
    public void setImpactHash(String value) { this.impactHash = value; }
}
