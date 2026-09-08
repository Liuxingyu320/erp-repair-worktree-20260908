package com.erp.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.erp.system.domain.HrOnboardingPositionConfig;

public interface HrOnboardingPositionConfigMapper
{
    List<HrOnboardingPositionConfig> selectList(HrOnboardingPositionConfig query);

    HrOnboardingPositionConfig selectById(Long configId);

    HrOnboardingPositionConfig selectByIdForUpdate(Long configId);

    HrOnboardingPositionConfig selectByPair(@Param("postId") Long postId,
            @Param("employeeCategory") String employeeCategory);

    int insert(HrOnboardingPositionConfig config);

    int updateByVersion(HrOnboardingPositionConfig config);

    int disableByVersion(@Param("configId") Long configId, @Param("version") Integer version,
            @Param("operator") String operator);

    List<Long> selectRoleIds(Long configId);

    int deleteRolesByConfigId(Long configId);

    int insertRole(@Param("configId") Long configId, @Param("roleId") Long roleId,
            @Param("operator") String operator);
}
