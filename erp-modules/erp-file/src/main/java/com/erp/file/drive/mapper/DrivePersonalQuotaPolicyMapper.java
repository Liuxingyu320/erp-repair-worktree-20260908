package com.erp.file.drive.mapper;

import java.util.Date;
import java.util.List;
import com.erp.file.drive.domain.DrivePersonalQuotaPolicy;
import com.erp.file.drive.domain.DriveUserQuotaContext;
import com.erp.file.drive.domain.vo.DrivePostQuotaOptionVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DrivePersonalQuotaPolicyMapper
{
    DrivePersonalQuotaPolicy selectBySubject(@Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId);

    DrivePersonalQuotaPolicy selectGlobalPolicy();

    DrivePersonalQuotaPolicy selectActiveUserPolicy(@Param("userId") Long userId,
            @Param("now") Date now);

    List<DrivePersonalQuotaPolicy> selectActivePostPolicies(@Param("userId") Long userId,
            @Param("now") Date now);

    List<DrivePersonalQuotaPolicy> selectAllPolicies();

    List<DrivePostQuotaOptionVo> selectPostOptions();

    List<DriveUserQuotaContext> selectActiveUserContexts();

    int countActiveUser(@Param("userId") Long userId);

    int countActivePost(@Param("postId") Long postId);

    int insertPolicy(DrivePersonalQuotaPolicy policy);

    int updatePolicy(DrivePersonalQuotaPolicy policy);

    int deletePolicy(@Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId, @Param("version") Integer version);
}
