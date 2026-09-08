package com.erp.system.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("离职确认请求校验")
class HrOffboardingConfirmRequestTest
{
    private static Validator validator;

    @BeforeAll
    static void createValidator()
    {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("完整标准离职请求通过校验")
    void acceptsCompleteStandardRequest()
    {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    @DisplayName("请求标识和必填业务字段不能为空")
    void rejectsMissingRequiredFields()
    {
        HrOffboardingConfirmRequest request = validRequest();
        request.setRequestId(" ");
        request.setLastWorkingDate(null);
        request.setOffboardingType(null);
        request.setReason(" ");
        request.setSalarySettlementStatus(null);
        request.setAssetHandoverStatus(null);
        request.setNonCompeteDecision(null);
        request.setCompensationAmount(null);

        assertThat(paths(validator.validate(request))).containsExactlyInAnyOrder(
                "requestId", "lastWorkingDate", "offboardingType", "reason",
                "salarySettlementStatus", "assetHandoverStatus",
                "nonCompeteDecision", "compensationAmount");
    }

    @Test
    @DisplayName("补偿金额不得为负数或超过两位小数")
    void rejectsInvalidCompensationAmount()
    {
        HrOffboardingConfirmRequest request = validRequest();
        request.setCompensationAmount(new BigDecimal("-0.01"));
        assertThat(paths(validator.validate(request))).contains("compensationAmount");

        request.setCompensationAmount(new BigDecimal("1.001"));
        assertThat(paths(validator.validate(request))).contains("compensationAmount");
    }

    @Test
    @DisplayName("请求文本字段遵守长度上限")
    void rejectsOversizedText()
    {
        HrOffboardingConfirmRequest request = validRequest();
        request.setRequestId("r".repeat(65));
        request.setReason("原".repeat(501));
        request.setCompensationNote("说".repeat(501));

        assertThat(paths(validator.validate(request))).containsExactlyInAnyOrder(
                "requestId", "reason", "compensationNote");
    }

    @Test
    @DisplayName("嵌套高风险确认执行字段级校验")
    void validatesNestedRiskConfirmation()
    {
        HrOffboardingConfirmRequest request = validRequest();
        HrOffboardingRiskConfirmation confirmation = new HrOffboardingRiskConfirmation();
        confirmation.setConfirmed(true);
        request.setRiskConfirmation(confirmation);

        assertThat(paths(validator.validate(request))).contains(
                "riskConfirmation.employeeId",
                "riskConfirmation.employeeName",
                "riskConfirmation.offboardingType",
                "riskConfirmation.lastWorkingDate",
                "riskConfirmation.operationDate",
                "riskConfirmation.salarySettlementStatus",
                "riskConfirmation.assetHandoverStatus",
                "riskConfirmation.nonCompeteDecision",
                "riskConfirmation.compensationAmount",
                "riskConfirmation.riskStatement",
                "riskConfirmation.reason");
    }

    private static HrOffboardingConfirmRequest validRequest()
    {
        HrOffboardingConfirmRequest request = new HrOffboardingConfirmRequest();
        request.setRequestId("offboard-request-1");
        request.setLastWorkingDate(LocalDate.of(2026, 7, 13));
        request.setOffboardingType(HrOffboardingType.VOLUNTARY_EXPECTED);
        request.setReason("员工按计划主动离职");
        request.setSalarySettlementStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setAssetHandoverStatus(HrOffboardingCompletionStatus.COMPLETED);
        request.setNonCompeteDecision(HrOffboardingNonCompeteDecision.NOT_APPLICABLE);
        request.setCompensationAmount(BigDecimal.ZERO.setScale(2));
        return request;
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations)
    {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
