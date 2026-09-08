package com.erp.file.drive.service;

import com.erp.file.drive.domain.DriveOperationLog;
import com.erp.file.drive.mapper.DriveOperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveOperationLogPersistence
{
    private final DriveOperationLogMapper mapper;

    public DriveOperationLogPersistence(DriveOperationLogMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int insert(DriveOperationLog operation)
    {
        return mapper.insertOperation(operation);
    }
}
