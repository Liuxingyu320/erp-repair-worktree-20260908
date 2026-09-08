package com.erp.inventory.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@DisplayName("V2调拨收货请求 Bean Validation")
class InvTransferShipmentReceiptCreateRequestValidationTest
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
    @DisplayName("完整服务端规划回传可以通过结构校验")
    void shouldAcceptStructurallyValidServerPlanEcho()
    {
        assertThat(VALIDATOR.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("规划版本依据到货时间与完成标志均为必填")
    void shouldRequireReceiptPlanEnvelope()
    {
        InvTransferShipmentReceiptCreateRequest request = validRequest();
        request.setReceiptPlanVersion("NOT-A-HASH");
        request.setBasis("client-selection");
        request.setArrivedTime(null);
        request.setFinalizeShipment(null);

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "receiptPlanVersion", "basis", "arrivedTime",
                "finalizeShipment");
    }

    @Test
    @DisplayName("嵌套分配拒绝负数和非法来源标识")
    void shouldRejectInvalidNestedAllocation()
    {
        InvTransferShipmentReceiptCreateRequest request = validRequest();
        InvTransferShipmentReceiptAllocationRequest allocation =
                request.getAllocations().get(0);
        allocation.setShipmentAllocationId(0L);
        allocation.setDamagedQuantity(new BigDecimal("-0.0001"));

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "allocations[0].shipmentAllocationId",
                "allocations[0].damagedQuantity");
    }

    @Test
    @DisplayName("请求列表采用防御性复制")
    void shouldDefensivelyCopyMutableLists()
    {
        InvTransferShipmentReceiptCreateRequest request = validRequest();
        List<InvTransferShipmentReceiptAllocationRequest> allocations =
                new java.util.ArrayList<>(request.getAllocations());

        request.setAllocations(allocations);
        allocations.clear();

        assertThat(request.getAllocations()).hasSize(1);
    }

    private static Set<String> invalidProperties(
            InvTransferShipmentReceiptCreateRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static InvTransferShipmentReceiptCreateRequest validRequest()
    {
        InvTransferShipmentReceiptAllocationRequest allocation =
                new InvTransferShipmentReceiptAllocationRequest();
        allocation.setShipmentAllocationId(81L);
        allocation.setAcceptedLocationId(901L);
        allocation.setAcceptedQuantity(BigDecimal.ONE);
        allocation.setDamagedQuantity(BigDecimal.ZERO);
        allocation.setShortageQuantity(BigDecimal.ZERO);

        InvTransferShipmentReceiptCreateRequest request =
                new InvTransferShipmentReceiptCreateRequest();
        request.setReceiptPlanVersion("a".repeat(64));
        request.setBasis("server-recommendation");
        request.setArrivedTime(new Date(1_752_637_800_000L));
        request.setFinalizeShipment(Boolean.FALSE);
        request.setAllocations(List.of(allocation));
        return request;
    }
}
