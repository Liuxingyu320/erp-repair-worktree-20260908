package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.dto.HrOffboardingCompletionStatus;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.domain.dto.HrOffboardingNonCompeteDecision;
import com.erp.system.domain.dto.HrOffboardingRiskConfirmation;
import com.erp.system.domain.dto.HrOffboardingType;

@DisplayName("HR离职业务策略")
class HrOffboardingPolicyTest
{
    private static final LocalDate OPERATION_DATE =
            LocalDate.of(2026, 7, 31);
    private static final String RISK_STATEMENT =
            "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用";

    private final HrOffboardingPolicy policy = new HrOffboardingPolicy();

    @Test
    @DisplayName("请求和二次确认文本在校验前统一去除首尾空白")
    void normalizesRequestAndConfirmationText()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        request.setRequestId("  offboard-1  ");
        request.setReason("  按计划离职  ");
        request.setCompensationNote("   ");
        HrOffboardingRiskConfirmation confirmation =
                validConfirmation(request);
        confirmation.setEmployeeName("  员工甲  ");
        confirmation.setRiskStatement("  " + RISK_STATEMENT + "  ");
        confirmation.setReason("  风险已核对  ");
        request.setRiskConfirmation(confirmation);

        policy.normalizeAndValidate(9L, request);

        assertThat(request.getRequestId()).isEqualTo("offboard-1");
        assertThat(request.getReason()).isEqualTo("按计划离职");
        assertThat(request.getCompensationNote()).isNull();
        assertThat(confirmation.getEmployeeName()).isEqualTo("员工甲");
        assertThat(confirmation.getRiskStatement()).isEqualTo(RISK_STATEMENT);
        assertThat(confirmation.getReason()).isEqualTo("风险已核对");
    }

    @Test
    @DisplayName("员工标识、请求编号和补偿金额约束均失败关闭")
    void rejectsMalformedRequestBeforeBusinessReads()
    {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                0L, standardRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工ID不能为空");

        HrOffboardingConfirmRequest longRequestId = standardRequest();
        longRequestId.setRequestId("x".repeat(65));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, longRequestId))
                .isInstanceOf(ServiceException.class)
                .hasMessage("requestId长度不能超过64个字符");

        HrOffboardingConfirmRequest preciseAmount = standardRequest();
        preciseAmount.setCompensationAmount(new BigDecimal("1.001"));
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, preciseAmount))
                .isInstanceOf(ServiceException.class)
                .hasMessage("补偿金额小数不能超过2位");
    }

    @Test
    @DisplayName("标准当天离职无风险代码")
    void standardSameDayOffboardingIsLowRisk()
    {
        assertThat(policy.riskCodes(standardRequest(), false)).isEmpty();
    }

    @Test
    @DisplayName("所有服务端离职风险按稳定顺序派生")
    void derivesEveryRiskCodeInStableOrder()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        request.setOffboardingType(HrOffboardingType.TERMINATION);
        request.setSalarySettlementStatus(
                HrOffboardingCompletionStatus.PENDING);
        request.setAssetHandoverStatus(
                HrOffboardingCompletionStatus.PENDING);
        request.setNonCompeteDecision(
                HrOffboardingNonCompeteDecision.REQUIRED);
        request.setCompensationAmount(new BigDecimal("1000.00"));
        request.setCompensationNote("协商补偿");

        assertThat(policy.riskCodes(request, true)).containsExactly(
                "HISTORICAL_OFFBOARDING",
                "NON_STANDARD_OFFBOARDING_TYPE",
                "SALARY_SETTLEMENT_PENDING",
                "ASSET_HANDOVER_PENDING",
                "NON_COMPETE_REVIEW_REQUIRED",
                "COMPENSATION_REVIEW_REQUIRED");
    }

    @Test
    @DisplayName("离职快照只应用请求中的稳定业务事实")
    void appliesOffboardingSnapshot()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        request.setCompensationAmount(new BigDecimal("50.00"));
        request.setCompensationNote("补偿说明");
        HrEmployeeSigningSnapshot snapshot = employeeSnapshot();

        policy.apply(snapshot, request);

        assertThat(snapshot.getEmployeeStatus()).isEqualTo("离职");
        assertThat(snapshot.getAccountStatus()).isEqualTo("1");
        assertThat(snapshot.getLeaveDate()).isEqualTo(OPERATION_DATE);
        assertThat(snapshot.getOffboardingType())
                .isEqualTo("VOLUNTARY_EXPECTED");
        assertThat(snapshot.getLeaveReason()).isEqualTo("按计划离职");
        assertThat(snapshot.getSalarySettlementStatus())
                .isEqualTo("COMPLETED");
        assertThat(snapshot.getAssetHandoverStatus())
                .isEqualTo("COMPLETED");
        assertThat(snapshot.getNonCompeteDecision())
                .isEqualTo("NOT_APPLICABLE");
        assertThat(snapshot.getCompensationAmount())
                .isEqualByComparingTo("50.00");
        assertThat(snapshot.getCompensationNote()).isEqualTo("补偿说明");
    }

    @Test
    @DisplayName("结构化二次确认允许金额等值但要求所有冻结事实一致")
    void validatesStructuredRiskConfirmation()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        request.setSalarySettlementStatus(
                HrOffboardingCompletionStatus.PENDING);
        request.setCompensationAmount(new BigDecimal("1000.00"));
        HrOffboardingRiskConfirmation confirmation =
                validConfirmation(request);
        confirmation.setCompensationAmount(new BigDecimal("1000.0"));

        assertThatCode(() -> policy.validateRiskConfirmation(
                confirmation, employeeSnapshot(), request, OPERATION_DATE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("结构化二次确认任一冻结事实变化时拒绝")
    void rejectsMismatchedRiskConfirmation()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        request.setSalarySettlementStatus(
                HrOffboardingCompletionStatus.PENDING);
        HrOffboardingRiskConfirmation confirmation =
                validConfirmation(request);
        confirmation.setOperationDate(OPERATION_DATE.minusDays(1));

        assertThatThrownBy(() -> policy.validateRiskConfirmation(
                confirmation, employeeSnapshot(), request, OPERATION_DATE))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("二次确认内容与当前离职信息一致");
    }

    @Test
    @DisplayName("幂等重放同时核对冻结payload和当前离职终态")
    void matchesFrozenPayloadAndCurrentTerminalState()
    {
        HrOffboardingConfirmRequest request = standardRequest();
        HrEmployeeSigningSnapshot expected = employeeSnapshot();
        policy.apply(expected, request);
        HrEmployeeSigningSnapshot current = employeeSnapshot();
        policy.apply(current, request);

        assertThat(policy.matchesRequest(expected, request)).isTrue();
        assertThat(policy.sameState(current, expected)).isTrue();

        request.setReason("另一个原因");
        assertThat(policy.matchesRequest(expected, request)).isFalse();
        request.setReason("按计划离职");
        current.setAccountStatus("0");
        assertThat(policy.sameState(current, expected)).isFalse();
    }

    private HrOffboardingConfirmRequest standardRequest()
    {
        HrOffboardingConfirmRequest request =
                new HrOffboardingConfirmRequest();
        request.setRequestId("offboard-1");
        request.setLastWorkingDate(OPERATION_DATE);
        request.setOffboardingType(
                HrOffboardingType.VOLUNTARY_EXPECTED);
        request.setReason("按计划离职");
        request.setSalarySettlementStatus(
                HrOffboardingCompletionStatus.COMPLETED);
        request.setAssetHandoverStatus(
                HrOffboardingCompletionStatus.COMPLETED);
        request.setNonCompeteDecision(
                HrOffboardingNonCompeteDecision.NOT_APPLICABLE);
        request.setCompensationAmount(BigDecimal.ZERO);
        return request;
    }

    private HrOffboardingRiskConfirmation validConfirmation(
            HrOffboardingConfirmRequest request)
    {
        HrOffboardingRiskConfirmation confirmation =
                new HrOffboardingRiskConfirmation();
        confirmation.setConfirmed(true);
        confirmation.setEmployeeId(9L);
        confirmation.setEmployeeName("员工甲");
        confirmation.setOffboardingType(request.getOffboardingType());
        confirmation.setLastWorkingDate(request.getLastWorkingDate());
        confirmation.setOperationDate(OPERATION_DATE);
        confirmation.setSalarySettlementStatus(
                request.getSalarySettlementStatus());
        confirmation.setAssetHandoverStatus(
                request.getAssetHandoverStatus());
        confirmation.setNonCompeteDecision(
                request.getNonCompeteDecision());
        confirmation.setCompensationAmount(
                request.getCompensationAmount());
        confirmation.setRiskStatement(RISK_STATEMENT);
        confirmation.setReason("风险已核对");
        return confirmation;
    }

    private HrEmployeeSigningSnapshot employeeSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot =
                new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeName("员工甲");
        snapshot.setEmployeeStatus("在职");
        snapshot.setAccountStatus("0");
        return snapshot;
    }
}
