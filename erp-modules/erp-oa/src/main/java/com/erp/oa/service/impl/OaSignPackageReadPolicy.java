package com.erp.oa.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.domain.dto.OaSignFinalDocumentReadRequest;

/**
 * Defines canonical evidence rules for opening initial and final sign documents.
 */
final class OaSignPackageReadPolicy
{
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String INITIAL_READ_EVENT = "DOCUMENT_READ_CONFIRMED";
    private static final String FINAL_READ_EVENT = "FINAL_DOCUMENT_OPENED";

    void validateInitialRequest(OaSignDocumentReadRequest request)
    {
        String requestId = request == null ? null
                : StringUtils.trim(request.getRequestId());
        if (StringUtils.isBlank(requestId) || requestId.length() > 64
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 0)
        {
            throw new ServiceException("阅读确认请求缺少请求编号或预期版本");
        }
    }

    void validateInitialEvidence(OaSignPackage signPackage,
            OaSignPackageDocument document, OaSignDocumentReadRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getDocumentVersion())
                || !Objects.equals(signPackage.getDocumentVersion(),
                        request.getDocumentVersion())
                || !Objects.equals(document.getDocumentVersion(),
                        request.getDocumentVersion()))
        {
            throw new ServiceException("文档版本已更新，请重新打开文件后确认阅读");
        }
        Long currentVersion = signPackage.getVersion() == null
                ? 0L : signPackage.getVersion();
        if (!Objects.equals(currentVersion, request.getExpectedVersion()))
        {
            throw new ServiceException("签约包版本已变化，请刷新后重试");
        }
        if (StringUtils.isBlank(document.getReviewPdfUrl())
                || StringUtils.isBlank(document.getReviewPdfHash())
                || !sameHash(document.getReviewPdfHash(),
                        request.getReviewPdfHash()))
        {
            throw new ServiceException("阅读文件校验不一致，请刷新后重试");
        }
    }

    String initialEventPayload(OaSignDocumentReadRequest request)
    {
        return initialEventPayload(request.getDocumentVersion(),
                request.getReviewPdfHash())
                + ";expectedVersion=" + request.getExpectedVersion();
    }

    boolean hasInitialReadEvent(List<OaSignEvent> events,
            OaSignPackageDocument document)
    {
        if (events == null)
        {
            return false;
        }
        String legacyPayload = legacyInitialEventPayload(document);
        String canonicalPayloadPrefix = initialEventPayload(
                document.getDocumentVersion(), document.getReviewPdfHash());
        return events.stream().anyMatch(event ->
                INITIAL_READ_EVENT.equals(event.getEventType())
                        && Objects.equals(document.getDocumentId(),
                                event.getDocumentId())
                        && sameHash(document.getReviewPdfHash(),
                                event.getDocumentHash())
                        && (legacyPayload.equals(event.getEventPayload())
                                || canonicalPayloadPrefix.equals(
                                        event.getEventPayload())
                                || (event.getEventPayload() != null
                                        && event.getEventPayload().startsWith(
                                                canonicalPayloadPrefix
                                                        + ";expectedVersion="))));
    }

    void assertSameInitialReplay(OaSignEvent existing,
            OaSignPackage signPackage, Long documentId,
            OaSignDocumentReadRequest request, Long currentUserId)
    {
        String requestedPayload = initialEventPayload(request);
        if (!Objects.equals(existing.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(existing.getDocumentId(), documentId)
                || !Objects.equals(existing.getOperatorUserId(),
                        signPackage.getEmployeeId())
                || !Objects.equals(existing.getOperatorUserId(), currentUserId)
                || !EMPLOYEE_ROLE.equals(existing.getOperatorRole())
                || !requestedPayload.equals(existing.getEventPayload())
                || !sameHash(existing.getDocumentHash(),
                        request.getReviewPdfHash()))
        {
            throw new ServiceException("阅读确认请求编号已用于不同内容");
        }
    }

    void validateFinalRequest(OaSignFinalDocumentReadRequest request)
    {
        if (request == null || StringUtils.isBlank(request.getRequestId()))
        {
            throw new ServiceException("最终合同打开记录不完整");
        }
    }

    void validateFinalEvidence(OaSignPackage signPackage,
            OaSignPackageDocument document,
            OaSignFinalDocumentReadRequest request)
    {
        if (!Objects.equals(signPackage.getFinalDocumentVersion(),
                request.getFinalDocumentVersion())
                || !Objects.equals(document.getFinalDocumentVersion(),
                        request.getFinalDocumentVersion()))
        {
            throw new ServiceException("最终合同版本已变化，请重新打开");
        }
        if (StringUtils.isBlank(document.getFinalPdfUrl())
                || !sameHash(document.getFinalPdfHash(),
                        request.getFinalPdfHash()))
        {
            throw new ServiceException("最终合同文件校验不一致，请刷新后重试");
        }
    }

    String finalEventPayload(OaSignPackage signPackage,
            OaSignPackageDocument document)
    {
        return finalEventPayload(signPackage.getFinalDocumentVersion(),
                document.getFinalPdfHash());
    }

    boolean hasFinalReadEvent(OaSignPackage signPackage,
            OaSignPackageDocument document, List<OaSignEvent> events)
    {
        String expectedPayload = finalEventPayload(signPackage, document);
        return events != null && events.stream().anyMatch(event ->
                FINAL_READ_EVENT.equals(event.getEventType())
                        && Objects.equals(document.getDocumentId(),
                                event.getDocumentId())
                        && sameHash(document.getFinalPdfHash(),
                                event.getDocumentHash())
                        && expectedPayload.equals(event.getEventPayload()));
    }

    void assertSameFinalReplay(OaSignEvent existing,
            OaSignPackage signPackage, Long documentId,
            OaSignFinalDocumentReadRequest request)
    {
        String requestedPayload = finalEventPayload(
                request.getFinalDocumentVersion(), request.getFinalPdfHash());
        if (!Objects.equals(existing.getPackageId(), signPackage.getPackageId())
                || !Objects.equals(existing.getDocumentId(), documentId)
                || !Objects.equals(existing.getOperatorUserId(),
                        signPackage.getEmployeeId())
                || !EMPLOYEE_ROLE.equals(existing.getOperatorRole())
                || !requestedPayload.equals(existing.getEventPayload())
                || !sameHash(existing.getDocumentHash(),
                        request.getFinalPdfHash()))
        {
            throw new ServiceException("最终合同打开请求编号已用于不同内容");
        }
    }

    private String legacyInitialEventPayload(OaSignPackageDocument document)
    {
        return "documentVersion=" + value(document.getDocumentVersion())
                + ";reviewPdfHash=" + value(document.getReviewPdfHash());
    }

    private String initialEventPayload(String documentVersion,
            String reviewPdfHash)
    {
        return "documentVersion=" + value(documentVersion)
                + ";reviewPdfHash="
                + value(reviewPdfHash).toLowerCase(Locale.ROOT);
    }

    private String finalEventPayload(String finalDocumentVersion,
            String finalPdfHash)
    {
        return "version=" + value(finalDocumentVersion)
                + ";hash=" + value(finalPdfHash).toLowerCase(Locale.ROOT);
    }

    private boolean sameHash(String left, String right)
    {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String value(String value)
    {
        return value == null ? "" : value;
    }
}
