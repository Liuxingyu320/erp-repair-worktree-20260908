package com.erp.oa.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.vo.SignedPdfResult;

/**
 * Verifies immutable sign-package files and builds deterministic document roots.
 *
 * <p>This boundary owns cryptographic comparison and physical-file verification.
 * It does not persist evidence, mutate package state, or manage transactions.</p>
 */
final class OaSignPackageFileIntegrity
{
    private final OaSignDocumentService documentService;
    private final OaSignedPdfService signedPdfService;

    OaSignPackageFileIntegrity(OaSignDocumentService documentService,
            OaSignedPdfService signedPdfService)
    {
        this.documentService = documentService;
        this.signedPdfService = signedPdfService;
    }

    boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    String sha256(byte[] bytes)
    {
        try
        {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("文件校验算法不可用", exception);
        }
    }

    String sha256(Path path)
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
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException exception)
        {
            throw new ServiceException("读取签约文件校验值失败")
                    .setDetailMessage(exception.getMessage());
        }
    }

    byte[] readAndValidateManagedFile(String fileUrl, String expectedHash,
            String mismatchMessage, String readFailureMessage)
    {
        try
        {
            byte[] bytes = Files.readAllBytes(
                    documentService.resolveGeneratedSignPackageFile(fileUrl));
            if (!sameHash(expectedHash, sha256(bytes)))
            {
                throw new ServiceException(mismatchMessage);
            }
            return bytes;
        }
        catch (ServiceException exception)
        {
            throw exception;
        }
        catch (IOException exception)
        {
            throw new ServiceException(readFailureMessage)
                    .setDetailMessage(exception.getMessage());
        }
    }

    byte[] readAndValidateInitialSignature(
            OaSignPackageDocument signatureSource)
    {
        return readAndValidateManagedFile(
                signatureSource.getSignatureFileUrl(),
                signatureSource.getSignatureHash(),
                "员工首次签名图片校验不一致",
                "读取员工首次签名图片失败");
    }

    byte[] readFinalSignatureBytes(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        if (StringUtils.isNotBlank(document.getSignatureFileUrl()))
        {
            return readAndValidateInitialSignature(document);
        }
        if (StringUtils.isBlank(signPackage.getSignatureSampleFileUrl())
                || StringUtils.isBlank(signPackage.getSignatureSampleHash()))
        {
            throw new ServiceException("最终归档缺少本任务手写签名样本");
        }
        return readAndValidateManagedFile(
                signPackage.getSignatureSampleFileUrl(),
                signPackage.getSignatureSampleHash(),
                "本任务手写签名样本校验不一致",
                "读取本任务手写签名样本失败");
    }

    String finalDocumentRootHash(Map<Long, SignedPdfResult> results)
    {
        Map<Long, String> hashes = new LinkedHashMap<>();
        results.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> hashes.put(
                        entry.getKey(), entry.getValue().getContentPdfHash()));
        return finalDocumentRootHashFromHashes(hashes);
    }

    String archiveDocumentRootHash(Map<Long, SignedPdfResult> results)
    {
        Map<Long, String> hashes = new LinkedHashMap<>();
        results.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> hashes.put(
                        entry.getKey(), entry.getValue().getSignedPdfHash()));
        return finalDocumentRootHashFromHashes(hashes);
    }

    String finalDocumentRootHashFromHashes(Map<Long, String> hashes)
    {
        StringBuilder payload = new StringBuilder();
        hashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> payload.append(entry.getKey())
                        .append(':').append(entry.getValue()).append('\n'));
        return sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    Map<Long, String> validateFinalDocuments(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents)
    {
        if (documents == null || documents.isEmpty())
        {
            throw new ServiceException("最终合同文件不存在");
        }
        Map<Long, String> hashes = new LinkedHashMap<>();
        for (OaSignPackageDocument document : documents)
        {
            if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                    document.getFinalDocumentVersion())
                    || StringUtils.isBlank(document.getFinalPdfUrl())
                    || StringUtils.isBlank(document.getFinalPdfHash())
                    || StringUtils.isBlank(document.getFinalContentHash()))
            {
                throw new ServiceException("最终合同文件不完整，请联系经办人");
            }
            Path path = documentService.resolveGeneratedSignPackageFile(
                    document.getFinalPdfUrl());
            String diskHash = sha256(path);
            if (!sameHash(diskHash, document.getFinalPdfHash()))
            {
                throw new ServiceException("最终合同文件校验不一致，请联系经办人");
            }
            signedPdfService.validatePendingFinalContentHash(
                    path, document.getFinalPdfHash(),
                    document.getFinalContentHash());
            hashes.put(document.getDocumentId(),
                    document.getFinalContentHash());
        }
        return hashes;
    }
}
