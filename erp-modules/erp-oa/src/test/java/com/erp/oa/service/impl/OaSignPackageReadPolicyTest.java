package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;

@DisplayName("签约包阅读证据策略")
class OaSignPackageReadPolicyTest
{
    private static final String VERSION = "SP-90-V1";
    private static final String FINAL_VERSION = "SP-90-F1";
    private static final String HASH = "A".repeat(64);
    private final OaSignPackageReadPolicy policy =
            new OaSignPackageReadPolicy();

    @Test
    @DisplayName("初始阅读请求必须携带有界请求编号和非负预期版本")
    void validatesInitialReadRequest()
    {
        OaSignDocumentReadRequest request = initialRequest();
        request.setRequestId(" request-1 ");

        assertThatCode(() -> policy.validateInitialRequest(request))
                .doesNotThrowAnyException();

        OaSignDocumentReadRequest missingId = initialRequest();
        missingId.setRequestId(" ");
        assertThatThrownBy(() -> policy.validateInitialRequest(missingId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号或预期版本");

        OaSignDocumentReadRequest invalidVersion = initialRequest();
        invalidVersion.setExpectedVersion(-1L);
        assertThatThrownBy(() -> policy.validateInitialRequest(invalidVersion))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号或预期版本");
    }

    @Test
    @DisplayName("初始阅读证据绑定签约包版本、文档版本和阅读文件哈希")
    void validatesInitialReadEvidence()
    {
        OaSignPackage signPackage = signPackage();
        OaSignPackageDocument document = initialDocument();
        OaSignDocumentReadRequest request = initialRequest();

        assertThatCode(() -> policy.validateInitialEvidence(
                signPackage, document, request)).doesNotThrowAnyException();

        signPackage.setVersion(8L);
        assertThatThrownBy(() -> policy.validateInitialEvidence(
                signPackage, document, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("签约包版本已变化");

        signPackage.setVersion(7L);
        document.setDocumentVersion("SP-90-V2");
        assertThatThrownBy(() -> policy.validateInitialEvidence(
                signPackage, document, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("文档版本已更新");
    }

    @Test
    @DisplayName("初始阅读事件兼容历史载荷并生成规范化新载荷")
    void recognizesCanonicalAndLegacyInitialReadEvents()
    {
        OaSignPackageDocument document = initialDocument();
        OaSignDocumentReadRequest request = initialRequest();
        OaSignEvent canonical = initialEvent(
                "documentVersion=" + VERSION
                        + ";reviewPdfHash=" + HASH.toLowerCase()
                        + ";expectedVersion=7");

        assertThat(policy.initialEventPayload(request))
                .isEqualTo(canonical.getEventPayload());
        assertThat(policy.hasInitialReadEvent(List.of(canonical), document))
                .isTrue();

        OaSignEvent legacy = initialEvent(
                "documentVersion=" + VERSION + ";reviewPdfHash=" + HASH);
        assertThat(policy.hasInitialReadEvent(List.of(legacy), document))
                .isTrue();
    }

    @Test
    @DisplayName("初始阅读重复请求绑定签约包、文档、员工、角色和载荷")
    void validatesInitialReadReplay()
    {
        OaSignPackage signPackage = signPackage();
        OaSignDocumentReadRequest request = initialRequest();
        OaSignEvent existing = initialEvent(policy.initialEventPayload(request));
        existing.setPackageId(90L);
        existing.setOperatorUserId(900L);
        existing.setOperatorRole("EMPLOYEE");

        assertThatCode(() -> policy.assertSameInitialReplay(existing,
                signPackage, 51L, request, 900L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertSameInitialReplay(existing,
                signPackage, 51L, request, 901L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号已用于不同内容");
    }

    @Test
    @DisplayName("最终合同打开请求绑定最终版本、文件地址和哈希")
    void validatesFinalReadRequestAndEvidence()
    {
        OaSignPackage signPackage = signPackage();
        OaSignPackageDocument document = finalDocument();
        OaSignFinalDocumentReadRequest request = finalRequest();

        assertThatCode(() -> policy.validateFinalRequest(request))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateFinalEvidence(
                signPackage, document, request)).doesNotThrowAnyException();

        OaSignFinalDocumentReadRequest missingId = finalRequest();
        missingId.setRequestId(" ");
        assertThatThrownBy(() -> policy.validateFinalRequest(missingId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("打开记录不完整");

        request.setFinalDocumentVersion("SP-90-F2");
        assertThatThrownBy(() -> policy.validateFinalEvidence(
                signPackage, document, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最终合同版本已变化");
    }

    @Test
    @DisplayName("最终阅读事件和重复请求使用规范化版本哈希载荷")
    void recognizesAndValidatesFinalReadReplay()
    {
        OaSignPackage signPackage = signPackage();
        OaSignPackageDocument document = finalDocument();
        OaSignFinalDocumentReadRequest request = finalRequest();
        OaSignEvent existing = new OaSignEvent();
        existing.setEventType("FINAL_DOCUMENT_OPENED");
        existing.setPackageId(90L);
        existing.setDocumentId(51L);
        existing.setOperatorUserId(900L);
        existing.setOperatorRole("EMPLOYEE");
        existing.setDocumentHash(HASH.toLowerCase());
        existing.setEventPayload("version=" + FINAL_VERSION
                + ";hash=" + HASH.toLowerCase());

        assertThat(policy.finalEventPayload(signPackage, document))
                .isEqualTo(existing.getEventPayload());
        assertThat(policy.hasFinalReadEvent(
                signPackage, document, List.of(existing))).isTrue();
        assertThatCode(() -> policy.assertSameFinalReplay(existing,
                signPackage, 51L, request)).doesNotThrowAnyException();

        existing.setPackageId(91L);
        assertThatThrownBy(() -> policy.assertSameFinalReplay(existing,
                signPackage, 51L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("请求编号已用于不同内容");
    }

    private OaSignPackage signPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setEmployeeId(900L);
        signPackage.setDocumentVersion(VERSION);
        signPackage.setFinalDocumentVersion(FINAL_VERSION);
        signPackage.setVersion(7L);
        return signPackage;
    }

    private OaSignPackageDocument initialDocument()
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setDocumentVersion(VERSION);
        document.setReviewPdfUrl("sign/review.pdf");
        document.setReviewPdfHash(HASH);
        return document;
    }

    private OaSignPackageDocument finalDocument()
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setFinalDocumentVersion(FINAL_VERSION);
        document.setFinalPdfUrl("sign/final.pdf");
        document.setFinalPdfHash(HASH);
        return document;
    }

    private OaSignDocumentReadRequest initialRequest()
    {
        OaSignDocumentReadRequest request = new OaSignDocumentReadRequest();
        request.setRequestId("request-1");
        request.setExpectedVersion(7L);
        request.setDocumentVersion(VERSION);
        request.setReviewPdfHash(HASH.toLowerCase());
        return request;
    }

    private OaSignFinalDocumentReadRequest finalRequest()
    {
        OaSignFinalDocumentReadRequest request =
                new OaSignFinalDocumentReadRequest();
        request.setRequestId("final-read-1");
        request.setFinalDocumentVersion(FINAL_VERSION);
        request.setFinalPdfHash(HASH.toLowerCase());
        return request;
    }

    private OaSignEvent initialEvent(String payload)
    {
        OaSignEvent event = new OaSignEvent();
        event.setEventType("DOCUMENT_READ_CONFIRMED");
        event.setDocumentId(51L);
        event.setDocumentHash(HASH.toLowerCase());
        event.setEventPayload(payload);
        return event;
    }
}
