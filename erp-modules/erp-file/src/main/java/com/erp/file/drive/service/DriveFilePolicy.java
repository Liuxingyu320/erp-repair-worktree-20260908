package com.erp.file.drive.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.exception.DriveException;
import org.springframework.stereotype.Service;

@Service
public class DriveFilePolicy
{
    private static final String OCTET_STREAM = "application/octet-stream";

    private static final Set<String> ACTIVE_MIME_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "image/svg+xml",
            "application/javascript", "text/javascript", "application/x-javascript",
            "text/x-shellscript", "application/x-sh", "application/x-csh",
            "application/x-msdownload", "application/x-executable", "application/x-dosexec",
            "application/java-archive");

    private static final Map<String, Set<String>> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("doc", Set.of("application/msword", OCTET_STREAM)),
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    OCTET_STREAM)),
            Map.entry("xls", Set.of("application/vnd.ms-excel", OCTET_STREAM)),
            Map.entry("xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    OCTET_STREAM)),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint", OCTET_STREAM)),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    OCTET_STREAM)),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/plain", "text/csv", "application/csv",
                    "application/vnd.ms-excel")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("heic", Set.of("image/heic", "image/heic-sequence")),
            Map.entry("heif", Set.of("image/heif", "image/heif-sequence")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed", OCTET_STREAM)),
            Map.entry("rar", Set.of("application/vnd.rar", "application/x-rar-compressed", OCTET_STREAM)),
            Map.entry("7z", Set.of("application/x-7z-compressed", OCTET_STREAM)));

    private static final Set<String> PREVIEW_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv");

    private final DriveProperties properties;

    public DriveFilePolicy(DriveProperties properties)
    {
        this.properties = properties;
    }

    public void validate(String fileName, String contentType, long size)
    {
        if (size < 0 || size > properties.getMaxFileSize())
        {
            throw new DriveException(DriveErrorCodes.DRIVE_FILE_TOO_LARGE, "文件大小超过限制");
        }
        String extension = extension(fileName);
        String mime = normalizeMime(contentType);
        if (ACTIVE_MIME_TYPES.contains(mime) || !matches(extension, mime))
        {
            throw rejectedType();
        }
    }

    public boolean isPreviewable(String extension, String contentType)
    {
        if (extension == null)
        {
            return false;
        }
        String normalizedExtension = extension.startsWith(".")
                ? extension.substring(1) : extension;
        normalizedExtension = normalizedExtension.toLowerCase(Locale.ROOT);
        String mime = normalizeMime(contentType);
        return PREVIEW_EXTENSIONS.contains(normalizedExtension)
                && !ACTIVE_MIME_TYPES.contains(mime)
                && matches(normalizedExtension, mime);
    }

    public String extension(String fileName)
    {
        if (fileName == null)
        {
            throw rejectedType();
        }
        String trimmed = fileName.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot < 0 || dot == trimmed.length() - 1)
        {
            throw rejectedType();
        }
        String extension = trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!MIME_BY_EXTENSION.containsKey(extension))
        {
            throw rejectedType();
        }
        return extension;
    }

    private static boolean matches(String extension, String mime)
    {
        Set<String> allowed = MIME_BY_EXTENSION.get(extension);
        return allowed != null && allowed.contains(mime);
    }

    private static String normalizeMime(String contentType)
    {
        if (contentType == null || contentType.isBlank())
        {
            return OCTET_STREAM;
        }
        int parameters = contentType.indexOf(';');
        String mime = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        return mime.trim().toLowerCase(Locale.ROOT);
    }

    private static DriveException rejectedType()
    {
        return new DriveException(DriveErrorCodes.DRIVE_FILE_TYPE_REJECTED, "不支持该文件类型");
    }
}
