package com.erp.file.drive.storage;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * 与公开附件目录隔离的本地云盘对象存储。
 */
@Service
@ConditionalOnProperty(prefix = "drive", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalDriveStorageProvider implements DriveStorageProvider
{
    private final Path root;

    public LocalDriveStorageProvider(DriveProperties properties)
    {
        Objects.requireNonNull(properties, "properties");
        String localPath = properties.getLocalPath();
        if (localPath == null || localPath.isBlank())
        {
            throw new IllegalArgumentException("drive.local-path must not be blank");
        }
        this.root = Path.of(localPath).toAbsolutePath().normalize();
    }

    @Override
    public void put(String storageKey, InputStream input) throws IOException
    {
        Objects.requireNonNull(input, "input");
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try
        {
            Files.copy(input, part, REPLACE_EXISTING);
            try
            {
                Files.move(part, target, ATOMIC_MOVE, REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ex)
            {
                Files.move(part, target, REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(part);
        }
    }

    @Override
    public DriveStoredObject open(String storageKey) throws IOException
    {
        Path object = resolve(storageKey);
        if (!Files.isRegularFile(object))
        {
            throw new NoSuchFileException(storageKey);
        }
        return new DriveStoredObject(new FileSystemResource(object), Files.size(object));
    }

    @Override
    public boolean exists(String storageKey)
    {
        return Files.isRegularFile(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException
    {
        Files.deleteIfExists(resolve(storageKey));
    }

    @Override
    public void validate() throws IOException
    {
        Files.createDirectories(root);
        if (!Files.isDirectory(root) || !Files.isWritable(root))
        {
            throw new IOException("Cloud drive local storage is not a writable directory");
        }
    }

    private Path resolve(String storageKey)
    {
        if (storageKey == null || storageKey.isBlank())
        {
            throw accessDenied();
        }
        try
        {
            Path candidate = root.resolve(storageKey).normalize();
            if (candidate.equals(root) || !candidate.startsWith(root))
            {
                throw accessDenied();
            }
            return candidate;
        }
        catch (InvalidPathException ex)
        {
            throw accessDenied();
        }
    }

    private static DriveException accessDenied()
    {
        return new DriveException(DriveErrorCodes.DRIVE_ACCESS_DENIED, "非法存储路径");
    }
}
