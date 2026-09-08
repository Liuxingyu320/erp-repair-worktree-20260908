package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.constant.OaSignSigningSequence;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.dto.OaSignPackageFinalizeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@DisplayName("最终合同生成请求持久幂等")
class OaSignFinalizeRequestIdempotencyTest
{
    @AfterEach
    void clearSecurity()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void dtoRequiresRequestIdAndBothOptimisticVersions()
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<String> properties = validator.validate(new OaSignPackageFinalizeRequest()).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());

        assertThat(properties).contains("requestId", "expectedVersion", "expectedTaskVersion");
    }

    @Test
    void persistedRequestHashAcceptsExactReplayAndRejectsChangedVersion()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        SecurityContextHolder.setUserId("101");
        OaSignPackageFinalizeRequest request = request();
        String payloadHash = ReflectionTestUtils.invokeMethod(service,
                "finalizeRequestPayloadHash", 90L, request);
        OaSignEvent persisted = new OaSignEvent();
        persisted.setPackageId(90L);
        persisted.setOperatorUserId(101L);
        persisted.setOperatorRole("HR");
        persisted.setDocumentHash(payloadHash);

        ReflectionTestUtils.invokeMethod(service, "assertSameFinalizeRequestReplay",
                persisted, 90L, payloadHash);

        request.setExpectedTaskVersion(12L);
        String changedHash = ReflectionTestUtils.invokeMethod(service,
                "finalizeRequestPayloadHash", 90L, request);
        assertThat(changedHash).isNotEqualTo(payloadHash);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "assertSameFinalizeRequestReplay", persisted, 90L, changedHash))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已用于不同内容");
    }

    @Test
    void serviceValidationRejectsMissingOrNegativeVersionsBeforeWriting()
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        OaSignPackageFinalizeRequest request = request();
        request.setExpectedVersion(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "validateFinalizeWriteRequest", request, request.getRequestId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");

        request.setExpectedVersion(7L);
        request.setExpectedTaskVersion(-1L);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service,
                "validateFinalizeWriteRequest", request, request.getRequestId()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预期版本");
    }

    private static OaSignPackageFinalizeRequest request()
    {
        OaSignPackageFinalizeRequest request = new OaSignPackageFinalizeRequest();
        request.setLegalEntityId(31L);
        request.setSealId(41L);
        request.setCorrectionReason("  人工更正  ");
        request.setRequestId("finalize-idempotency-1");
        request.setExpectedVersion(7L);
        request.setExpectedTaskVersion(11L);
        request.setSigningSequence(OaSignSigningSequence.SIGNATURE_FIRST);
        return request;
    }
}
