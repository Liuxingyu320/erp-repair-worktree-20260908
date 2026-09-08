package com.erp.file.drive.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DriveStorageProvider
{
    void put(String storageKey, InputStream input) throws IOException;

    DriveStoredObject open(String storageKey) throws IOException;

    boolean exists(String storageKey);

    void delete(String storageKey) throws IOException;

    void validate() throws IOException;
}
