package com.erp.file.drive.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;

public record DriveStoredObject(Resource resource, long size) implements AutoCloseable
{
    @Override
    public void close() throws IOException
    {
        if (resource != null && resource.isOpen())
        {
            resource.getInputStream().close();
        }
    }
}
