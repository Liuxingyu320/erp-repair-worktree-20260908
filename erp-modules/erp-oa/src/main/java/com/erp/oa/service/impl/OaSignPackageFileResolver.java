package com.erp.oa.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.OaSignPackageFile;

/**
 * Resolves package artifacts and verifies the integrity evidence required by each file stage.
 */
final class OaSignPackageFileResolver
{
    private final OaSignDocumentService documentService;

    OaSignPackageFileResolver(OaSignDocumentService documentService)
    {
        this.documentService = documentService;
    }

    OaSignPackageFile source(OaSignPackageDocument document)
    {
        String fileUrl = StringUtils.isNotBlank(document.getGeneratedPdfUrl())
                ? document.getGeneratedPdfUrl() : document.getGeneratedFileUrl();
        if (StringUtils.isBlank(fileUrl))
        {
            throw new ServiceException("签约包文件尚未生成");
        }
        return resolve(fileUrl, document.getDocumentName());
    }

    OaSignPackageFile certificate(OaSignPackageDocument document)
    {
        if (!"Y".equalsIgnoreCase(document.getSigned())
                || StringUtils.isBlank(document.getCertificateFileUrl()))
        {
            throw new ServiceException("签署证明尚未生成");
        }
        return resolve(document.getCertificateFileUrl(), document.getDocumentName() + "-签署证明");
    }

    OaSignPackageFile signed(OaSignPackageDocument document)
    {
        if (!"Y".equalsIgnoreCase(document.getSigned())
                || StringUtils.isBlank(document.getSignedPdfUrl()))
        {
            throw new ServiceException("已签文件尚未生成");
        }
        return resolve(document.getSignedPdfUrl(), document.getDocumentName() + "-已签");
    }

    OaSignPackageFile finalCandidate(OaSignPackageDocument document)
    {
        if (StringUtils.isBlank(document.getFinalPdfUrl())
                || StringUtils.isBlank(document.getFinalPdfHash()))
        {
            throw new ServiceException("最终合同尚未生成");
        }
        Path path = documentService.resolveGeneratedSignPackageFile(document.getFinalPdfUrl());
        String actualHash = sha256(path);
        if (!sameHash(document.getFinalPdfHash(), actualHash))
        {
            throw new ServiceException("最终合同文件校验不一致");
        }
        return toFile(path, document.getDocumentName() + "-最终合同", actualHash);
    }

    OaSignPackageFile finalArchive(OaSignPackageDocument document)
    {
        if (StringUtils.isNotBlank(document.getFinalArchivePdfUrl())
                && StringUtils.isNotBlank(document.getFinalArchivePdfHash()))
        {
            Path path = documentService.resolveGeneratedSignPackageFile(
                    document.getFinalArchivePdfUrl());
            String actualHash = sha256(path);
            if (!sameHash(document.getFinalArchivePdfHash(), actualHash))
            {
                throw new ServiceException("最终归档文件校验不一致");
            }
            return toFile(path, document.getDocumentName() + "-最终归档", actualHash);
        }
        // Compatibility for historical signed packages created before archive evidence existed.
        return finalCandidate(document);
    }

    /**
     * Resolves only the immutable post-confirmation archive.  Formal export must never
     * substitute the pending candidate for a historical archive whose evidence is absent.
     */
    OaSignPackageFile finalArchiveOnly(OaSignPackageDocument document)
    {
        if (StringUtils.isBlank(document.getFinalArchivePdfUrl())
                || StringUtils.isBlank(document.getFinalArchivePdfHash()))
        {
            throw new ServiceException("该历史合同缺少签章位置或证据，暂不能恢复");
        }
        Path path = documentService.resolveGeneratedSignPackageFile(
                document.getFinalArchivePdfUrl());
        String actualHash = sha256(path);
        if (!sameHash(document.getFinalArchivePdfHash(), actualHash))
        {
            throw new ServiceException("最终归档文件校验不一致");
        }
        return toFile(path, document.getDocumentName() + "-最终归档", actualHash);
    }

    private OaSignPackageFile resolve(String fileUrl, String displayName)
    {
        return toFile(documentService.resolveGeneratedSignPackageFile(fileUrl), displayName);
    }

    private OaSignPackageFile toFile(Path path, String displayName)
    {
        return toFile(path, displayName, null);
    }

    private OaSignPackageFile toFile(Path path, String displayName, String fileHash)
    {
        String lowerName = path.getFileName().toString().toLowerCase();
        String extension = extension(lowerName);
        return new OaSignPackageFile(path, safeFileName(displayName, extension),
                contentType(extension), fileHash);
    }

    private String extension(String lowerName)
    {
        int index = lowerName.lastIndexOf('.');
        return index > -1 ? lowerName.substring(index) : "";
    }

    private String safeFileName(String displayName, String extension)
    {
        String baseName = StringUtils.isNotBlank(displayName) ? displayName : "签约文件";
        baseName = baseName.replaceAll("[\\\\/\\r\\n]+", "_");
        return baseName.endsWith(extension) ? baseName : baseName + extension;
    }

    private String contentType(String extension)
    {
        if (".pdf".equals(extension))
        {
            return "application/pdf";
        }
        if (".docx".equals(extension))
        {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (".xlsx".equals(extension))
        {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/octet-stream";
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String sha256(Path path)
    {
        try (InputStream input = Files.newInputStream(path))
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            throw new ServiceException("读取签约文件校验值失败").setDetailMessage(e.getMessage());
        }
    }
}
