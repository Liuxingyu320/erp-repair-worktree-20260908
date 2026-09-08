package com.erp.file.drive.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class DriveMoveRequest
{
    @NotNull
    @Min(0)
    private Long targetParentId;

    @NotNull
    @Min(0)
    private Integer version;

    public Long getTargetParentId()
    {
        return targetParentId;
    }

    public void setTargetParentId(Long targetParentId)
    {
        this.targetParentId = targetParentId;
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
