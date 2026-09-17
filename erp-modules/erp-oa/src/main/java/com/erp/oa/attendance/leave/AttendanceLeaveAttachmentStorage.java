package com.erp.oa.attendance.leave;

import com.erp.common.core.utils.file.UploadImageNormalizer;
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

/** Private, content-validated storage for leave certificates. */
@Service
public class AttendanceLeaveAttachmentStorage
{
    private static final Set<String> EXTENSIONS = Set.of(
            ".pdf", ".png", ".jpg", ".jpeg");

    private final Path root;
    private final Path tempRoot;
    private final long maxBytes;

    public AttendanceLeaveAttachmentStorage(AttendanceV2Properties properties)
    {
        root = Paths.get(properties.getStorageRoot()).toAbsolutePath()
                .normalize();
        tempRoot = Paths.get(properties.getTempRoot(), "leave")
                .toAbsolutePath().normalize();
        maxBytes = Math.max(1024L, properties.getMaxPhotoBytes());
    }

    public StoredAttachment store(Long leaveRequestId, MultipartFile file)
    {
        return store(leaveRequestId, prepare(file));
    }

    PreparedAttachment prepare(MultipartFile file)
    {
        if (file == null || file.isEmpty())
            throw new ServiceException("LEAVE_ATTACHMENT_REQUIRED");
        if (file.getSize() <= 0 || file.getSize() > maxBytes)
            throw new ServiceException("LEAVE_ATTACHMENT_SIZE_INVALID");
        String originalName = safeName(file.getOriginalFilename());
        String extension = extension(originalName);
        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (IOException exception)
        {
            throw failure("LEAVE_ATTACHMENT_READ_FAILED", exception);
        }
        if (bytes.length <= 0 || bytes.length > maxBytes)
            throw new ServiceException("LEAVE_ATTACHMENT_SIZE_INVALID");
        String contentType = validate(extension, bytes);
        if (!".pdf".equals(extension) && !".ofd".equals(extension))
            bytes = UploadImageNormalizer.normalize(bytes, originalName);
        return new PreparedAttachment(originalName, contentType,
                extension.substring(1), bytes, sha256(bytes));
    }

    StoredAttachment store(Long leaveRequestId, PreparedAttachment prepared)
    {
        if (leaveRequestId == null || leaveRequestId <= 0)
            throw new ServiceException("LEAVE_REQUEST_ID_INVALID");
        if (prepared == null)
            throw new ServiceException("LEAVE_ATTACHMENT_REQUIRED");
        String extension = "." + prepared.extension();
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + extension;
        Path relative = Paths.get("leave", String.valueOf(leaveRequestId),
                storedName);
        Path target = target(relative);
        Path staging = null;
        try
        {
            Files.createDirectories(tempRoot);
            Files.createDirectories(target.getParent());
            staging = Files.createTempFile(tempRoot, "attachment-", extension);
            Files.write(staging, prepared.bytes(),
                    StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException exception)
            {
                Files.move(staging, target);
            }
            return new StoredAttachment(prepared.originalName(),
                    portable(relative), prepared.contentType(),
                    prepared.extension(), prepared.bytes().length,
                    prepared.sha256());
        }
        catch (IOException exception)
        {
            deletePathQuietly(staging);
            deletePathQuietly(target);
            throw failure("LEAVE_ATTACHMENT_STORE_FAILED", exception);
        }
    }

    public Path resolve(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()
                || relativePath.contains("\\"))
            throw new ServiceException("LEAVE_ATTACHMENT_PATH_INVALID");
        Path value = target(Paths.get(relativePath));
        if (!Files.isRegularFile(value))
            throw new ServiceException("LEAVE_ATTACHMENT_NOT_FOUND");
        return value;
    }

    public void deleteQuietly(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()) return;
        try
        {
            deletePathQuietly(target(Paths.get(relativePath)));
        }
        catch (RuntimeException ignored)
        {
            // Best-effort compensation after transaction completion.
        }
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
            throw new ServiceException("LEAVE_ATTACHMENT_CONTENT_INVALID");
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
            throw new ServiceException("LEAVE_ATTACHMENT_NAME_INVALID");
        String name;
        try
        {
            name = Paths.get(value.replace('\\', '/')).getFileName()
                    .toString();
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("LEAVE_ATTACHMENT_NAME_INVALID");
        }
        StringBuilder safe = new StringBuilder();
        name.codePoints().filter(code -> !Character.isISOControl(code))
                .forEach(code -> {
                    if (safe.length() + Character.charCount(code) <= 180)
                        safe.appendCodePoint(code);
                });
        if (safe.isEmpty() || safe.toString().contains(".."))
            throw new ServiceException("LEAVE_ATTACHMENT_NAME_INVALID");
        return safe.toString();
    }

    private String extension(String name)
    {
        int index = name.lastIndexOf('.');
        String value = index < 0 ? ""
                : name.substring(index).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(value))
            throw new ServiceException("LEAVE_ATTACHMENT_TYPE_INVALID");
        return value;
    }

    private Path target(Path relative)
    {
        if (relative == null || relative.isAbsolute())
            throw new ServiceException("LEAVE_ATTACHMENT_PATH_INVALID");
        Path value = root.resolve(relative).toAbsolutePath().normalize();
        if (value.equals(root) || !value.startsWith(root))
            throw new ServiceException("LEAVE_ATTACHMENT_PATH_ESCAPE");
        return value;
    }

    private static boolean startsWith(byte[] source, byte[] prefix)
    {
        if (source.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++)
            if (source[index] != prefix[index]) return false;
        return true;
    }

    private static String portable(Path path)
    { return path.toString().replace('\\', '/'); }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private static void deletePathQuietly(Path path)
    {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { }
    }

    private static ServiceException failure(String message, Exception cause)
    { return new ServiceException(message).setDetailMessage(cause.getMessage()); }

    record PreparedAttachment(String originalName, String contentType,
            String extension, byte[] bytes, String sha256) { }

    public record StoredAttachment(String originalName, String relativePath,
            String contentType, String extension, long size, String sha256) { }
}
