package com.erp.file.drive.domain;

import java.util.Date;
import com.erp.common.core.web.domain.BaseEntity;

/**
 * 云盘文件或文件夹持久化模型。
 */
public class DriveNode extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long nodeId;
    private Long spaceId;
    private String spaceName;
    private Long parentId;
    private String ancestors;
    private String nodeType;
    private String nodeName;
    private String normalizedName;
    private String extension;
    private String storageKey;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String status;
    private Integer activeFlag;
    private Long originalParentId;
    private Long trashRootId;
    private Long trashedBy;
    private Date trashedTime;
    private Date purgeAfter;
    private Integer version;

    public Long getNodeId()
    {
        return nodeId;
    }

    public void setNodeId(Long nodeId)
    {
        this.nodeId = nodeId;
    }

    public Long getSpaceId()
    {
        return spaceId;
    }

    public void setSpaceId(Long spaceId)
    {
        this.spaceId = spaceId;
    }

    public String getSpaceName()
    {
        return spaceName;
    }

    public void setSpaceName(String spaceName)
    {
        this.spaceName = spaceName;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public String getAncestors()
    {
        return ancestors;
    }

    public void setAncestors(String ancestors)
    {
        this.ancestors = ancestors;
    }

    public String getNodeType()
    {
        return nodeType;
    }

    public void setNodeType(String nodeType)
    {
        this.nodeType = nodeType;
    }

    public String getNodeName()
    {
        return nodeName;
    }

    public void setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
    }

    public String getNormalizedName()
    {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName)
    {
        this.normalizedName = normalizedName;
    }

    public String getExtension()
    {
        return extension;
    }

    public void setExtension(String extension)
    {
        this.extension = extension;
    }

    public String getStorageKey()
    {
        return storageKey;
    }

    public void setStorageKey(String storageKey)
    {
        this.storageKey = storageKey;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public Long getSizeBytes()
    {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes)
    {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256()
    {
        return sha256;
    }

    public void setSha256(String sha256)
    {
        this.sha256 = sha256;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getActiveFlag()
    {
        return activeFlag;
    }

    public void setActiveFlag(Integer activeFlag)
    {
        this.activeFlag = activeFlag;
    }

    public Long getOriginalParentId()
    {
        return originalParentId;
    }

    public void setOriginalParentId(Long originalParentId)
    {
        this.originalParentId = originalParentId;
    }

    public Long getTrashRootId()
    {
        return trashRootId;
    }

    public void setTrashRootId(Long trashRootId)
    {
        this.trashRootId = trashRootId;
    }

    public Long getTrashedBy()
    {
        return trashedBy;
    }

    public void setTrashedBy(Long trashedBy)
    {
        this.trashedBy = trashedBy;
    }

    public Date getTrashedTime()
    {
        return trashedTime;
    }

    public void setTrashedTime(Date trashedTime)
    {
        this.trashedTime = trashedTime;
    }

    public Date getPurgeAfter()
    {
        return purgeAfter;
    }

    public void setPurgeAfter(Date purgeAfter)
    {
        this.purgeAfter = purgeAfter;
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
