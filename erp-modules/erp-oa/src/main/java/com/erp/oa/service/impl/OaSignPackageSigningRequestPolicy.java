package com.erp.oa.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.dto.OaSignDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;

/**
 * Validates employee signing requests and produces their canonical replay fingerprint.
 */
final class OaSignPackageSigningRequestPolicy
{
    private static final String CONFIRMATION_TEXT = "本人确认签署本签约包";
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";
    private static final int MAX_SIGNATURE_BYTES = 5 * 1024 * 1024;

    void validate(OaSignPackage signPackage, OaSignPackageSignRequest request)
    {
        if (request == null || !CONFIRMATION_TEXT.equals(request.getSignConfirmText()))
        {
            throw new ServiceException("请确认输入：本人确认签署本签约包");
        }
        if (StringUtils.isBlank(request.getDocumentVersion())
                || StringUtils.isBlank(request.getSignatureDataUrl())
                || StringUtils.isBlank(request.getRequestId())
                || request.getRequestId().length() > 64)
        {
            throw new ServiceException("签署请求缺少文档版本、文件校验值、签名或请求编号");
        }
        if (OaSignSigningSequence.COMPANY_FIRST.equals(signPackage.getSigningSequence()))
        {
            if (StringUtils.isBlank(request.getFinalDocumentVersion())
                    || !isSha256Hash(request.getFinalDocumentRootHash())
                    || request.getFinalDocumentHashes() == null
                    || request.getFinalDocumentHashes().isEmpty())
            {
                throw new ServiceException("签署请求缺少员工已查看的最终合同版本、集合校验值或逐文件校验值");
            }
            return;
        }
        if (request.getDocumentHashes() == null || request.getDocumentHashes().isEmpty())
        {
            throw new ServiceException("签署请求缺少待签文件校验值");
        }
    }

    String payloadHash(OaSignPackageSignRequest request, byte[] signaturePng)
    {
        StringBuilder payload = new StringBuilder();
        payload.append("documentVersion=").append(value(request.getDocumentVersion()))
                .append('\n');
        payload.append("finalDocumentVersion=")
                .append(value(request.getFinalDocumentVersion())).append('\n');
        payload.append("finalDocumentRootHash=")
                .append(value(request.getFinalDocumentRootHash()).toLowerCase(Locale.ROOT))
                .append('\n');
        payload.append("confirmation=").append(value(request.getSignConfirmText()))
                .append('\n');
        payload.append("signatureSha256=").append(sha256(signaturePng)).append('\n');
        (request.getDocumentHashes() == null
                ? List.<OaSignDocumentHashRequest>of() : request.getDocumentHashes()).stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> compareDocumentIds(
                        left.getDocumentId(), right.getDocumentId()))
                .forEach(item -> payload.append("document=")
                        .append(item.getDocumentId()).append(':')
                        .append(value(item.getReviewPdfHash()).toLowerCase(Locale.ROOT))
                        .append('\n'));
        (request.getFinalDocumentHashes() == null
                ? List.<OaSignFinalDocumentHashRequest>of()
                : request.getFinalDocumentHashes()).stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> compareDocumentIds(
                        left.getDocumentId(), right.getDocumentId()))
                .forEach(item -> payload.append("finalDocument=")
                        .append(item.getDocumentId()).append(':')
                        .append(value(item.getFinalPdfHash()).toLowerCase(Locale.ROOT))
                        .append('\n'));
        return sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    void assertSameReplay(OaSignEvent existingRequest, Long packageId,
            String requestPayloadHash)
    {
        if (!Objects.equals(existingRequest.getPackageId(), packageId))
        {
            throw new ServiceException("签署请求编号已被其他签约包使用");
        }
        if (!sameHash(existingRequest.getDocumentHash(), requestPayloadHash))
        {
            throw new ServiceException("同一签署请求编号不能提交不同载荷");
        }
    }

    Map<Long, String> requestedReviewHashes(
            List<OaSignDocumentHashRequest> hashRequests, int requiredCount)
    {
        if (hashRequests == null || hashRequests.size() != requiredCount)
        {
            throw new ServiceException("必签文件集合不完整，请刷新后重新阅读");
        }
        Map<Long, String> hashes = new HashMap<>();
        for (OaSignDocumentHashRequest request : hashRequests)
        {
            if (request == null || request.getDocumentId() == null
                    || StringUtils.isBlank(request.getReviewPdfHash())
                    || hashes.putIfAbsent(
                            request.getDocumentId(), request.getReviewPdfHash()) != null)
            {
                throw new ServiceException("必签文件集合不完整，请刷新后重新阅读");
            }
        }
        return hashes;
    }

    Map<Long, String> requestedFinalHashes(
            List<OaSignFinalDocumentHashRequest> hashRequests, int requiredCount)
    {
        if (hashRequests == null || hashRequests.size() != requiredCount)
        {
            throw new ServiceException("最终合同文件集合不完整，请刷新后逐份重新打开");
        }
        Map<Long, String> hashes = new HashMap<>();
        for (OaSignFinalDocumentHashRequest request : hashRequests)
        {
            if (request == null || request.getDocumentId() == null
                    || !isSha256Hash(request.getFinalPdfHash())
                    || hashes.putIfAbsent(
                            request.getDocumentId(), request.getFinalPdfHash()) != null)
            {
                throw new ServiceException("最终合同文件集合不完整，请刷新后逐份重新打开");
            }
        }
        return hashes;
    }

    byte[] decodeSignaturePng(String signatureDataUrl)
    {
        if (StringUtils.isBlank(signatureDataUrl)
                || !signatureDataUrl.startsWith(PNG_DATA_URL_PREFIX))
        {
            throw new ServiceException("签名图片必须为无损图片格式");
        }
        try
        {
            byte[] bytes = Base64.getDecoder().decode(
                    signatureDataUrl.substring(PNG_DATA_URL_PREFIX.length()));
            if (bytes.length == 0 || bytes.length > MAX_SIGNATURE_BYTES)
            {
                throw new ServiceException("签名图片内容不合法");
            }
            return bytes;
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("签名图片编码无效");
        }
    }

    boolean isSha256Hash(String value)
    {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
    }

    private int compareDocumentIds(Long leftId, Long rightId)
    {
        if (leftId == null)
        {
            return rightId == null ? 0 : -1;
        }
        return rightId == null ? 1 : leftId.compareTo(rightId);
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("文件校验算法不可用", e);
        }
    }

    private String value(String value)
    {
        return value == null ? "" : value;
    }
}
