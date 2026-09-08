package com.erp.file.drive.domain.dto;

import com.erp.file.drive.constant.DriveConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class DriveFolderCreateRequest
{
    @NotNull
    @Positive
    private Long spaceId;

    @NotNull
    @Min(0)
    private Long parentId = DriveConstants.ROOT_PARENT_ID;

    @NotBlank
    @Size(max = 200)
    private String name;

    public Long getSpaceId()
    {
        return spaceId;
    }

    public void setSpaceId(Long spaceId)
    {
        this.spaceId = spaceId;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
