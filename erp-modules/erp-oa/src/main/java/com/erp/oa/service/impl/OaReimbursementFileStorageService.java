package com.erp.oa.service.impl;

import com.erp.common.core.utils.file.UploadImageNormalizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaReimbursementProperties;

@Service
public class OaReimbursementFileStorageService
{
    private static final Set<String> EXTENSIONS = Set.of(
            ".pdf", ".png", ".jpg", ".jpeg", ".ofd");

    private final Path root;
    private final Path tempRoot;
    private final long maxInvoiceBytes;

    public OaReimbursementFileStorageService(
            OaReimbursementProperties properties)
    {
        root = Paths.get(properties.getStorageRoot()).toAbsolutePath()
                .normalize();
        tempRoot = Paths.get(properties.getTempRoot()).toAbsolutePath()
                .normalize();
        maxInvoiceBytes = properties.getMaxInvoiceBytes();
    }

    public StoredInvoice storeInvoice(Long reimbursementId,
            MultipartFile file)
    {
        if (reimbursementId == null || reimbursementId <= 0)
        {
            throw new ServiceException("报销单编号不合法");
        }
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("发票文件不能为空");
        }
        if (file.getSize() <= 0 || file.getSize() > maxInvoiceBytes)
        {
            throw new ServiceException("单个发票文件不能超过"
                    + Math.max(1, maxInvoiceBytes / 1024 / 1024) + "MB");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = extension(originalName);
        byte[] bytes;
        try
        {
            bytes = file.getBytes();
        }
        catch (IOException exception)
        {
            throw storageFailure("读取发票文件失败", exception);
        }
        String contentType = validateContent(extension, bytes);
        if (!".pdf".equals(extension) && !".ofd".equals(extension))
            bytes = UploadImageNormalizer.normalize(bytes, originalName);
        String storedName = UUID.randomUUID().toString().replace("-", "")
                + extension;
        Path relative = Paths.get("invoices",
                String.valueOf(reimbursementId), storedName);
        Path target = resolveTarget(relative);
        Path staging = null;
        try
        {
            Files.createDirectories(tempRoot);
            Files.createDirectories(target.getParent());
            staging = Files.createTempFile(tempRoot, "invoice-", extension);
            Files.write(staging, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException exception)
            {
                Files.move(staging, target);
            }
            return new StoredInvoice(originalName, storedName,
                    portable(relative), contentType, extension.substring(1),
                    bytes.length, sha256(bytes));
        }
        catch (IOException exception)
        {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw storageFailure("保存发票文件失败", exception);
        }
    }

    public Path resolve(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank()
                || relativePath.contains("\\"))
        {
            throw new ServiceException("发票文件路径无效");
        }
        Path value = resolveTarget(Paths.get(relativePath));
        if (!Files.isRegularFile(value))
        {
            throw new ServiceException("发票文件不存在");
        }
        return value;
    }

    public void delete(String relativePath)
    {
        Path value = resolveTarget(Paths.get(relativePath));
        try
        {
            Files.deleteIfExists(value);
        }
        catch (IOException exception)
        {
            throw storageFailure("删除发票文件失败", exception);
        }
    }

    public Path createExportTemp(String batchNo)
    {
        safeBatchNo(batchNo);
        try
        {
            Files.createDirectories(tempRoot);
            return Files.createTempFile(tempRoot, batchNo + "-", ".zip");
        }
        catch (IOException exception)
        {
            throw storageFailure("创建导出临时文件失败", exception);
        }
    }

    public StoredExport promoteExport(Path temporary, String batchNo)
    {
        safeBatchNo(batchNo);
        if (temporary == null || !Files.isRegularFile(temporary)
                || !temporary.toAbsolutePath().normalize().startsWith(tempRoot))
        {
            throw new ServiceException("导出临时文件无效");
        }
        Path relative = Paths.get("exports", batchNo + ".zip");
        Path target = resolveTarget(relative);
        try
        {
            Files.createDirectories(target.getParent());
            try
            {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, target);
            }
            return new StoredExport(portable(relative), target.getFileName()
                    .toString(), Files.size(target), sha256(target));
        }
        catch (IOException exception)
        {
            deleteQuietly(temporary);
            deleteQuietly(target);
            throw storageFailure("归档会计导出资料包失败", exception);
        }
    }

    public void deleteQuietly(String relativePath)
    {
        if (relativePath == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(resolveTarget(Paths.get(relativePath)));
        }
        catch (RuntimeException | IOException ignored)
        {
            // Best-effort cleanup after a failed database transaction.
        }
    }

    private String validateContent(String extension, byte[] bytes)
    {
        boolean valid = switch (extension)
        {
            case ".pdf" -> startsWith(bytes, "%PDF-".getBytes(
                    java.nio.charset.StandardCharsets.US_ASCII));
            case ".png" -> startsWith(bytes, new byte[] {
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
            case ".jpg", ".jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case ".ofd" -> isOfd(bytes);
            default -> false;
        };
        if (!valid)
        {
            throw new ServiceException("发票文件内容与格式不匹配");
        }
        return switch (extension)
        {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".ofd" -> "application/ofd";
            default -> "application/octet-stream";
        };
    }

    private boolean isOfd(byte[] bytes)
    {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K')
        {
            return false;
        }
        int entries = 0;
        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(bytes)))
        {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null && entries++ < 512)
            {
                String name = entry.getName().replace('\\', '/');
                if ("OFD.xml".equalsIgnoreCase(name)
                        || name.toLowerCase(Locale.ROOT).endsWith("/ofd.xml"))
                {
                    return true;
                }
            }
            return false;
        }
        catch (IOException exception)
        {
            return false;
        }
    }

    private String safeOriginalName(String value)
    {
        if (value == null || value.isBlank())
        {
            throw new ServiceException("发票文件名不能为空");
        }
        String name = Paths.get(value.replace('\\', '/'))
                .getFileName().toString();
        StringBuilder safe = new StringBuilder();
        name.codePoints().filter(code -> !Character.isISOControl(code))
                .forEach(code -> {
                    if (safe.length() + Character.charCount(code) <= 180)
                    {
                        safe.appendCodePoint(code);
                    }
                });
        if (safe.isEmpty() || safe.toString().contains(".."))
        {
            throw new ServiceException("发票文件名不合法");
        }
        return safe.toString();
    }

    private String extension(String filename)
    {
        int index = filename.lastIndexOf('.');
        String extension = index < 0 ? ""
                : filename.substring(index).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension))
        {
            throw new ServiceException("发票只支持 PDF、JPG、PNG 或 OFD");
        }
        return extension;
    }

    private Path resolveTarget(Path relative)
    {
        if (relative == null || relative.isAbsolute())
        {
            throw new ServiceException("私有文件路径无效");
        }
        Path target = root.resolve(relative).toAbsolutePath().normalize();
        if (target.equals(root) || !target.startsWith(root))
        {
            throw new ServiceException("私有文件路径越界");
        }
        return target;
    }

    private void safeBatchNo(String batchNo)
    {
        if (batchNo == null || !batchNo.matches("^BXDC[0-9A-Z]{12,32}$"))
        {
            throw new ServiceException("导出批次号无效");
        }
    }

    private static boolean startsWith(byte[] source, byte[] prefix)
    {
        if (source.length < prefix.length)
        {
            return false;
        }
        for (int index = 0; index < prefix.length; index++)
        {
            if (source[index] != prefix[index])
            {
                return false;
            }
        }
        return true;
    }

    private static String portable(Path value)
    {
        return value.toString().replace('\\', '/');
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sha256(Path path) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = Files.newInputStream(path);
                    DigestInputStream input = new DigestInputStream(raw, digest))
            {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // Best effort only.
        }
    }

    private ServiceException storageFailure(String message, Exception cause)
    {
        return new ServiceException(message).setDetailMessage(
                cause.getMessage());
    }

    public record StoredInvoice(String originalName, String storedName,
            String relativePath, String contentType, String extension,
            long size, String sha256) { }

    public record StoredExport(String relativePath, String archiveName,
            long size, String sha256) { }
}
