package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.dto.OaSignDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentHashRequest;
import com.erp.oa.domain.dto.OaSignPackageSignRequest;

@DisplayName("签约包签署请求策略")
class OaSignPackageSigningRequestPolicyTest
{
    private static final String CONFIRMATION_TEXT = "本人确认签署本签约包";
    private final OaSignPackageSigningRequestPolicy policy =
            new OaSignPackageSigningRequestPolicy();

    @Test
    @DisplayName("普通签署请求必须携带确认文本、请求编号、版本、签名和逐文件哈希")
    void validatesOrdinarySigningRequest()
    {
        OaSignPackage signPackage = new OaSignPackage();
        OaSignPackageSignRequest request = ordinaryRequest();

        assertThatCode(() -> policy.validate(signPackage, request))
                .doesNotThrowAnyException();

        OaSignPackageSignRequest invalidConfirmation = ordinaryRequest();
        invalidConfirmation.setSignConfirmText("我确认");
        assertThatThrownBy(() -> policy.validate(signPackage, invalidConfirmation))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CONFIRMATION_TEXT);

        OaSignPackageSignRequest missingHashes = ordinaryRequest();
        missingHashes.setDocumentHashes(List.of());
        assertThatThrownBy(() -> policy.validate(signPackage, missingHashes))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("待签文件校验值");
    }

    @Test
    @DisplayName("公司先签请求必须携带最终合同版本、根哈希和逐文件哈希")
    void validatesCompanyFirstSigningRequest()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setSigningSequence(OaSignSigningSequence.COMPANY_FIRST);
        OaSignPackageSignRequest request = ordinaryRequest();
        request.setDocumentHashes(null);
        request.setFinalDocumentVersion("SP-90-F1");
        request.setFinalDocumentRootHash("a".repeat(64));
        request.setFinalDocumentHashes(List.of(finalHash(51L, "b".repeat(64))));

        assertThatCode(() -> policy.validate(signPackage, request))
                .doesNotThrowAnyException();

        request.setFinalDocumentRootHash("not-a-hash");
        assertThatThrownBy(() -> policy.validate(signPackage, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同版本");
    }

    @Test
    @DisplayName("签署载荷指纹固定排序并忽略哈希大小写")
    void payloadHashIsCanonical()
    {
        OaSignPackageSignRequest first = ordinaryRequest();
        first.setDocumentHashes(List.of(
                reviewHash(52L, "B".repeat(64)),
                reviewHash(51L, "A".repeat(64))));
        OaSignPackageSignRequest second = ordinaryRequest();
        second.setDocumentHashes(List.of(
                reviewHash(51L, "a".repeat(64)),
                reviewHash(52L, "b".repeat(64))));
        byte[] signature = "signature".getBytes(StandardCharsets.UTF_8);

        String firstHash = policy.payloadHash(first, signature);
        String secondHash = policy.payloadHash(second, signature);

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash)
                .isEqualTo("f45b20973339c4a0efab410dcda1b3a8bf06a1e32f944d1700ada5b927e78260");
    }

    @Test
    @DisplayName("逐文件哈希集合拒绝缺失、重复和非法最终哈希")
    void rejectsIncompleteOrDuplicateHashSets()
    {
        assertThatThrownBy(() -> policy.requestedReviewHashes(
                List.of(reviewHash(51L, "a"), reviewHash(51L, "b")), 2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("集合不完整");
        assertThatThrownBy(() -> policy.requestedFinalHashes(
                List.of(finalHash(51L, "invalid")), 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同文件集合不完整");
    }

    @Test
    @DisplayName("重复请求只允许同一签约包和相同载荷")
    void replayRequiresSamePackageAndPayload()
    {
        OaSignEvent existing = new OaSignEvent();
        existing.setPackageId(90L);
        existing.setDocumentHash("A".repeat(64));

        assertThatCode(() -> policy.assertSameReplay(existing, 90L, "a".repeat(64)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertSameReplay(existing, 91L, "a".repeat(64)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("其他签约包");
        assertThatThrownBy(() -> policy.assertSameReplay(existing, 90L, "b".repeat(64)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不同载荷");
    }

    @Test
    @DisplayName("签名图片仅接受非空且不超过五兆的 PNG data URL")
    void decodesBoundedPngDataUrl()
    {
        byte[] signature = "signature".getBytes(StandardCharsets.UTF_8);
        String dataUrl = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(signature);

        assertThat(policy.decodeSignaturePng(dataUrl)).isEqualTo(signature);
        assertThatThrownBy(() -> policy.decodeSignaturePng(
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(signature)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无损图片格式");
        assertThatThrownBy(() -> policy.decodeSignaturePng("data:image/png;base64,"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("内容不合法");
    }

    private OaSignPackageSignRequest ordinaryRequest()
    {
        OaSignPackageSignRequest request = new OaSignPackageSignRequest();
        request.setRequestId("request-1");
        request.setDocumentVersion("SP-90-V1");
        request.setSignConfirmText(CONFIRMATION_TEXT);
        request.setSignatureDataUrl("data:image/png;base64,c2lnbmF0dXJl");
        request.setDocumentHashes(List.of(reviewHash(51L, "a".repeat(64))));
        return request;
    }

    private OaSignDocumentHashRequest reviewHash(Long documentId, String hash)
    {
        OaSignDocumentHashRequest value = new OaSignDocumentHashRequest();
        value.setDocumentId(documentId);
        value.setReviewPdfHash(hash);
        return value;
    }

    private OaSignFinalDocumentHashRequest finalHash(Long documentId, String hash)
    {
        OaSignFinalDocumentHashRequest value = new OaSignFinalDocumentHashRequest();
        value.setDocumentId(documentId);
        value.setFinalPdfHash(hash);
        return value;
    }
}
