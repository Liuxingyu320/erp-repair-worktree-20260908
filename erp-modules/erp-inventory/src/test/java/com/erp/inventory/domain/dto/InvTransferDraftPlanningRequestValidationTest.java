package com.erp.inventory.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
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

@DisplayName("调拨草稿库存规划请求 Bean Validation")
class InvTransferDraftPlanningRequestValidationTest
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
    @DisplayName("完整的仓库补货规划请求通过结构校验")
    void shouldAcceptValidWarehousePlanningRequest()
    {
        assertThat(VALIDATOR.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("路线类型和明细均失败关闭非法输入")
    void shouldRejectInvalidRouteAndNestedLine()
    {
        InvTransferDraftPlanningRequest request = validRequest();
        request.setTransferType("unsupported");
        request.setFromDeptId(0L);
        request.setToDeptId(null);
        InvTransferDraftPlanningRequest.Line line =
                request.getDetails().get(0);
        line.setItemType("unknown");
        line.setItemId(0L);
        line.setQuantity(new BigDecimal("1.00001"));

        assertThat(invalidProperties(request)).containsExactlyInAnyOrder(
                "transferType", "fromDeptId", "toDeptId",
                "details[0].itemType", "details[0].itemId",
                "details[0].quantity");
    }

    @Test
    @DisplayName("规划请求防御性复制明细列表")
    void shouldDefensivelyCopyDetails()
    {
        InvTransferDraftPlanningRequest request = validRequest();
        List<InvTransferDraftPlanningRequest.Line> details =
                new ArrayList<>(request.getDetails());

        request.setDetails(details);
        details.clear();

        assertThat(request.getDetails()).hasSize(1);
    }

    private static Set<String> invalidProperties(
            InvTransferDraftPlanningRequest request)
    {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static InvTransferDraftPlanningRequest validRequest()
    {
        InvTransferDraftPlanningRequest.Line line =
                new InvTransferDraftPlanningRequest.Line();
        line.setItemType("product");
        line.setItemId(501L);
        line.setProductId(501L);
        line.setQuantity(new BigDecimal("7"));

        InvTransferDraftPlanningRequest request =
                new InvTransferDraftPlanningRequest();
        request.setTransferType("warehouse");
        request.setFromDeptId(201L);
        request.setToDeptId(301L);
        request.setDetails(List.of(line));
        return request;
    }
}
