package com.erp.file.drive.mapper;

import com.erp.file.drive.domain.DriveCapacityConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveCapacityMapper
{
    DriveCapacityConfig selectConfig(@Param("configId") Long configId);

    DriveCapacityConfig selectConfigForUpdate(@Param("configId") Long configId);

    int updateConfig(DriveCapacityConfig config);
}

