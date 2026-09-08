package com.erp.oa.service.impl;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.domain.vo.StagedSignFile;

@Service
public class OaSignFileStorageService
{
    private static final Pattern DOCUMENT_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^\\.[A-Za-z0-9]{1,10}$");

    private final Path root;
    private final Path tempRoot;
    private final String publicPrefix;

    public OaSignFileStorageService(OaSignFileProperties properties)
    {
        if (properties == null || properties.getStorage() == null)
        {
            throw new IllegalArgumentException("签约文件存储配置不能为空");
        }
        this.root = Paths.get(properties.getStorage().getRootPath()).toAbsolutePath().normalize();
        this.tempRoot = Paths.get(properties.getStorage().getTempPath()).toAbsolutePath().normalize();
        String prefix = properties.getStorage().getPublicPrefix();
        this.publicPrefix = prefix == null || prefix.isBlank()
                ? "/profile/private/sign-package"
                : prefix.replaceAll("/+$", "");
    }

    public StagedSignFile stage(Long taskId, Long packageId, String documentVersion, String filename, byte[] bytes)
    {
        validatePackageId(packageId);
        validateDocumentVersion(documentVersion);
        String extension = safeExtension(filename);
        if (bytes == null || bytes.length == 0)
        {
            throw new ServiceException("签约文件内容不能为空");
        }

        String taskSegment = taskId == null ? "task-none" : "task-" + taskId;
        String packageSegment = "package-" + packageId;
        String serverFilename = UUID.randomUUID().toString().replace("-", "") + extension;
        Path relativePath = Paths.get(taskSegment, packageSegment, documentVersion, serverFilename);
        String relative = toPortablePath(relativePath);
        Path stagingDirectory = tempRoot.resolve(Paths.get(taskSegment, packageSegment, documentVersion,
                "stage-" + UUID.randomUUID().toString().replace("-", ""))).normalize();
        ensureWithin(stagingDirectory, tempRoot, "非法临时文件路径");
        Path tempPath = stagingDirectory.resolve(serverFilename).normalize();

        try
        {
            Files.createDirectories(stagingDirectory);
            Files.write(tempPath, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StagedSignFile(stagingDirectory, tempPath, relative, serverFilename,
                    sha256(bytes), bytes.length);
        }
        catch (IOException e)
        {
            deleteDirectoryQuietly(stagingDirectory);
            throw storageException("签约文件临时写入失败", e);
        }
    }

    public StagedSignFile promote(StagedSignFile stagedFile)
    {
        if (stagedFile == null)
        {
            throw new ServiceException("待归档文件不能为空");
        }
        Path destination = archiveTarget(stagedFile.getArchiveRelativePath());
        try
        {
            if (Files.exists(destination))
            {
                throw new ServiceException("同一文档版本的归档文件已存在，不可覆盖");
            }
            if (!Files.isRegularFile(stagedFile.getTempPath()))
            {
                throw new ServiceException("待归档临时文件不存在");
            }
            if (Files.size(stagedFile.getTempPath()) != stagedFile.getFileSize()
                    || !sha256(Files.readAllBytes(stagedFile.getTempPath())).equals(stagedFile.getFileHash()))
            {
                throw new ServiceException("待归档临时文件已发生变化");
            }
            Files.createDirectories(destination.getParent());
            Files.move(stagedFile.getTempPath(), destination, StandardCopyOption.ATOMIC_MOVE);
            stagedFile.markPromoted(destination, publicPrefix + "/" + stagedFile.getArchiveRelativePath());
            deleteDirectoryIfEmpty(stagedFile.getStagingDirectory());
            return stagedFile;
        }
        catch (FileAlreadyExistsException e)
        {
            throw storageException("同一文档版本的归档文件已存在，不可覆盖", e);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw storageException("签约文件原子归档失败", e);
        }
    }

    public Path resolveAuthorizedFile(String archiveRelativePath)
    {
        Path candidate = archiveTarget(archiveRelativePath);
        if (!Files.isRegularFile(candidate))
        {
            throw new ServiceException("签约文件不存在");
        }
        return candidate;
    }

    public boolean isManagedPublicUrl(String fileUrl)
    {
        return fileUrl != null && fileUrl.startsWith(publicPrefix + "/");
    }

    public Path resolveAuthorizedPublicUrl(String fileUrl)
    {
        if (!isManagedPublicUrl(fileUrl))
        {
            throw new ServiceException("签约文件地址无效");
        }
        return resolveAuthorizedFile(fileUrl.substring(publicPrefix.length() + 1));
    }

    /**
     * 删除未完成签约任务的受管文件。只允许触及精确的任务/签约包目录，
     * 历史存储中使用 task-none 的同包目录也会被精确清理。
     */
    public void deleteManagedPackageFiles(Long taskId, Long packageId, Collection<String> fileReferences)
    {
        validateTaskAndPackageIds(taskId, packageId);
        validateManagedPackageReferences(taskId, packageId, fileReferences);
        Path taskPackage = Paths.get("task-" + taskId, "package-" + packageId);
        Path legacyPackage = Paths.get("task-none", "package-" + packageId);
        try
        {
            deleteExactPackageDirectory(root, taskPackage);
            deleteExactPackageDirectory(root, legacyPackage);
            deleteExactPackageDirectory(tempRoot, taskPackage);
            deleteExactPackageDirectory(tempRoot, legacyPackage);
        }
        catch (IOException e)
        {
            throw storageException("签约任务文件清理失败", e);
        }
    }

    /** 在数据库删除前预先验证所有受管文件引用均属于目标签约包。 */
    public void validateManagedPackageReferences(Long taskId, Long packageId,
            Collection<String> fileReferences)
    {
        validateTaskAndPackageIds(taskId, packageId);
        if (fileReferences == null)
        {
            return;
        }
        Path taskPackage = Paths.get("task-" + taskId, "package-" + packageId);
        Path legacyPackage = Paths.get("task-none", "package-" + packageId);
        for (String reference : fileReferences)
        {
            String relativeReference = managedRelativeReference(reference);
            if (relativeReference == null)
            {
                continue;
            }
            Path destination = archiveTarget(relativeReference);
            Path relative = root.relativize(destination);
            if (!relative.startsWith(taskPackage) && !relative.startsWith(legacyPackage))
            {
                throw new ServiceException("签约文件引用与目标任务不一致，拒绝删除");
            }
        }
    }

    void discardUncommitted(String archiveRelativePath, String expectedHash)
    {
        Path archivePath = archiveTarget(archiveRelativePath);
        if (!Files.exists(archivePath))
        {
            return;
        }
        if (!Files.isRegularFile(archivePath))
        {
            throw new ServiceException("待回滚归档文件不合法");
        }
        try
        {
            String actualHash = sha256(Files.readAllBytes(archivePath));
            if (expectedHash == null || !actualHash.equalsIgnoreCase(expectedHash))
            {
                throw new ServiceException("待回滚归档文件校验值不一致，拒绝删除");
            }
            Files.delete(archivePath);
            deleteEmptyArchiveParents(archivePath.getParent());
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw storageException("回滚未提交签约文件失败", e);
        }
    }

    void discardArchivedEvidence(String fileReference, String expectedHash)
    {
        String archiveRelativePath = fileReference;
        if (isManagedPublicUrl(fileReference))
        {
            archiveRelativePath = fileReference.substring(publicPrefix.length() + 1);
        }
        discardUncommitted(archiveRelativePath, expectedHash);
    }

    public void cleanup(StagedSignFile stagedFile)
    {
        if (stagedFile == null || stagedFile.getStagingDirectory() == null)
        {
            return;
        }
        Path stagingDirectory = stagedFile.getStagingDirectory().toAbsolutePath().normalize();
        ensureWithin(stagingDirectory, tempRoot, "非法临时文件路径");
        if (stagingDirectory.equals(tempRoot))
        {
            throw new ServiceException("禁止清理签约临时根目录");
        }
        try
        {
            deleteDirectory(stagingDirectory);
        }
        catch (IOException e)
        {
            throw storageException("清理签约临时文件失败", e);
        }
    }

    private Path archiveTarget(String archiveRelativePath)
    {
        if (archiveRelativePath == null || archiveRelativePath.isBlank()
                || archiveRelativePath.contains("\\"))
        {
            throw new ServiceException("非法文件路径");
        }
        Path relative = Paths.get(archiveRelativePath);
        if (relative.isAbsolute())
        {
            throw new ServiceException("非法文件路径");
        }
        Path destination = root.resolve(relative).normalize();
        ensureWithin(destination, root, "非法文件路径");
        return destination;
    }

    private String managedRelativeReference(String fileReference)
    {
        if (fileReference == null || fileReference.isBlank())
        {
            return null;
        }
        if (isManagedPublicUrl(fileReference))
        {
            return fileReference.substring(publicPrefix.length() + 1);
        }
        // 证据表的历史数据可以保存归档相对路径。其他绝对URL不属于本服务受管范围。
        if (!fileReference.startsWith("/") && !fileReference.contains("://"))
        {
            return fileReference;
        }
        return null;
    }

    private void validateTaskAndPackageIds(Long taskId, Long packageId)
    {
        if (taskId == null || taskId <= 0 || packageId == null || packageId <= 0)
        {
            throw new ServiceException("签约任务或签约包编号不合法");
        }
    }

    private void deleteExactPackageDirectory(Path allowedRoot, Path relativeDirectory) throws IOException
    {
        Path directory = allowedRoot.resolve(relativeDirectory).toAbsolutePath().normalize();
        ensureWithin(directory, allowedRoot, "非法签约包清理路径");
        if (directory.equals(allowedRoot))
        {
            throw new ServiceException("禁止清理签约存储根目录");
        }
        deleteDirectory(directory);
        deleteEmptyParents(directory.getParent(), allowedRoot);
    }

    private void deleteEmptyParents(Path directory, Path allowedRoot) throws IOException
    {
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        Path current = directory;
        while (current != null && !current.equals(normalizedRoot) && current.startsWith(normalizedRoot))
        {
            if (!Files.isDirectory(current))
            {
                current = current.getParent();
                continue;
            }
            try (Stream<Path> entries = Files.list(current))
            {
                if (entries.findAny().isPresent())
                {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private void validatePackageId(Long packageId)
    {
        if (packageId == null || packageId <= 0)
        {
            throw new ServiceException("签约包编号不合法");
        }
    }

    private void validateDocumentVersion(String documentVersion)
    {
        if (documentVersion == null || documentVersion.contains("..")
                || !DOCUMENT_VERSION_PATTERN.matcher(documentVersion).matches())
        {
            throw new ServiceException("文档版本不合法");
        }
    }

    private String safeExtension(String filename)
    {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..") || Paths.get(filename).isAbsolute())
        {
            throw new ServiceException("文件名不合法");
        }
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0)
        {
            return "";
        }
        String extension = filename.substring(extensionIndex).toLowerCase();
        if (!EXTENSION_PATTERN.matcher(extension).matches())
        {
            throw new ServiceException("文件名扩展名不合法");
        }
        return extension;
    }

    private void ensureWithin(Path candidate, Path allowedRoot, String message)
    {
        if (!candidate.toAbsolutePath().normalize().startsWith(allowedRoot))
        {
            throw new ServiceException(message);
        }
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private String toPortablePath(Path path)
    {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private ServiceException storageException(String message, Exception cause)
    {
        return new ServiceException(message).setDetailMessage(cause == null ? null : cause.getMessage());
    }

    private void deleteDirectory(Path directory) throws IOException
    {
        if (!Files.exists(directory))
        {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory))
        {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(path);
            }
        }
    }

    private void deleteDirectoryQuietly(Path directory)
    {
        try
        {
            deleteDirectory(directory);
        }
        catch (IOException ignored)
        {
            // Preserve the original write error; a later temp-directory sweep can retry cleanup.
        }
    }

    private void deleteDirectoryIfEmpty(Path directory) throws IOException
    {
        if (Files.isDirectory(directory))
        {
            try (Stream<Path> entries = Files.list(directory))
            {
                if (entries.findAny().isEmpty())
                {
                    Files.deleteIfExists(directory);
                }
            }
        }
    }

    private void deleteEmptyArchiveParents(Path directory) throws IOException
    {
        Path current = directory;
        while (current != null && !current.equals(root) && current.startsWith(root))
        {
            try (Stream<Path> entries = Files.list(current))
            {
                if (entries.findAny().isPresent())
                {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }
}
