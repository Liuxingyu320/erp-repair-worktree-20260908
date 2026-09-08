package com.erp.inventory.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("V2差异退回发货请求 Bean Validation")
class
        InvTransferReceiptDiscrepancyReturnShipmentCreateRequestValidationTest
{
    private static final ValidatorFactory FACTORY = Validation
            .buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory()
    {
        FACTORY.close();
    }

    @Test
    @DisplayName("完整固定隔离来源计划回传通过结构校验")
    void shouldAcceptValidServerPlanEcho()
    {
        assertThat(VALIDATOR.validate(valid())).isEmpty();
    }

    @Test
    @DisplayName("拒绝客户端来源规划零数量和超精度数量")
    void shouldRejectInvalidPlanEnvelope()
    {
        var request = valid();
        request.setPlanVersion("NOT-A-HASH");
        request.setBasis("client-selection");
        request.setQuantity(new BigDecimal("1.00001"));

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "planVersion", "basis", "quantity");
    }

    private static Set<String> invalidProperties(
            InvTransferReceiptDiscrepancyReturnShipmentCreateRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet());
    }

    private static
            InvTransferReceiptDiscrepancyReturnShipmentCreateRequest valid()
    {
        var value =
                new InvTransferReceiptDiscrepancyReturnShipmentCreateRequest();
        value.setPlanVersion("a".repeat(64));
        value.setBasis("return-quarantine-reservation-v2");
        value.setQuantity(new BigDecimal("5.0000"));
        return value;
    }
}
