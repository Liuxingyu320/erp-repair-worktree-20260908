package com.erp.file.drive.mapper;

import java.util.List;
import com.erp.file.drive.domain.DriveSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveSpaceMapper
{
    DriveSpace selectById(@Param("spaceId") Long spaceId);

    DriveSpace selectByIdForUpdate(@Param("spaceId") Long spaceId);

    DriveSpace selectByKey(@Param("spaceKey") String spaceKey);

    List<DriveSpace> selectByDeptIds(@Param("deptIds") List<Long> deptIds);

    List<DriveSpace> selectByType(@Param("spaceType") String spaceType);

    Long selectTotalUsedBytes();

    int insertIgnore(DriveSpace space);

    int reserveQuota(@Param("spaceId") Long spaceId, @Param("bytes") long bytes);

    int releaseQuota(@Param("spaceId") Long spaceId, @Param("bytes") long bytes);

    int updateQuota(@Param("spaceId") Long spaceId,
            @Param("quotaBytes") long quotaBytes,
            @Param("version") int version,
            @Param("updateBy") String updateBy);

    int updateEffectiveQuota(@Param("spaceId") Long spaceId,
            @Param("quotaBytes") long quotaBytes,
            @Param("quotaSourceType") String quotaSourceType,
            @Param("quotaSourceId") Long quotaSourceId,
            @Param("version") int version,
            @Param("updateBy") String updateBy);

    int updateOrganizationMetadata(@Param("deptId") Long deptId,
            @Param("spaceName") String spaceName,
            @Param("status") String status,
            @Param("updateBy") String updateBy);
}
