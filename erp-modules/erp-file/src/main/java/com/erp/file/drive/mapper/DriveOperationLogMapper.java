package com.erp.file.drive.mapper;

import java.util.List;
import com.erp.file.drive.domain.DriveNode;
import com.erp.file.drive.domain.DriveOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveOperationLogMapper
{
    int insertOperation(DriveOperationLog operation);

    List<DriveNode> selectRecentNodes(@Param("userId") Long userId,
            @Param("limit") int limit);
}
