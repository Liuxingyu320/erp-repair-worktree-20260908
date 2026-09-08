package com.erp.file.drive.domain.vo;

import java.util.Date;
import java.util.List;

/**
 * 云盘节点对外响应；不包含物理存储键或内容摘要。
 */
public record DriveNodeVo(Long nodeId, Long spaceId, String spaceName,
        Long parentId, String nodeType, String nodeName, String extension,
        String contentType, Long sizeBytes, String status, Integer version,
        String createBy, Date createTime, String updateBy, Date updateTime,
        String logicalPath, List<Long> ancestorIds,
        List<DriveBreadcrumbVo> breadcrumbs,
        boolean canWrite, boolean canDelete, boolean canPreview)
{
    public DriveNodeVo
    {
        ancestorIds = ancestorIds == null ? List.of() : List.copyOf(ancestorIds);
        breadcrumbs = breadcrumbs == null ? List.of() : List.copyOf(breadcrumbs);
    }
}
