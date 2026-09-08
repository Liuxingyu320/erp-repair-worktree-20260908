package com.erp.oa.attendance.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.config.AttendanceV2Properties;

/** Private append-only evidence storage; files are never punch watermarks. */
@Service
public class AttendanceRemainingWorkAttachmentStorage
{
    private static final Set<String> EXTENSIONS = Set.of(
            ".pdf", ".png", ".jpg", ".jpeg");
    private final Path root;
    private final Path tempRoot;
    private final long maxBytes;

    public AttendanceRemainingWorkAttachmentStorage(
            AttendanceV2Properties properties)
    {
        root = Paths.get(properties.getStorageRoot()).toAbsolutePath()
                .normalize();
        tempRoot = Paths.get(properties.getTempRoot(), "remaining-work")
                .toAbsolutePath().normalize();
        maxBytes = Math.max(1024L, properties.getMaxPhotoBytes());
    }

    public StoredAttachment store(Long confirmationId, MultipartFile file)
    {
        if (confirmationId == null || confirmationId <= 0)
            throw new ServiceException("REMAINING_WORK_CONFIRMATION_INVALID");
        if (file == null || file.isEmpty())
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_REQUIRED");
        if (file.getSize() <= 0 || file.getSize() > maxBytes)
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_SIZE_INVALID");
        String originalName = safeName(file.getOriginalFilename());
        String extension = extension(originalName);
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException exception)
        { throw failure("REMAINING_WORK_ATTACHMENT_READ_FAILED", exception); }
        if (bytes.length <= 0 || bytes.length > maxBytes)
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_SIZE_INVALID");
        String contentType = validate(extension, bytes);
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + extension;
        Path relative = Paths.get("remaining-work",
                String.valueOf(confirmationId), storedName);
        Path target = target(relative);
        Path staging = null;
        try
        {
            Files.createDirectories(tempRoot);
            Files.createDirectories(target.getParent());
            staging = Files.createTempFile(tempRoot, "evidence-", extension);
            Files.write(staging, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(staging, target,
                    StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored)
            { Files.move(staging, target); }
            return new StoredAttachment(originalName, portable(relative),
                    contentType, extension.substring(1), bytes.length,
                    sha256(bytes));
        }
        catch (IOException exception)
        {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw failure("REMAINING_WORK_ATTACHMENT_STORE_FAILED",
                    exception);
        }
    }

    public Path resolve(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()
                || relativePath.contains("\\"))
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_PATH_INVALID");
        Path value = target(Paths.get(relativePath));
        if (!Files.isRegularFile(value))
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_NOT_FOUND");
        return value;
    }

    public void deleteQuietly(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()) return;
        try { deleteQuietly(target(Paths.get(relativePath))); }
        catch (RuntimeException ignored) { }
    }

    private String validate(String extension, byte[] bytes)
    {
        boolean valid = switch (extension)
        {
            case ".pdf" -> startsWith(bytes,
                    "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case ".png" -> startsWith(bytes, new byte[] {
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
            case ".jpg", ".jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            default -> false;
        };
        if (!valid)
            throw new ServiceException(
                    "REMAINING_WORK_ATTACHMENT_CONTENT_INVALID");
        return switch (extension)
        {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String safeName(String value)
    {
        if (value == null || value.isBlank())
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_NAME_INVALID");
        String name;
        try { name = Paths.get(value.replace('\\', '/')).getFileName()
                .toString(); }
        catch (RuntimeException exception)
        { throw new ServiceException("REMAINING_WORK_ATTACHMENT_NAME_INVALID"); }
        if (name.contains("..") || name.length() > 180)
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_NAME_INVALID");
        return name;
    }

    private String extension(String name)
    {
        int index = name.lastIndexOf('.');
        String value = index < 0 ? ""
                : name.substring(index).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(value))
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_TYPE_INVALID");
        return value;
    }

    private Path target(Path relative)
    {
        if (relative == null || relative.isAbsolute())
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_PATH_INVALID");
        Path value = root.resolve(relative).toAbsolutePath().normalize();
        if (value.equals(root) || !value.startsWith(root))
            throw new ServiceException("REMAINING_WORK_ATTACHMENT_PATH_ESCAPE");
        return value;
    }

    private static boolean startsWith(byte[] source, byte[] prefix)
    {
        if (source.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++)
            if (source[index] != prefix[index]) return false;
        return true;
    }

    private static String sha256(byte[] bytes)
    {
        try { return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception)
        { throw new IllegalStateException(exception); }
    }

    private static String portable(Path value)
    { return value.toString().replace('\\', '/'); }
    private static void deleteQuietly(Path value)
    { if (value != null) try { Files.deleteIfExists(value); }
      catch (IOException ignored) { } }
    private static ServiceException failure(String message, Exception cause)
    { return new ServiceException(message).setDetailMessage(cause.getMessage()); }

    public record StoredAttachment(String originalName, String relativePath,
            String contentType, String extension, long size, String sha256) { }
}
