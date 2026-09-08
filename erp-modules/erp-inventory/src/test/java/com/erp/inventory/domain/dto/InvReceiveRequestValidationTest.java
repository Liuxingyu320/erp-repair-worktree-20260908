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

@DisplayName("采购收货请求 Bean Validation")
class InvReceiveRequestValidationTest
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
    @DisplayName("实际到货时间是服务端必填字段")
    void shouldRequireActualArrivalTime()
    {
        InvReceiveRequest request = validRequest();
        request.setArrivedTime(null);

        assertThat(invalidProperties(request)).containsExactly("arrivedTime");
    }

    @Test
    @DisplayName("收货追溯文本不能超过数据库列宽")
    void shouldEnforceReceiptTraceDatabaseLengths()
    {
        InvReceiveRequest request = validRequest();
        request.setSupplierBatchNo("S".repeat(101));
        request.setDeliveryNoteNo("D".repeat(101));
        request.setRemark("R".repeat(501));

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "supplierBatchNo", "deliveryNoteNo", "remark");
    }

    @Test
    @DisplayName("数据库列宽边界值可以通过校验")
    void shouldAcceptReceiptTraceValuesAtDatabaseLimits()
    {
        InvReceiveRequest request = validRequest();
        request.setSupplierBatchNo("S".repeat(100));
        request.setDeliveryNoteNo("D".repeat(100));
        request.setRemark("R".repeat(500));

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    private static Set<String> invalidProperties(InvReceiveRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static InvReceiveRequest validRequest()
    {
        InvReceiveItem item = new InvReceiveItem();
        item.setDetailId(1L);
        item.setReceiveQuantity(BigDecimal.ONE);
        InvReceiveRequest request = new InvReceiveRequest();
        request.setWarehouseId(20L);
        request.setArrivedTime(new Date(1_752_637_800_000L));
        request.setItems(List.of(item));
        return request;
    }
}
