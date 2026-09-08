package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.dto.OaSignDocumentReadRequest;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@DisplayName("初始文档阅读确认持久幂等")
class OaSignDocumentReadIdempotencyTest
{
    private static final String PDF_HASH = "a".repeat(64);
    private final OaSignPackageReadPolicy readPolicy =
            new OaSignPackageReadPolicy();

    @AfterEach
    void clearSecurity()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void dtoRequiresRequestIdAndExpectedPackageVersion()
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<String> properties = validator.validate(new OaSignDocumentReadRequest()).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(properties).contains("requestId", "expectedVersion",
                "documentVersion", "reviewPdfHash");
    }

    @Test
    void exactCommittedReplayReturnsWithoutRepeatingAnyMutation()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        SecurityContextHolder.setUserId("960");
        SecurityContextHolder.setUserName("employee");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(500L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(OaSignPackageStatus.PART_VIEWED);
        signPackage.setVersion(8L);
        signPackage.setDocumentVersion("SP-500-V1");
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(500L);
        document.setEmployeeVisible("Y");
        document.setDocumentVersion("SP-500-V1");
        document.setReviewPdfHash(PDF_HASH);

        OaSignDocumentReadRequest request = request(7L);
        String payload = readPolicy.initialEventPayload(request);
        OaSignEvent persisted = new OaSignEvent();
        persisted.setPackageId(500L);
        persisted.setDocumentId(51L);
        persisted.setEventType("DOCUMENT_READ_CONFIRMED");
        persisted.setOperatorUserId(960L);
        persisted.setOperatorRole("EMPLOYEE");
        persisted.setEventPayload(payload);
        persisted.setDocumentHash(PDF_HASH);

        when(packageMapper.selectOaSignPackageById(500L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(500L)).thenReturn(List.of(document));
        when(eventMapper.selectEventByTypeAndRequestId(
                "DOCUMENT_READ_CONFIRMED", "read-idempotency-1")).thenReturn(persisted);
        when(eventMapper.selectEventsByPackageId(500L)).thenReturn(List.of(persisted));

        OaSignPackage result = service.confirmDocumentRead(500L, 51L, request);

        assertThat(result.getPackageId()).isEqualTo(500L);
        assertThat(signPackage.getVersion()).isEqualTo(8L);
        verify(documentMapper, never()).updateOaSignPackageDocument(any());
        verify(packageMapper, never()).markViewedWithVersion(any(), any(), any(), any(), any());
        verify(eventMapper, never()).insertOaSignEvent(any());
    }

    @Test
    void reusedRequestIdWithChangedPayloadIsRejected()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        SecurityContextHolder.setUserId("960");

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(500L);
        signPackage.setEmployeeId(960L);
        when(packageMapper.selectOaSignPackageById(500L)).thenReturn(signPackage);

        OaSignDocumentReadRequest first = request(7L);
        String firstPayload = readPolicy.initialEventPayload(first);
        OaSignEvent persisted = new OaSignEvent();
        persisted.setPackageId(500L);
        persisted.setDocumentId(51L);
        persisted.setOperatorUserId(960L);
        persisted.setOperatorRole("EMPLOYEE");
        persisted.setEventPayload(firstPayload);
        persisted.setDocumentHash(PDF_HASH);
        when(eventMapper.selectEventByTypeAndRequestId(
                "DOCUMENT_READ_CONFIRMED", "read-idempotency-1")).thenReturn(persisted);

        OaSignDocumentReadRequest changed = request(8L);
        assertThatThrownBy(() -> service.confirmDocumentRead(500L, 51L, changed))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于不同内容");
        verify(eventMapper, never()).insertOaSignEvent(any());
    }

    @Test
    void serviceRejectsMissingOrNegativeExpectedVersion()
    {
        OaSignDocumentReadRequest request = request(null);

        assertThatThrownBy(() -> readPolicy.validateInitialRequest(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");

        request.setExpectedVersion(-1L);
        assertThatThrownBy(() -> readPolicy.validateInitialRequest(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");
    }

    private static OaSignDocumentReadRequest request(Long expectedVersion)
    {
        OaSignDocumentReadRequest request = new OaSignDocumentReadRequest();
        request.setRequestId("read-idempotency-1");
        request.setExpectedVersion(expectedVersion);
        request.setDocumentVersion("SP-500-V1");
        request.setReviewPdfHash(PDF_HASH.toUpperCase());
        return request;
    }
}
