package com.erp.oa.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.utils.StringUtils;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;

/**
 * Owns employee-facing document delivery flags and event visibility.
 *
 * <p>Template metadata is normalized when a document snapshot is created.
 * Legacy document rows fall back to the immutable template-type catalog, while
 * unknown types fail closed and remain hidden.</p>
 */
final class OaSignPackageDocumentDeliveryPolicy
{
    private static final String YES = "Y";
    private static final String NO = "N";

    DeliveryRequirements deliveryRequirements(OaSignTemplate template)
    {
        OaSignTemplateType.Option type =
                OaSignTemplateType.require(template.getTemplateType());
        String employeeVisible = type.isEmployeeVisible()
                ? normalizeFlag(template.getEmployeeVisible(), YES, "员工可见要求")
                : NO;
        String employeeSignRequired = NO;
        String readConfirmationRequired = NO;
        if (YES.equals(employeeVisible))
        {
            employeeSignRequired = normalizeFlag(
                    template.getEmployeeSignRequired(),
                    type.isEmployeeSignRequired() ? YES : NO,
                    "员工签署要求");
            readConfirmationRequired = normalizeFlag(
                    template.getReadConfirmationRequired(),
                    type.isReadConfirmationRequired() ? YES : NO,
                    "阅读确认要求");
            if (YES.equals(employeeSignRequired))
            {
                readConfirmationRequired = YES;
            }
        }
        return new DeliveryRequirements(
                employeeVisible, readConfirmationRequired, employeeSignRequired);
    }

    List<OaSignPackageDocument> employeeVisibleDocuments(
            List<OaSignPackageDocument> documents)
    {
        if (documents == null || documents.isEmpty())
        {
            return List.of();
        }
        return documents.stream()
                .filter(this::isEmployeeVisible)
                .toList();
    }

    List<OaSignEvent> employeeVisibleEvents(List<OaSignEvent> events,
            Collection<OaSignPackageDocument> visibleDocuments)
    {
        if (events == null || events.isEmpty())
        {
            return List.of();
        }
        Set<Long> visibleDocumentIds = new HashSet<>();
        if (visibleDocuments != null)
        {
            for (OaSignPackageDocument document : visibleDocuments)
            {
                if (document != null && document.getDocumentId() != null)
                {
                    visibleDocumentIds.add(document.getDocumentId());
                }
            }
        }
        return events.stream()
                .filter(event -> event != null
                        && (event.getDocumentId() == null
                                || visibleDocumentIds.contains(event.getDocumentId())))
                .toList();
    }

    boolean isEmployeeVisible(OaSignPackageDocument document)
    {
        if (document == null || NO.equalsIgnoreCase(document.getEmployeeVisible()))
        {
            return false;
        }
        if (YES.equalsIgnoreCase(document.getEmployeeVisible()))
        {
            return true;
        }
        try
        {
            return OaSignTemplateType.require(
                    document.getTemplateType()).isEmployeeVisible();
        }
        catch (ServiceException ignored)
        {
            return false;
        }
    }

    boolean requiresReadConfirmation(OaSignPackageDocument document)
    {
        if (!isEmployeeVisible(document))
        {
            return false;
        }
        if (isYes(document.getEmployeeSignRequired())
                || isYes(document.getReadConfirmationRequired()))
        {
            return true;
        }
        if (NO.equalsIgnoreCase(document.getReadConfirmationRequired()))
        {
            return false;
        }
        try
        {
            return OaSignTemplateType.require(
                    document.getTemplateType()).isReadConfirmationRequired();
        }
        catch (ServiceException ignored)
        {
            return false;
        }
    }

    private String normalizeFlag(String value, String defaultValue, String label)
    {
        String normalized =
                StringUtils.isBlank(value) ? defaultValue : value.trim().toUpperCase();
        if (!YES.equals(normalized) && !NO.equals(normalized))
        {
            throw new ServiceException(label + "必须为Y或N");
        }
        return normalized;
    }

    private boolean isYes(String value)
    {
        return YES.equalsIgnoreCase(value);
    }

    record DeliveryRequirements(String employeeVisible,
            String readConfirmationRequired, String employeeSignRequired) {}
}
