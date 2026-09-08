package com.erp.file.drive.mapper;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.domain.DriveNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveNodeMapper
{
    DriveNode selectById(@Param("nodeId") Long nodeId);

    DriveNode selectActiveById(@Param("nodeId") Long nodeId);

    DriveNode selectByIdForUpdate(@Param("nodeId") Long nodeId);

    List<DriveNode> selectActiveChildren(@Param("spaceId") Long spaceId,
            @Param("parentId") Long parentId,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection);

    List<DriveNode> selectActiveSearch(@Param("spaceId") Long spaceId,
            @Param("keywordPattern") String keywordPattern,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection);

    int existsActiveName(@Param("spaceId") Long spaceId,
            @Param("parentId") Long parentId,
            @Param("normalizedName") String normalizedName,
            @Param("excludeNodeId") Long excludeNodeId);

    int insertNode(DriveNode node);

    int updateName(@Param("nodeId") Long nodeId,
            @Param("nodeName") String nodeName,
            @Param("normalizedName") String normalizedName,
            @Param("extension") String extension,
            @Param("version") int version,
            @Param("updateBy") String updateBy);

    int updateParent(@Param("nodeId") Long nodeId,
            @Param("parentId") Long parentId,
            @Param("ancestors") String ancestors,
            @Param("version") int version,
            @Param("updateBy") String updateBy);

    int updateDescendantAncestors(@Param("spaceId") Long spaceId,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix,
            @Param("updateBy") String updateBy);

    List<DriveNode> selectPathNodes(@Param("spaceId") Long spaceId,
            @Param("nodeIds") List<Long> nodeIds);

    List<DriveNode> selectTrashRoots(@Param("spaceId") Long spaceId);

    List<DriveNode> selectTrashBatch(@Param("trashRootId") Long trashRootId);

    List<DriveNode> selectTrashBatchForUpdate(@Param("trashRootId") Long trashRootId);

    int trashActiveSubtree(@Param("spaceId") Long spaceId,
            @Param("rootNodeId") Long rootNodeId,
            @Param("rootPath") String rootPath,
            @Param("trashedBy") Long trashedBy,
            @Param("retentionDays") int retentionDays,
            @Param("updateBy") String updateBy);

    int restoreTrashBatch(@Param("trashRootId") Long trashRootId,
            @Param("rootNodeId") Long rootNodeId,
            @Param("parentId") Long parentId,
            @Param("rootAncestors") String rootAncestors,
            @Param("oldPrefix") String oldPrefix,
            @Param("newPrefix") String newPrefix,
            @Param("rootName") String rootName,
            @Param("normalizedName") String normalizedName,
            @Param("updateBy") String updateBy);

    int claimTrashRoot(@Param("trashRootId") Long trashRootId,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("staleBefore") Date staleBefore,
            @Param("updateBy") String updateBy);

    int claimTrashDescendants(@Param("trashRootId") Long trashRootId,
            @Param("rootNodeId") Long rootNodeId,
            @Param("expectedStatus") String expectedStatus,
            @Param("staleBefore") Date staleBefore,
            @Param("updateBy") String updateBy);

    DriveNode selectClaimRootForUpdate(@Param("trashRootId") Long trashRootId,
            @Param("claimVersion") int claimVersion);

    int markPurgeFailedRoot(@Param("trashRootId") Long trashRootId,
            @Param("claimVersion") int claimVersion,
            @Param("updateBy") String updateBy);

    int markPurgeFailedDescendants(@Param("trashRootId") Long trashRootId,
            @Param("rootNodeId") Long rootNodeId,
            @Param("updateBy") String updateBy);

    int deletePurgeBatch(@Param("trashRootId") Long trashRootId);

    List<DriveNode> selectExpiredTrashRoots(@Param("now") Date now,
            @Param("staleBefore") Date staleBefore,
            @Param("limit") int limit);
}
