package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.constant.OaSignTaskStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;

class OaSignPackageFinalizationPolicyTest
{
    private static final String CONFIRMATION_TEXT =
            "本人已阅读并确认最终合同中的公司及印章信息";

    private final OaSignPackageFinalizationPolicy policy =
            new OaSignPackageFinalizationPolicy();

    @Test
    void finalConfirmationRequiresExactTextAndCompleteFrozenEvidence()
    {
        OaSignFinalConfirmRequest valid = finalConfirmationRequest();

        assertThatCode(() -> policy.validateFinalConfirmationRequest(valid))
                .doesNotThrowAnyException();

        OaSignFinalConfirmRequest wrongText = finalConfirmationRequest();
        wrongText.setConfirmationText("我已阅读");
        assertThatThrownBy(() -> policy.validateFinalConfirmationRequest(wrongText))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CONFIRMATION_TEXT);

        OaSignFinalConfirmRequest incomplete = finalConfirmationRequest();
        incomplete.setDocumentRootHash(" ");
        assertThatThrownBy(() -> policy.validateFinalConfirmationRequest(incomplete))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("信息不完整");
    }

    @Test
    void finalConfirmationReplayBindsEmployeePackageVersionHashTextAndIdentity()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setEmployeeId(960L);
        OaSignFinalConfirmRequest request = finalConfirmationRequest();
        OaSignFinalConfirmation existing = new OaSignFinalConfirmation();
        existing.setPackageId(90L);
        existing.setEmployeeId(960L);
        existing.setFinalDocumentVersion(request.getFinalDocumentVersion());
        existing.setDocumentRootHash(request.getDocumentRootHash().toUpperCase());
        existing.setConfirmationText(request.getConfirmationText());
        existing.setIdentityMethod("LOGIN_TOKEN");

        assertThatCode(() -> policy.assertSameFinalConfirmationReplay(
                existing, signPackage, request)).doesNotThrowAnyException();

        existing.setEmployeeId(961L);
        assertThatThrownBy(() -> policy.assertSameFinalConfirmationReplay(
                existing, signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于不同内容");
    }

    @Test
    void finalizationOnlyAcceptsTheFrozenSignatureFirstSequence()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        OaSignPackageFinalizeRequest request = finalizeRequest();
        request.setSigningSequence(" signature_first ");

        assertThatCode(() -> policy.validateFinalizeSigningSequence(
                signPackage, request)).doesNotThrowAnyException();

        request.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        assertThatThrownBy(() -> policy.validateFinalizeSigningSequence(
                signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签署顺序已变化");

        request.setSigningSequence(null);
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        assertThatThrownBy(() -> policy.validateFinalizeSigningSequence(
                signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅先确认事实并留签名流程");
    }

    @Test
    void finalizationWriteRequiresBoundedRequestIdAndNonNegativeVersions()
    {
        OaSignPackageFinalizeRequest request = finalizeRequest();

        assertThatCode(() -> policy.validateFinalizeWriteRequest(
                request, request.getRequestId())).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateFinalizeWriteRequest(request, " "))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");
        assertThatThrownBy(() -> policy.validateFinalizeWriteRequest(
                request, "x".repeat(65)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");

        request.setExpectedTaskVersion(-1L);
        assertThatThrownBy(() -> policy.validateFinalizeWriteRequest(
                request, request.getRequestId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");
    }

    @Test
    void finalizationPayloadHashCanonicalizesReasonAndSigningSequence()
    {
        OaSignPackageFinalizeRequest first = finalizeRequest();
        first.setCorrectionReason("  人工更正  ");
        first.setSigningSequence(" signature_first ");
        OaSignPackageFinalizeRequest replay = finalizeRequest();
        replay.setCorrectionReason("人工更正");
        replay.setSigningSequence(null);

        String firstHash = policy.finalizeRequestPayloadHash(90L, first);
        String replayHash = policy.finalizeRequestPayloadHash(90L, replay);

        assertThat(firstHash).matches("[0-9a-f]{64}").isEqualTo(replayHash);

        replay.setExpectedTaskVersion(12L);
        assertThat(policy.finalizeRequestPayloadHash(90L, replay))
                .isNotEqualTo(firstHash);
    }

    @Test
    void durableRequestReplayBindsPackageActorRoleAndPayload()
    {
        OaSignEvent existing = new OaSignEvent();
        existing.setPackageId(90L);
        existing.setOperatorUserId(101L);
        existing.setOperatorRole("HR");
        existing.setDocumentHash("a".repeat(64));

        assertThatCode(() -> policy.assertSameFinalizeRequestReplay(
                existing, 90L, "A".repeat(64), 101L))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.assertSameFinalizeRequestReplay(
                existing, 90L, "a".repeat(64), 102L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于不同内容");
        assertThatThrownBy(() -> policy.assertSameFinalizeRequestReplay(
                existing, 91L, "a".repeat(64), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于不同内容");
    }

    @Test
    void replayStatusMapsOnlyPreparedPendingConfirmationAndSignedStates()
    {
        OaSignPackage signPackage = preparedPackage();
        assertThat(policy.existingReplayTaskStatus(signPackage))
                .isEqualTo(OaSignTaskStatus.PENDING_COMPANY);

        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setFinalConfirmationStatus("PENDING");
        assertThat(policy.existingReplayTaskStatus(signPackage))
                .isEqualTo(OaSignTaskStatus.PENDING_FINAL_CONFIRM);

        signPackage.setStatus(OaSignPackageStatus.SIGNED);
        assertThat(policy.existingReplayTaskStatus(signPackage))
                .isEqualTo(OaSignTaskStatus.SIGNED);

        signPackage.setStatus(OaSignPackageStatus.DRAFT);
        assertThatThrownBy(() -> policy.existingReplayTaskStatus(signPackage))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态不一致");
    }

    @Test
    void replayDecisionUsesFrozenAutomaticCompanyAndExactSeal()
    {
        OaSignPackage signPackage = preparedPackage();
        signPackage.setLegalEntityIdSnapshot(31L);
        signPackage.setSealIdSnapshot(41L);
        signPackage.setLegalEntityResolveMode("AUTO");
        OaSignPackageFinalizeRequest request = finalizeRequest();
        request.setLegalEntityId(null);

        assertThatCode(() -> policy.validateExistingReplayDecision(
                signPackage, request)).doesNotThrowAnyException();

        request.setSealId(42L);
        assertThatThrownBy(() -> policy.validateExistingReplayDecision(
                signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他公司或印章");

        request.setSealId(41L);
        signPackage.setLegalEntityResolveMode("MANUAL");
        assertThatThrownBy(() -> policy.validateExistingReplayDecision(
                signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他公司或印章");

        request.setLegalEntityId(31L);
        assertThatCode(() -> policy.validateExistingReplayDecision(
                signPackage, request)).doesNotThrowAnyException();
    }

    @Test
    void preparedCandidateRequiresEveryFrozenFinalEvidenceField()
    {
        OaSignPackage signPackage = preparedPackage();
        assertThat(policy.isPreparedFinalCandidate(signPackage)).isTrue();

        signPackage.setFinalDocumentRootHash(null);
        assertThat(policy.isPreparedFinalCandidate(signPackage)).isFalse();
        assertThat(policy.isPreparedFinalCandidate(null)).isFalse();
    }

    private static OaSignFinalConfirmRequest finalConfirmationRequest()
    {
        OaSignFinalConfirmRequest request = new OaSignFinalConfirmRequest();
        request.setFinalDocumentVersion("FINAL-2");
        request.setDocumentRootHash("a".repeat(64));
        request.setConfirmationText(CONFIRMATION_TEXT);
        request.setRequestId("confirm-final-1");
        return request;
    }

    private static OaSignPackageFinalizeRequest finalizeRequest()
    {
        OaSignPackageFinalizeRequest request = new OaSignPackageFinalizeRequest();
        request.setLegalEntityId(31L);
        request.setSealId(41L);
        request.setCorrectionReason("人工更正");
        request.setRequestId("finalize-1");
        request.setExpectedVersion(7L);
        request.setExpectedTaskVersion(11L);
        request.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        return request;
    }

    private static OaSignPackage preparedPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_COMPANY);
        signPackage.setFinalConfirmationStatus("PREPARED_NOT_SENT");
        signPackage.setFinalDocumentVersion("FINAL-2");
        signPackage.setFinalDocumentRootHash("b".repeat(64));
        signPackage.setFinalGeneratedTime(new Date());
        return signPackage;
    }
}
