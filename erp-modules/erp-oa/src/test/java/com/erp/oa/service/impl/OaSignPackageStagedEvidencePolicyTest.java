package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFileEvidence;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

class OaSignPackageStagedEvidencePolicyTest
{
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final Date CAPTURED_TIME = new Date(1_752_990_400_456L);
    private static final String SAMPLE_HASH = sha256(PNG);
    private static final String ROOT_HASH = "a".repeat(64);

    private final OaSignPackageStagedEvidencePolicy policy =
            new OaSignPackageStagedEvidencePolicy();

    @Test
    void candidateSampleRequiresPositiveSourceValidPngTimeAndMatchingHash()
    {
        assertThatCode(() -> policy.validateCandidateSample(
                500L, "request-1", PNG, SAMPLE_HASH.toUpperCase(), CAPTURED_TIME))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateCandidateSample(
                0L, "request-1", PNG, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不完整或校验不一致");
        assertThatThrownBy(() -> policy.validateCandidateSample(
                500L, " ", PNG, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不完整或校验不一致");
        assertThatThrownBy(() -> policy.validateCandidateSample(
                500L, "request-1", new byte[] {1, 2, 3}, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不完整或校验不一致");
        assertThatThrownBy(() -> policy.validateCandidateSample(
                500L, "request-1", PNG, "b".repeat(64), CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不完整或校验不一致");
    }

    @Test
    void stagedBindingRequiresTaskPackageIdentityVersionAndBoundedRequest()
    {
        assertThatCode(() -> policy.requireStagedSignatureBinding(
                packageSnapshot(), taskSnapshot(), 500L, "request-1",
                PNG, SAMPLE_HASH, CAPTURED_TIME)).doesNotThrowAnyException();

        OaSignTask changedEmployee = taskSnapshot();
        changedEmployee.setEmployeeId(999L);
        assertThatThrownBy(() -> policy.requireStagedSignatureBinding(
                packageSnapshot(), changedEmployee, 500L, "request-1",
                PNG, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("关联已变化");

        OaSignPackage wrongSequence = packageSnapshot();
        wrongSequence.setSigningSequence("COMPANY_FIRST");
        assertThatThrownBy(() -> policy.requireStagedSignatureBinding(
                wrongSequence, taskSnapshot(), 500L, "request-1",
                PNG, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不是先确认事实流程");

        assertThatThrownBy(() -> policy.requireStagedSignatureBinding(
                packageSnapshot(), taskSnapshot(), 500L, "x".repeat(65),
                PNG, SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("手写签名不完整");
    }

    @Test
    void persistedSampleRequiresOneMatchingEvidenceAndSecondPrecisionSnapshots()
    {
        OaSignPackage signPackage = packageSnapshot();
        signPackage.setSignatureSampleTime(new Date(CAPTURED_TIME.getTime() + 100L));
        signPackage.setInitialSignedTime(new Date(CAPTURED_TIME.getTime() + 200L));
        signPackage.setSignedTime(new Date(CAPTURED_TIME.getTime() + 300L));
        OaSignFileEvidence evidence = sampleEvidence();
        evidence.setGeneratedTime(new Date(CAPTURED_TIME.getTime() + 400L));

        assertThat(policy.validatePersistedStagedSignatureSample(
                signPackage, 500L, SAMPLE_HASH.toUpperCase(), CAPTURED_TIME,
                List.of(evidence))).isSameAs(evidence);

        signPackage.setSignedTime(new Date(CAPTURED_TIME.getTime() + 1_000L));
        assertThatThrownBy(() -> policy.validatePersistedStagedSignatureSample(
                signPackage, 500L, SAMPLE_HASH, CAPTURED_TIME,
                List.of(evidence)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名快照");

        signPackage.setSignedTime(CAPTURED_TIME);
        assertThatThrownBy(() -> policy.validatePersistedStagedSignatureSample(
                signPackage, 500L, SAMPLE_HASH, CAPTURED_TIME,
                List.of(evidence, sampleEvidence())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签名证据不完整或不一致");
    }

    @Test
    void frozenCandidateRequiresOnePackageBoundSampleAndOneSystemPreparedEvent()
    {
        OaSignFileEvidence evidence = sampleEvidence();
        OaSignEvent event = preparedEvent();

        OaSignPackageStagedEvidencePolicy.FrozenSignatureFirstEvidence frozen =
                policy.validateFrozenSignatureFirstCandidate(
                        packageSnapshot(), List.of(evidence), List.of(event));

        assertThat(frozen.sample()).isSameAs(evidence);
        assertThat(frozen.prepared()).isSameAs(event);
    }

    @Test
    void malformedFrozenCandidateEvidenceFailsClosed()
    {
        OaSignPackage incomplete = packageSnapshot();
        incomplete.setFinalDocumentRootHash(null);
        assertThatThrownBy(() -> policy.validateFrozenSignatureFirstCandidate(
                incomplete, List.of(sampleEvidence()), List.of(preparedEvent())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("冻结结果不完整");

        assertThatThrownBy(() -> policy.validateFrozenSignatureFirstCandidate(
                packageSnapshot(), List.of(sampleEvidence(), sampleEvidence()),
                List.of(preparedEvent())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("样本证据不唯一");

        OaSignEvent wrongRole = preparedEvent();
        wrongRole.setOperatorRole("HR");
        assertThatThrownBy(() -> policy.validateFrozenSignatureFirstCandidate(
                packageSnapshot(), List.of(sampleEvidence()), List.of(wrongRole)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("生成证据校验不一致");
    }

    @Test
    void repeatedCandidateBindsSourceRequestHashTimeAndPreparedRequestId()
    {
        OaSignPackage signPackage = packageSnapshot();
        OaSignPackageStagedEvidencePolicy.FrozenSignatureFirstEvidence frozen =
                policy.validateFrozenSignatureFirstCandidate(
                        signPackage, List.of(sampleEvidence()), List.of(preparedEvent()));

        assertThatCode(() -> policy.validateRepeatedSignatureFirstCandidate(
                signPackage, frozen, 500L, "signature-request-1",
                SAMPLE_HASH, CAPTURED_TIME)).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateRepeatedSignatureFirstCandidate(
                signPackage, frozen, 501L, "signature-request-1",
                SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("来源请求");
        assertThatThrownBy(() -> policy.validateRepeatedSignatureFirstCandidate(
                signPackage, frozen, 500L, "signature-request-2",
                SAMPLE_HASH, CAPTURED_TIME))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("幂等请求");
        assertThatThrownBy(() -> policy.validateRepeatedSignatureFirstCandidate(
                signPackage, frozen, 500L, "signature-request-1",
                SAMPLE_HASH, new Date(CAPTURED_TIME.getTime() + 1_000L)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已冻结结果不一致");
    }

    private OaSignPackage packageSnapshot()
    {
        OaSignPackage value = new OaSignPackage();
        value.setPackageId(90L);
        value.setTaskId(9L);
        value.setEmployeeId(201L);
        value.setPlanVersionId(66L);
        value.setStatus(OaSignPackageStatus.DRAFT);
        value.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        value.setDocumentVersion("SP-90-V1");
        value.setSignatureSampleFileUrl("/signature/sample.png");
        value.setSignatureSampleHash(SAMPLE_HASH);
        value.setSignatureSampleTime(CAPTURED_TIME);
        value.setInitialSignedTime(CAPTURED_TIME);
        value.setSignedTime(CAPTURED_TIME);
        value.setFinalDocumentVersion("SP-90-F1");
        value.setFinalDocumentRootHash(ROOT_HASH);
        value.setFinalGeneratedTime(new Date(CAPTURED_TIME.getTime() + 2_000L));
        return value;
    }

    private OaSignTask taskSnapshot()
    {
        OaSignTask value = new OaSignTask();
        value.setTaskId(9L);
        value.setPackageId(90L);
        value.setEmployeeId(201L);
        value.setPlanVersionId(66L);
        return value;
    }

    private OaSignFileEvidence sampleEvidence()
    {
        OaSignFileEvidence value = new OaSignFileEvidence();
        value.setPackageId(90L);
        value.setEvidenceType(OaSignFileEvidenceType.SIGNATURE_SAMPLE);
        value.setDocumentVersion("SAMPLE-500");
        value.setFileHash(SAMPLE_HASH);
        value.setGeneratedTime(CAPTURED_TIME);
        return value;
    }

    private OaSignEvent preparedEvent()
    {
        OaSignEvent value = new OaSignEvent();
        value.setPackageId(90L);
        value.setEventType("FINAL_CONTRACT_PREPARED");
        value.setOperatorRole("SYSTEM");
        value.setRequestId("signature-request-1");
        value.setDocumentHash(ROOT_HASH);
        return value;
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(exception);
        }
    }
}
