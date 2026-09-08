package com.erp.oa.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

/**
 * Validates the one package-scoped signature sample used by staged
 * signature-first onboarding.
 *
 * <p>This policy owns input shape, frozen identity and persisted evidence
 * relationships. Callers remain responsible for loading rows and validating
 * the physical managed file.</p>
 */
final class OaSignPackageStagedEvidencePolicy
{
    void validateCandidateSample(Long dataRequestId, String signatureRequestId,
            byte[] signatureSampleBytes, String signatureSampleHash,
            Date signatureSampleTime)
    {
        if (StringUtils.isBlank(signatureRequestId) || dataRequestId == null
                || dataRequestId <= 0 || signatureSampleTime == null
                || !validSignatureBytes(signatureSampleBytes)
                || StringUtils.isBlank(signatureSampleHash)
                || !sameHash(signatureSampleHash, sha256(signatureSampleBytes)))
        {
            throw new ServiceException("任务级手写签名样本不完整或校验不一致");
        }
    }

    void requireStagedSignatureBinding(OaSignPackage signPackage, OaSignTask task,
            Long dataRequestId, String signatureRequestId, byte[] signatureSampleBytes,
            String signatureSampleHash, Date signatureSampleTime)
    {
        if (signPackage == null || task == null
                || !Objects.equals(signPackage.getTaskId(), task.getTaskId())
                || !Objects.equals(task.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(task.getEmployeeId(), signPackage.getEmployeeId())
                || !Objects.equals(task.getPlanVersionId(), signPackage.getPlanVersionId()))
        {
            throw new ServiceException("签约任务与签约包关联已变化");
        }
        if (!OaSignSigningSequence.SIGNATURE_FIRST.equals(signPackage.getSigningSequence()))
        {
            throw new ServiceException("当前签约包不是先确认事实流程");
        }
        if (StringUtils.isBlank(signPackage.getDocumentVersion())
                || dataRequestId == null || dataRequestId <= 0
                || StringUtils.isBlank(signatureRequestId)
                || signatureRequestId.trim().length() > 64
                || signatureSampleTime == null
                || !validSignatureBytes(signatureSampleBytes)
                || StringUtils.isBlank(signatureSampleHash)
                || !sameHash(signatureSampleHash, sha256(signatureSampleBytes)))
        {
            throw new ServiceException("本签约包手写签名不完整或校验不一致");
        }
    }

    OaSignFileEvidence validatePersistedStagedSignatureSample(
            OaSignPackage signPackage, Long dataRequestId, String expectedHash,
            Date expectedTime, List<OaSignFileEvidence> evidenceRows)
    {
        validatePersistedStagedSignatureSnapshot(
                signPackage, expectedHash, expectedTime);
        return validatePersistedStagedSignatureEvidence(
                dataRequestId, expectedHash, expectedTime, evidenceRows);
    }

    void validatePersistedStagedSignatureSnapshot(OaSignPackage signPackage,
            String expectedHash, Date expectedTime)
    {
        if (signPackage == null || StringUtils.isBlank(signPackage.getSignatureSampleFileUrl())
                || !sameHash(signPackage.getSignatureSampleHash(), expectedHash)
                || !sameSecond(signPackage.getSignatureSampleTime(), expectedTime)
                || !sameSecond(signPackage.getInitialSignedTime(), expectedTime)
                || !sameSecond(signPackage.getSignedTime(), expectedTime))
        {
            throw new ServiceException("签约包唯一签名快照与员工提交不一致");
        }
    }

    OaSignFileEvidence validatePersistedStagedSignatureEvidence(
            Long dataRequestId, String expectedHash, Date expectedTime,
            List<OaSignFileEvidence> evidenceRows)
    {
        String sampleVersion = "SAMPLE-" + dataRequestId;
        List<OaSignFileEvidence> matches = (evidenceRows == null
                ? List.<OaSignFileEvidence>of() : evidenceRows).stream()
                .filter(Objects::nonNull)
                .filter(value -> OaSignFileEvidenceType.SIGNATURE_SAMPLE
                        .equals(value.getEvidenceType()))
                .filter(value -> sampleVersion.equals(value.getDocumentVersion()))
                .toList();
        if (matches.size() != 1 || !sameHash(matches.get(0).getFileHash(), expectedHash)
                || !sameSecond(matches.get(0).getGeneratedTime(), expectedTime))
        {
            throw new ServiceException("签约包唯一签名证据不完整或不一致");
        }
        return matches.get(0);
    }

    FrozenSignatureFirstEvidence validateFrozenSignatureFirstCandidate(
            OaSignPackage signPackage, List<OaSignFileEvidence> persistedEvidence,
            List<OaSignEvent> persistedEvents)
    {
        validateFrozenSignatureFirstPackage(signPackage);
        OaSignFileEvidence sample = validateFrozenSignatureSample(
                signPackage, persistedEvidence);
        OaSignEvent prepared = validateFrozenPreparedEvent(
                signPackage, persistedEvents);
        return new FrozenSignatureFirstEvidence(sample, prepared);
    }

    void validateFrozenSignatureFirstPackage(OaSignPackage signPackage)
    {
        if (signPackage == null
                || !OaSignPackageStatus.DRAFT.equals(signPackage.getStatus())
                || StringUtils.isBlank(signPackage.getSignatureSampleHash())
                || StringUtils.isBlank(signPackage.getSignatureSampleFileUrl())
                || signPackage.getSignatureSampleTime() == null
                || StringUtils.isBlank(signPackage.getFinalDocumentVersion())
                || StringUtils.isBlank(signPackage.getFinalDocumentRootHash())
                || signPackage.getFinalGeneratedTime() == null)
        {
            throw new ServiceException("先留签名候选的冻结结果不完整");
        }
    }

    OaSignFileEvidence validateFrozenSignatureSample(OaSignPackage signPackage,
            List<OaSignFileEvidence> persistedEvidence)
    {
        List<OaSignFileEvidence> sampleEvidence = (persistedEvidence == null
                ? List.<OaSignFileEvidence>of() : persistedEvidence).stream()
                .filter(value -> value != null
                        && OaSignFileEvidenceType.SIGNATURE_SAMPLE.equals(value.getEvidenceType()))
                .toList();
        if (sampleEvidence.size() != 1)
        {
            throw new ServiceException("任务级手写签名样本证据不唯一");
        }
        OaSignFileEvidence sample = sampleEvidence.get(0);
        if (!Objects.equals(signPackage.getPackageId(), sample.getPackageId())
                || StringUtils.isBlank(sample.getDocumentVersion())
                || !sample.getDocumentVersion().matches("SAMPLE-[1-9][0-9]*")
                || !sameHash(signPackage.getSignatureSampleHash(), sample.getFileHash())
                || !sameSecond(signPackage.getSignatureSampleTime(), sample.getGeneratedTime()))
        {
            throw new ServiceException("任务级手写签名样本证据校验不一致");
        }
        return sample;
    }

    OaSignEvent validateFrozenPreparedEvent(OaSignPackage signPackage,
            List<OaSignEvent> persistedEvents)
    {
        List<OaSignEvent> preparedEvents = (persistedEvents == null
                ? List.<OaSignEvent>of() : persistedEvents).stream()
                .filter(value -> value != null
                        && "FINAL_CONTRACT_PREPARED".equals(value.getEventType()))
                .toList();
        if (preparedEvents.size() != 1)
        {
            throw new ServiceException("先留签名候选生成证据不唯一");
        }
        OaSignEvent prepared = preparedEvents.get(0);
        if (!Objects.equals(signPackage.getPackageId(), prepared.getPackageId())
                || !Objects.equals("SYSTEM", prepared.getOperatorRole())
                || StringUtils.isBlank(prepared.getRequestId())
                || !sameHash(signPackage.getFinalDocumentRootHash(),
                        prepared.getDocumentHash()))
        {
            throw new ServiceException("先留签名候选生成证据校验不一致");
        }
        return prepared;
    }

    void validateRepeatedSignatureFirstCandidate(OaSignPackage signPackage,
            FrozenSignatureFirstEvidence frozen, Long expectedDataRequestId,
            String expectedSignatureRequestId, String expectedSampleHash,
            Date expectedSampleTime)
    {
        if (!sameHash(signPackage.getSignatureSampleHash(), expectedSampleHash)
                || !sameSecond(signPackage.getSignatureSampleTime(), expectedSampleTime))
        {
            throw new ServiceException("签名样本生成请求与已冻结结果不一致");
        }
        String expectedEvidenceVersion = "SAMPLE-" + expectedDataRequestId;
        if (!Objects.equals(expectedEvidenceVersion, frozen.sample().getDocumentVersion())
                || !sameHash(expectedSampleHash, frozen.sample().getFileHash())
                || !sameSecond(expectedSampleTime, frozen.sample().getGeneratedTime()))
        {
            throw new ServiceException("签名样本来源请求与已冻结证据不一致");
        }
        if (!Objects.equals(expectedSignatureRequestId, frozen.prepared().getRequestId()))
        {
            throw new ServiceException("签名样本幂等请求与已冻结候选不一致");
        }
    }

    private boolean validSignatureBytes(byte[] bytes)
    {
        return OaSignImageValidator.isValidSignaturePng(bytes);
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean sameSecond(Date first, Date second)
    {
        return first != null && second != null
                && first.getTime() / 1_000L == second.getTime() / 1_000L;
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("文件校验算法不可用", exception);
        }
    }

    record FrozenSignatureFirstEvidence(OaSignFileEvidence sample,
            OaSignEvent prepared) {}
}
