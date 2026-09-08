package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignTemplate;

class OaSignPackageDocumentDeliveryPolicyTest
{
    private final OaSignPackageDocumentDeliveryPolicy policy =
            new OaSignPackageDocumentDeliveryPolicy();

    @Test
    void visibleSigningTemplateDefaultsToReadAndSignRequired()
    {
        OaSignPackageDocumentDeliveryPolicy.DeliveryRequirements requirements =
                policy.deliveryRequirements(
                        template(OaSignTemplateType.ONBOARD_COMMITMENT));

        assertThat(requirements.employeeVisible()).isEqualTo("Y");
        assertThat(requirements.readConfirmationRequired()).isEqualTo("Y");
        assertThat(requirements.employeeSignRequired()).isEqualTo("Y");
    }

    @Test
    void hiddenTemplateTypeCannotBeMadeEmployeeVisibleByMutableFlags()
    {
        OaSignTemplate template =
                template(OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG);
        template.setEmployeeVisible("Y");
        template.setReadConfirmationRequired("Y");
        template.setEmployeeSignRequired("Y");

        OaSignPackageDocumentDeliveryPolicy.DeliveryRequirements requirements =
                policy.deliveryRequirements(template);

        assertThat(requirements.employeeVisible()).isEqualTo("N");
        assertThat(requirements.readConfirmationRequired()).isEqualTo("N");
        assertThat(requirements.employeeSignRequired()).isEqualTo("N");
    }

    @Test
    void explicitHiddenFlagSuppressesReadAndSignRequirements()
    {
        OaSignTemplate template =
                template(OaSignTemplateType.ONBOARD_COMMITMENT);
        template.setEmployeeVisible(" n ");
        template.setReadConfirmationRequired("Y");
        template.setEmployeeSignRequired("Y");

        OaSignPackageDocumentDeliveryPolicy.DeliveryRequirements requirements =
                policy.deliveryRequirements(template);

        assertThat(requirements.employeeVisible()).isEqualTo("N");
        assertThat(requirements.readConfirmationRequired()).isEqualTo("N");
        assertThat(requirements.employeeSignRequired()).isEqualTo("N");
    }

    @Test
    void invalidDeliveryFlagsAreRejectedInsteadOfSilentlyDefaulted()
    {
        OaSignTemplate invalidVisibility =
                template(OaSignTemplateType.ONBOARD_COMMITMENT);
        invalidVisibility.setEmployeeVisible("maybe");
        assertThatThrownBy(() -> policy.deliveryRequirements(invalidVisibility))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工可见要求必须为Y或N");

        OaSignTemplate invalidSign =
                template(OaSignTemplateType.ONBOARD_COMMITMENT);
        invalidSign.setEmployeeSignRequired("maybe");
        assertThatThrownBy(() -> policy.deliveryRequirements(invalidSign))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("员工签署要求必须为Y或N");

        OaSignTemplate invalidRead =
                template(OaSignTemplateType.ONBOARD_HANDBOOK);
        invalidRead.setEmployeeSignRequired("N");
        invalidRead.setReadConfirmationRequired("maybe");
        assertThatThrownBy(() -> policy.deliveryRequirements(invalidRead))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("阅读确认要求必须为Y或N");
    }

    @Test
    void documentVisibilityUsesSnapshotBeforeCatalogAndUnknownTypesFailClosed()
    {
        assertThat(policy.isEmployeeVisible(
                document(1L, OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, "Y")))
                .isTrue();
        assertThat(policy.isEmployeeVisible(
                document(2L, OaSignTemplateType.ONBOARD_COMMITMENT, "N")))
                .isFalse();
        assertThat(policy.isEmployeeVisible(
                document(3L, OaSignTemplateType.ONBOARD_COMMITMENT, null)))
                .isTrue();
        assertThat(policy.isEmployeeVisible(
                document(4L, OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, null)))
                .isFalse();
        assertThat(policy.isEmployeeVisible(document(5L, "UNKNOWN", null)))
                .isFalse();
        assertThat(policy.isEmployeeVisible(null)).isFalse();
    }

    @Test
    void readConfirmationHonorsSigningExplicitNoAndCatalogFallback()
    {
        OaSignPackageDocument signing =
                document(1L, OaSignTemplateType.ONBOARD_COMMITMENT, "Y");
        signing.setEmployeeSignRequired("Y");
        assertThat(policy.requiresReadConfirmation(signing)).isTrue();

        OaSignPackageDocument explicitNo =
                document(2L, OaSignTemplateType.ONBOARD_COMMITMENT, "Y");
        explicitNo.setEmployeeSignRequired("N");
        explicitNo.setReadConfirmationRequired("N");
        assertThat(policy.requiresReadConfirmation(explicitNo)).isFalse();

        OaSignPackageDocument fallback =
                document(3L, OaSignTemplateType.ONBOARD_HANDBOOK, null);
        assertThat(policy.requiresReadConfirmation(fallback)).isTrue();

        OaSignPackageDocument hidden =
                document(4L, OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, null);
        hidden.setReadConfirmationRequired("Y");
        assertThat(policy.requiresReadConfirmation(hidden)).isFalse();
    }

    @Test
    void employeeProjectionFiltersDocumentsAndDocumentScopedEvents()
    {
        OaSignPackageDocument visible =
                document(10L, OaSignTemplateType.ONBOARD_COMMITMENT, "Y");
        OaSignPackageDocument hidden =
                document(20L, OaSignTemplateType.ONBOARD_ARCHIVE_CATALOG, "N");

        List<OaSignPackageDocument> visibleDocuments =
                policy.employeeVisibleDocuments(List.of(visible, hidden));
        List<OaSignEvent> visibleEvents = policy.employeeVisibleEvents(
                List.of(event(1L, null), event(2L, 10L), event(3L, 20L),
                        event(4L, 30L)),
                visibleDocuments);

        assertThat(visibleDocuments).containsExactly(visible);
        assertThat(visibleEvents).extracting(OaSignEvent::getEventId)
                .containsExactly(1L, 2L);
        assertThat(policy.employeeVisibleDocuments(null)).isEmpty();
        assertThat(policy.employeeVisibleEvents(null, visibleDocuments)).isEmpty();
    }

    private OaSignTemplate template(String templateType)
    {
        OaSignTemplate template = new OaSignTemplate();
        template.setTemplateType(templateType);
        return template;
    }

    private OaSignPackageDocument document(Long documentId, String templateType,
            String employeeVisible)
    {
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(documentId);
        document.setTemplateType(templateType);
        document.setEmployeeVisible(employeeVisible);
        return document;
    }

    private OaSignEvent event(Long eventId, Long documentId)
    {
        OaSignEvent event = new OaSignEvent();
        event.setEventId(eventId);
        event.setDocumentId(documentId);
        return event;
    }
}
