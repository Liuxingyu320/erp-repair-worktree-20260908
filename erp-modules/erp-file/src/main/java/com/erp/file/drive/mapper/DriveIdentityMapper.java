package com.erp.file.drive.mapper;

import com.erp.file.drive.domain.DriveIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveIdentityMapper
{
    DriveIdentity selectCurrentIdentity(@Param("userId") Long userId);
}
