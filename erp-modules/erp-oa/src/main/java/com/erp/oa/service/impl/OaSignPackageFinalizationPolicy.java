package com.erp.oa.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;

/**
 * Defines canonical final-contract request, replay and frozen-decision rules.
 */
final class OaSignPackageFinalizationPolicy
{
    private static final String FINAL_CONFIRMATION_TEXT =
            "本人已阅读并确认最终合同中的公司及印章信息";

    void validateFinalConfirmationRequest(OaSignFinalConfirmRequest request)
    {
        if (request == null
                || !FINAL_CONFIRMATION_TEXT.equals(request.getConfirmationText()))
        {
            throw new ServiceException("请确认输入：" + FINAL_CONFIRMATION_TEXT);
        }
        if (StringUtils.isBlank(request.getRequestId())
                || StringUtils.isBlank(request.getFinalDocumentVersion())
                || StringUtils.isBlank(request.getDocumentRootHash()))
        {
            throw new ServiceException("最终合同确认信息不完整");
        }
    }

    void assertSameFinalConfirmationReplay(OaSignFinalConfirmation existing,
            OaSignPackage signPackage, OaSignFinalConfirmRequest request)
    {
        if (!Objects.equals(existing.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(existing.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(existing.getFinalDocumentVersion(),
                        request.getFinalDocumentVersion())
                || !sameHash(existing.getDocumentRootHash(),
                        request.getDocumentRootHash())
                || !Objects.equals(existing.getConfirmationText(),
                        request.getConfirmationText())
                || !"LOGIN_TOKEN".equals(existing.getIdentityMethod()))
        {
            throw new ServiceException("最终合同确认请求编号已用于不同内容");
        }
    }

    void validateFinalizeSigningSequence(OaSignPackage signPackage,
            OaSignPackageFinalizeRequest request)
    {
        if (StringUtils.isNotBlank(request.getSigningSequence())
                && !Objects.equals(
                        OaSignSigningSequence.normalize(request.getSigningSequence()),
                        signPackage.getSigningSequence()))
        {
            throw new ServiceException("签署顺序已变化，请重新预览");
        }
        if (!OaSignSigningSequence.SIGNATURE_FIRST.equals(
                signPackage.getSigningSequence()))
        {
            throw new ServiceException("仅先确认事实并留签名流程可以补充公司与印章");
        }
    }

    void validateFinalizeWriteRequest(OaSignPackageFinalizeRequest request,
            String requestId)
    {
        if (StringUtils.isBlank(requestId) || requestId.length() > 64
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 0
                || request.getExpectedTaskVersion() == null
                || request.getExpectedTaskVersion() < 0)
        {
            throw new ServiceException("最终合同生成请求缺少请求编号或预期版本");
        }
    }

    String finalizeRequestPayloadHash(Long packageId,
            OaSignPackageFinalizeRequest request)
    {
        String signingSequence = StringUtils.isBlank(request.getSigningSequence())
                ? OaSignSigningSequence.SIGNATURE_FIRST
                : OaSignSigningSequence.normalize(request.getSigningSequence());
        StringBuilder payload = new StringBuilder(192);
        payload.append("packageId=").append(packageId).append('\n')
                .append("legalEntityId=").append(request.getLegalEntityId()).append('\n')
                .append("sealId=").append(request.getSealId()).append('\n')
                .append("correctionReason=")
                .append(value(StringUtils.trim(request.getCorrectionReason()))).append('\n')
                .append("expectedVersion=").append(request.getExpectedVersion()).append('\n')
                .append("expectedTaskVersion=")
                .append(request.getExpectedTaskVersion()).append('\n')
                .append("signingSequence=").append(signingSequence).append('\n');
        return sha256(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    void assertSameFinalizeRequestReplay(OaSignEvent existingRequest,
            Long packageId, String requestPayloadHash, Long currentUserId)
    {
        if (!Objects.equals(existingRequest.getPackageId(), packageId)
                || !Objects.equals(existingRequest.getOperatorUserId(), currentUserId)
                || !"HR".equals(existingRequest.getOperatorRole())
                || !sameHash(existingRequest.getDocumentHash(), requestPayloadHash))
        {
            throw new ServiceException("最终合同生成请求编号已用于不同内容");
        }
    }

    OaSignTaskStatus existingReplayTaskStatus(OaSignPackage signPackage)
    {
        if (isPreparedFinalCandidate(signPackage))
        {
            return OaSignTaskStatus.PENDING_COMPANY;
        }
        if (OaSignPackageStatus.PENDING_FINAL_CONFIRM.equals(signPackage.getStatus()))
        {
            return OaSignTaskStatus.PENDING_FINAL_CONFIRM;
        }
        if (OaSignPackageStatus.SIGNED.equals(signPackage.getStatus()))
        {
            return OaSignTaskStatus.SIGNED;
        }
        throw new ServiceException("最终合同生成记录与签约包状态不一致");
    }

    void validateExistingReplayDecision(OaSignPackage signPackage,
            OaSignPackageFinalizeRequest request)
    {
        Long requestedLegalEntityId = request.getLegalEntityId();
        if (requestedLegalEntityId == null)
        {
            requestedLegalEntityId = "MANUAL".equalsIgnoreCase(
                    signPackage.getLegalEntityResolveMode())
                            ? null : signPackage.getLegalEntityIdSnapshot();
        }
        if (!Objects.equals(requestedLegalEntityId,
                signPackage.getLegalEntityIdSnapshot())
                || !Objects.equals(request.getSealId(),
                        signPackage.getSealIdSnapshot()))
        {
            throw new ServiceException(
                    "最终合同已按其他公司或印章生成，不能复用当前请求");
        }
    }

    boolean isPreparedFinalCandidate(OaSignPackage signPackage)
    {
        return signPackage != null
                && OaSignPackageStatus.PENDING_COMPANY.equals(signPackage.getStatus())
                && "PREPARED_NOT_SENT".equals(
                        signPackage.getFinalConfirmationStatus())
                && StringUtils.isNotBlank(signPackage.getFinalDocumentVersion())
                && StringUtils.isNotBlank(signPackage.getFinalDocumentRootHash())
                && signPackage.getFinalGeneratedTime() != null;
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
