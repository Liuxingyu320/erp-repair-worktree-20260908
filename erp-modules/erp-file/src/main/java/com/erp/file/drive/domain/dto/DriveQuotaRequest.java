package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DriveQuotaRequest
{
    @NotNull
    @Positive
    private Long quotaBytes;

    @NotNull
    @Min(0)
    private Integer version;

    public Long getQuotaBytes()
    {
        return quotaBytes;
    }

    public void setQuotaBytes(Long quotaBytes)
    {
        this.quotaBytes = quotaBytes;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }
}
