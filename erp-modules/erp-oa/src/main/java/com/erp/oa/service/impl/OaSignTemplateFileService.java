package com.erp.oa.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignTemplate;
import com.erp.oa.domain.vo.OaSignTemplateFile;
import com.erp.oa.mapper.OaSignTemplateMapper;

/** Resolves and converts signing templates without exposing their storage directory. */
@Service
public class OaSignTemplateFileService
{
    private static final Logger log = LoggerFactory.getLogger(OaSignTemplateFileService.class);
    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_PREVIEW_BYTES = 50L * 1024 * 1024;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final OaSignTemplateMapper templateMapper;
    private final OaOfficePdfConverter officePdfConverter;
    private final Path templateRoot;
    private final Path previewTempRoot;
    private final String templateUrlPrefix;

    public OaSignTemplateFileService(OaSignTemplateMapper templateMapper,
            OaOfficePdfConverter officePdfConverter,
            OaSignFileProperties properties,
            @Value("${file.path:./uploadPath}") String localFilePath,
            @Value("${file.prefix:/profile}") String localFilePrefix)
    {
        if (templateMapper == null || officePdfConverter == null || properties == null
                || properties.getStorage() == null)
        {
            throw new IllegalArgumentException("模板文件服务依赖不能为空");
        }
        if (StringUtils.isBlank(localFilePath))
        {
            throw new IllegalArgumentException("本地文件根目录不能为空");
        }
        this.templateMapper = templateMapper;
        this.officePdfConverter = officePdfConverter;
        this.templateRoot = Path.of(localFilePath).toAbsolutePath().normalize().resolve("sign-template");
        String configuredTempRoot = properties.getStorage().getTempPath();
        if (StringUtils.isBlank(configuredTempRoot))
        {
            throw new IllegalArgumentException("签约文件临时根目录不能为空");
        }
        this.previewTempRoot = Path.of(configuredTempRoot).toAbsolutePath().normalize()
                .resolve("template-preview");
        this.templateUrlPrefix = normalizePrefix(localFilePrefix) + "/sign-template/";
    }

    public OaSignTemplateFile download(Long templateId)
    {
        ValidatedTemplateSource source = loadValidatedSource(templateId);
        return new OaSignTemplateFile(source.content(), source.fileName(),
                contentType(source.extension()));
    }

    public OaSignTemplateFile preview(Long templateId)
    {
        ValidatedTemplateSource source = loadValidatedSource(templateId);
        Path stagingDirectory = null;
        try
        {
            Files.createDirectories(previewTempRoot);
            stagingDirectory = Files.createTempDirectory(previewTempRoot, "preview-")
                    .toAbsolutePath().normalize();
            Path stagedSource = stagingDirectory.resolve("template-source" + source.extension()).normalize();
            ensureWithin(stagedSource, stagingDirectory, "模板预览临时路径不合法");
            Files.write(stagedSource, source.content(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            Path convertedPdf = officePdfConverter.convert(stagedSource, stagingDirectory);
            if (convertedPdf == null)
            {
                throw new ServiceException("PDF转换结果不存在");
            }
            Path realStagingDirectory = stagingDirectory.toRealPath();
            Path realPdf = convertedPdf.toRealPath();
            ensureWithin(realPdf, realStagingDirectory, "PDF转换结果路径不合法");
            if (!Files.isRegularFile(realPdf, LinkOption.NOFOLLOW_LINKS))
            {
                throw new ServiceException("PDF转换结果不存在");
            }
            byte[] pdf = readBounded(realPdf, MAX_PREVIEW_BYTES, "模板预览PDF过大");
            return new OaSignTemplateFile(pdf, previewFileName(source.fileName()), PDF_CONTENT_TYPE);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new ServiceException("生成模板预览失败").setDetailMessage(e.getMessage());
        }
        finally
        {
            deleteDirectoryQuietly(stagingDirectory);
        }
    }

    private ValidatedTemplateSource loadValidatedSource(Long templateId)
    {
        if (templateId == null || templateId <= 0)
        {
            throw new ServiceException("模板编号不合法");
        }
        OaSignTemplate template = templateMapper.selectOaSignTemplateById(templateId);
        if (template == null)
        {
            throw new ServiceException("签约模板不存在");
        }
        OaSignTemplateType.Option type = OaSignTemplateType.require(template.getTemplateType());
        String extension = "." + type.getFileFormat().toLowerCase(Locale.ROOT);
        Path file = resolveTemplatePath(template.getFileUrl());
        if (!extension(file).equals(extension))
        {
            throw new ServiceException("模板文件类型与登记类型不一致");
        }
        if (template.getFileSize() == null || template.getFileSize() <= 0
                || template.getFileSize() > MAX_SOURCE_BYTES)
        {
            throw new ServiceException("模板文件大小未正确登记");
        }
        if (StringUtils.isBlank(template.getFileHash())
                || !template.getFileHash().matches("(?i)[0-9a-f]{64}"))
        {
            throw new ServiceException("模板文件校验值未正确登记");
        }
        byte[] content;
        try
        {
            content = readBounded(file, MAX_SOURCE_BYTES, "模板文件过大");
        }
        catch (IOException e)
        {
            throw new ServiceException("读取模板文件失败").setDetailMessage(e.getMessage());
        }
        if (content.length != template.getFileSize())
        {
            throw new ServiceException("模板文件大小校验不一致");
        }
        if (!sha256(content).equalsIgnoreCase(template.getFileHash()))
        {
            throw new ServiceException("模板文件校验值不一致");
        }
        return new ValidatedTemplateSource(content,
                safeFileName(template.getFileName(), template.getTemplateName(), extension), extension);
    }

    private Path resolveTemplatePath(String fileUrl)
    {
        if (StringUtils.isBlank(fileUrl) || !fileUrl.equals(fileUrl.trim())
                || fileUrl.contains("\\") || !fileUrl.startsWith(templateUrlPrefix))
        {
            throw new ServiceException("模板文件地址无效");
        }
        String relativeText = fileUrl.substring(templateUrlPrefix.length());
        if (relativeText.isBlank() || relativeText.startsWith("/"))
        {
            throw new ServiceException("模板文件地址无效");
        }
        try
        {
            Path relative = Path.of(relativeText);
            if (relative.isAbsolute() || containsTraversal(relative))
            {
                throw new ServiceException("模板文件地址无效");
            }
            Path candidate = templateRoot.resolve(relative).normalize();
            ensureWithin(candidate, templateRoot, "模板文件地址无效");
            Path realRoot = templateRoot.toRealPath();
            Path realCandidate = candidate.toRealPath();
            ensureWithin(realCandidate, realRoot, "模板文件地址无效");
            if (!Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS))
            {
                throw new ServiceException("模板文件不存在");
            }
            return realCandidate;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (InvalidPathException | IOException e)
        {
            throw new ServiceException("模板文件不存在或不可访问");
        }
    }

    private boolean containsTraversal(Path relative)
    {
        for (Path segment : relative)
        {
            if (".".equals(segment.toString()) || "..".equals(segment.toString()))
            {
                return true;
            }
        }
        return false;
    }

    private byte[] readBounded(Path file, long limit, String oversizeMessage) throws IOException
    {
        try (InputStream input = Files.newInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                total += read;
                if (total > limit)
                {
                    throw new ServiceException(oversizeMessage);
                }
                output.write(buffer, 0, read);
            }
            if (total == 0)
            {
                throw new ServiceException("模板文件内容为空");
            }
            return output.toByteArray();
        }
    }

    private String normalizePrefix(String prefix)
    {
        if (StringUtils.isBlank(prefix))
        {
            throw new IllegalArgumentException("文件地址前缀不能为空");
        }
        String normalized = prefix.trim().replaceAll("/+$", "");
        if (!normalized.startsWith("/") || normalized.contains("\\") || normalized.contains(".."))
        {
            throw new IllegalArgumentException("文件地址前缀不合法");
        }
        return normalized;
    }

    private String extension(Path file)
    {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index);
    }

    private String contentType(String extension)
    {
        return ".docx".equals(extension) ? DOCX_CONTENT_TYPE : XLSX_CONTENT_TYPE;
    }

    private String safeFileName(String storedName, String templateName, String extension)
    {
        String fileName = StringUtils.isNotBlank(storedName) ? storedName : templateName;
        if (StringUtils.isBlank(fileName))
        {
            fileName = "签约模板";
        }
        fileName = fileName.trim()
                .replaceAll("[\\\\/\\r\\n\\x00-\\x1f\\x7f]+", "_")
                .replaceAll("\\.{2,}", "_");
        if (fileName.length() > 180)
        {
            fileName = fileName.substring(0, 180);
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(extension))
        {
            fileName += extension;
        }
        return fileName;
    }

    private String previewFileName(String sourceFileName)
    {
        int extensionIndex = sourceFileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? sourceFileName.substring(0, extensionIndex) : sourceFileName;
        return baseName + ".pdf";
    }

    private String sha256(byte[] content)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private void ensureWithin(Path candidate, Path allowedRoot, String message)
    {
        if (!candidate.toAbsolutePath().normalize().startsWith(allowedRoot.toAbsolutePath().normalize()))
        {
            throw new ServiceException(message);
        }
    }

    private void deleteDirectoryQuietly(Path directory)
    {
        if (directory == null || !Files.exists(directory))
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
        catch (IOException e)
        {
            log.warn("清理模板预览临时目录失败", e);
        }
    }

    private record ValidatedTemplateSource(byte[] content, String fileName, String extension)
    {
    }
}
