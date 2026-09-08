package com.erp.file.drive.mapper;

import java.util.List;
import com.erp.file.drive.domain.DriveOrganization;
import com.erp.file.drive.domain.DriveOrganizationSpaceConfig;
import com.erp.file.drive.domain.DriveOrganizationTypeRule;
import com.erp.file.drive.domain.DriveRoleScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveOrganizationMapper
{
    DriveOrganization selectOrganization(@Param("deptId") Long deptId);

    List<DriveOrganization> selectAllOrganizations();

    List<DriveRoleScope> selectRoleScopes(@Param("userId") Long userId);

    DriveOrganizationTypeRule selectTypeRule(@Param("deptType") String deptType);

    List<DriveOrganizationTypeRule> selectTypeRules();

    int updateTypeRule(DriveOrganizationTypeRule rule);

    DriveOrganizationSpaceConfig selectConfig(@Param("deptId") Long deptId);

    DriveOrganizationSpaceConfig selectConfigForUpdate(@Param("deptId") Long deptId);

    List<DriveOrganizationSpaceConfig> selectConfigs();

    int insertConfig(DriveOrganizationSpaceConfig config);

    int updateConfig(DriveOrganizationSpaceConfig config);
}

