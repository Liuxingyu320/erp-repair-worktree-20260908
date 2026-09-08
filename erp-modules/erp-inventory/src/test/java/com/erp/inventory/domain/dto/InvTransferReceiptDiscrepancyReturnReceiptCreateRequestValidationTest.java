package com.erp.inventory.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
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

@DisplayName("V2差异退回专属收货请求 Bean Validation")
class InvTransferReceiptDiscrepancyReturnReceiptCreateRequestValidationTest
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
    @DisplayName("完整专属规划回传通过结构校验")
    void shouldAcceptStructurallyValidRequest()
    {
        assertThat(VALIDATOR.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("规划信封必须完整且只接受专属依据")
    void shouldRequireDedicatedPlanEnvelope()
    {
        var request = validRequest();
        request.setReturnReceiptPlanVersion("NOT-A-HASH");
        request.setBasis("server-recommendation");
        request.setArrivedTime(null);
        request.setFinalizeShipment(null);

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "returnReceiptPlanVersion", "basis", "arrivedTime",
                "finalizeShipment");
    }

    @Test
    @DisplayName("嵌套分配拒绝非法标识负数量与空序列号")
    void shouldRejectInvalidNestedAllocation()
    {
        var request = validRequest();
        var allocation = request.getAllocations().get(0);
        allocation.setShipmentAllocationId(0L);
        allocation.setReturnedQuantity(new BigDecimal("-0.0001"));
        allocation.setShortageSerialIds(java.util.Arrays.asList((Long) null));

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "allocations[0].shipmentAllocationId",
                "allocations[0].returnedQuantity",
                "allocations[0].shortageSerialIds[0].<list element>");
    }

    @Test
    @DisplayName("请求与序列号列表采用防御性复制")
    void shouldDefensivelyCopyMutableLists()
    {
        var request = validRequest();
        var allocations = new ArrayList<>(request.getAllocations());
        var serials = new ArrayList<>(List.of(51L));

        request.setAllocations(allocations);
        request.getAllocations().get(0).setReturnedSerialIds(serials);
        allocations.clear();
        serials.clear();

        assertThat(request.getAllocations()).hasSize(1);
        assertThat(request.getAllocations().get(0).getReturnedSerialIds())
                .containsExactly(51L);
    }

    private static Set<String> invalidProperties(
            InvTransferReceiptDiscrepancyReturnReceiptCreateRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static InvTransferReceiptDiscrepancyReturnReceiptCreateRequest
            validRequest()
    {
        var allocation =
                new InvTransferReceiptDiscrepancyReturnReceiptAllocationRequest();
        allocation.setShipmentAllocationId(81L);
        allocation.setQuarantineLocationId(902L);
        allocation.setReturnedQuantity(BigDecimal.ONE);
        allocation.setShortageQuantity(BigDecimal.ZERO);

        var request =
                new InvTransferReceiptDiscrepancyReturnReceiptCreateRequest();
        request.setReturnReceiptPlanVersion("a".repeat(64));
        request.setBasis("return-receipt-quarantine-plan-v1");
        request.setArrivedTime(new Date(1_752_637_800_000L));
        request.setFinalizeShipment(Boolean.FALSE);
        request.setAllocations(List.of(allocation));
        return request;
    }
}
