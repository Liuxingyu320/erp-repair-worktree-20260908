package com.erp.file.drive.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 云盘空间持久化模型。
 */
public class DriveSpace extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long spaceId;
    private String spaceKey;
    private String spaceType;
    private Long ownerUserId;
    private Long deptId;
    private String spaceName;
    private Long quotaBytes;
    private Long usedBytes;
    private String quotaSourceType;
    private Long quotaSourceId;
    private Date quotaSyncedTime;
    private String status;
    private Integer version;

    public Long getSpaceId()
    {
        return spaceId;
    }

    public void setSpaceId(Long spaceId)
    {
        this.spaceId = spaceId;
    }

    public String getSpaceKey()
    {
        return spaceKey;
    }

    public void setSpaceKey(String spaceKey)
    {
        this.spaceKey = spaceKey;
    }

    public String getSpaceType()
    {
        return spaceType;
    }

    public void setSpaceType(String spaceType)
    {
        this.spaceType = spaceType;
    }

    public Long getOwnerUserId()
    {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId)
    {
        this.ownerUserId = ownerUserId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getSpaceName()
    {
        return spaceName;
    }

    public void setSpaceName(String spaceName)
    {
        this.spaceName = spaceName;
    }

    public Long getQuotaBytes()
    {
        return quotaBytes;
    }

    public void setQuotaBytes(Long quotaBytes)
    {
        this.quotaBytes = quotaBytes;
    }

    public Long getUsedBytes()
    {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes)
    {
        this.usedBytes = usedBytes;
    }

    public String getQuotaSourceType()
    {
        return quotaSourceType;
    }

    public void setQuotaSourceType(String quotaSourceType)
    {
        this.quotaSourceType = quotaSourceType;
    }

    public Long getQuotaSourceId()
    {
        return quotaSourceId;
    }

    public void setQuotaSourceId(Long quotaSourceId)
    {
        this.quotaSourceId = quotaSourceId;
    }

    public Date getQuotaSyncedTime()
    {
        return quotaSyncedTime;
    }

    public void setQuotaSyncedTime(Date quotaSyncedTime)
    {
        this.quotaSyncedTime = quotaSyncedTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
