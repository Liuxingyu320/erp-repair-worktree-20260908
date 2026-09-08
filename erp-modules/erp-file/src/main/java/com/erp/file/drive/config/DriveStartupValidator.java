package com.erp.file.drive.config;

import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.mapper.DriveSpaceMapper;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 功能开启时 fail-fast，避免暴露只有部分依赖可用的云盘入口。
 */
@Component
public class DriveStartupValidator implements ApplicationRunner
{
    private final DriveProperties properties;
    private final DriveStorageProvider storage;
    private final DriveSpaceMapper spaceMapper;

    public DriveStartupValidator(DriveProperties properties,
            DriveStorageProvider storage, DriveSpaceMapper spaceMapper)
    {
        this.properties = properties;
        this.storage = storage;
        this.spaceMapper = spaceMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception
    {
        if (!properties.isEnabled())
        {
            return;
        }
        storage.validate();
        if (spaceMapper.selectByKey(DriveConstants.COMPANY_SPACE_KEY) == null)
        {
            throw new IllegalStateException("cloud drive schema/company space is not ready");
        }
    }
}
