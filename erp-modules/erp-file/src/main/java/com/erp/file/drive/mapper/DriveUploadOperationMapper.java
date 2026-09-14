package com.erp.file.drive.mapper;

import com.erp.file.drive.domain.DriveUploadOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DriveUploadOperationMapper
{
    int insertIfAbsent(DriveUploadOperation operation);
    DriveUploadOperation selectForUpdate(@Param("operationId") String operationId);
    DriveUploadOperation selectById(@Param("operationId") String operationId);
    int reclaim(@Param("operationId") String operationId, @Param("owner") String owner);
    int changeStatus(@Param("operationId") String operationId, @Param("owner") String owner,
            @Param("expected") String expected, @Param("status") String status, @Param("nodeId") Long nodeId);
}
