package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;

@DisplayName("HR合同续签业务策略")
class HrRenewalPolicyTest
{
    private static final LocalDate WINDOW_START =
            LocalDate.of(2026, 7, 12);
    private static final LocalDate WINDOW_END =
            LocalDate.of(2026, 8, 11);

    private final HrRenewalPolicy policy = new HrRenewalPolicy();

    @Test
    @DisplayName("续签请求文本和代码在校验前统一规范化")
    void normalizesRenewalRequest()
    {
        HrRenewalDecisionRequest request = renewRequest();
        request.setRequestId("  renew-1  ");
        request.setContractTypeCode("  labor_contract  ");
        request.setContractTermCode("  fixed_term  ");
        request.setLegalEntityCode("  le-sh  ");
        request.setLegalEntityName("  上海示例有限公司  ");

        policy.normalizeAndValidate(9L, request);

        assertThat(request.getRequestId()).isEqualTo("renew-1");
        assertThat(request.getContractTypeCode())
                .isEqualTo("LABOR_CONTRACT");
        assertThat(request.getContractTermCode())
                .isEqualTo("FIXED_TERM");
        assertThat(request.getLegalEntityCode()).isEqualTo("LE-SH");
        assertThat(request.getLegalEntityName())
                .isEqualTo("上海示例有限公司");

        HrRenewalDecisionRequest decline =
                new HrRenewalDecisionRequest();
        decline.setRequestId("  decline-1  ");
        decline.setDecision(
                HrRenewalDecisionRequest.Decision.DECLINE);
        assertThatCode(() -> policy.normalizeAndValidate(9L, decline))
                .doesNotThrowAnyException();
        assertThat(decline.getRequestId()).isEqualTo("decline-1");
    }

    @Test
    @DisplayName("员工标识、日期、合同代码和法律主体均失败关闭")
    void rejectsMalformedRenewalRequest()
    {
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                0L, renewRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工ID不能为空");

        HrRenewalDecisionRequest reversed = renewRequest();
        reversed.setContractEndDate(reversed.getContractStartDate());
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, reversed))
                .isInstanceOf(ServiceException.class)
                .hasMessage("新合同结束日期必须晚于开始日期");

        HrRenewalDecisionRequest unknownType = renewRequest();
        unknownType.setContractTypeCode("UNKNOWN");
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, unknownType))
                .isInstanceOf(ServiceException.class)
                .hasMessage("合同类型代码不受支持");

        HrRenewalDecisionRequest missingEntity = renewRequest();
        missingEntity.setLegalEntityId(null);
        assertThatThrownBy(() -> policy.normalizeAndValidate(
                9L, missingEntity))
                .isInstanceOf(ServiceException.class)
                .hasMessage("法律主体ID必须为正数");
    }

    @Test
    @DisplayName("扫描窗口参数和在职合同到期边界独立校验")
    void validatesScanWindowAndEligibility()
    {
        assertThatCode(() -> policy.validateScanParameters(
                9L, WINDOW_START, WINDOW_END))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateScanParameters(
                9L, WINDOW_END, WINDOW_START))
                .isInstanceOf(ServiceException.class)
                .hasMessage("续签扫描参数无效");

        HrEmployeeSigningSnapshot snapshot = activeSnapshot();
        snapshot.setContractEndDate(WINDOW_START);
        assertThat(policy.eligibleForScan(
                snapshot, WINDOW_START, WINDOW_END)).isTrue();
        snapshot.setContractEndDate(WINDOW_END);
        assertThat(policy.eligibleForScan(
                snapshot, WINDOW_START, WINDOW_END)).isTrue();
        snapshot.setContractEndDate(WINDOW_END.plusDays(1));
        assertThat(policy.eligibleForScan(
                snapshot, WINDOW_START, WINDOW_END)).isFalse();
        snapshot.setContractEndDate(WINDOW_START);
        snapshot.setEmployeeStatus("离职");
        assertThat(policy.eligibleForScan(
                snapshot, WINDOW_START, WINDOW_END)).isFalse();
    }

    @Test
    @DisplayName("冻结旧合同必须具备有效日期、代码和法律主体")
    void validatesFrozenOldContract()
    {
        assertThatCode(() -> policy.validateBeforeSnapshot(
                activeSnapshot())).doesNotThrowAnyException();

        HrEmployeeSigningSnapshot incomplete = activeSnapshot();
        incomplete.setLegalEntityName(" ");
        assertThatThrownBy(() -> policy.validateBeforeSnapshot(
                incomplete))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工旧法律主体信息不完整");

        HrEmployeeSigningSnapshot invalid = activeSnapshot();
        invalid.setContractTermCode("UNKNOWN");
        assertThatThrownBy(() -> policy.validateBeforeSnapshot(
                invalid))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工旧合同日期或代码无效");
    }

    @Test
    @DisplayName("续签次数空值归零且负数和溢出前值均拒绝")
    void normalizesRenewalCountAndRejectsOverflow()
    {
        HrEmployeeSigningSnapshot snapshot = activeSnapshot();
        snapshot.setRenewalCount(null);
        assertThat(policy.renewalCount(snapshot)).isZero();

        snapshot.setRenewalCount(-1);
        assertThatThrownBy(() -> policy.renewalCount(snapshot))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工续签次数无效");

        snapshot.setRenewalCount(Integer.MAX_VALUE);
        assertThatThrownBy(() -> policy.renewalCount(snapshot))
                .isInstanceOf(ServiceException.class)
                .hasMessage("员工续签次数无效");
    }

    @Test
    @DisplayName("新合同必须晚于旧周期且周期键只使用冻结事实")
    void validatesTransitionAndDerivesCycleKey()
    {
        HrEmployeeSigningSnapshot before = activeSnapshot();
        HrRenewalDecisionRequest request = renewRequest();
        assertThatCode(() -> policy.validateTransition(before, request))
                .doesNotThrowAnyException();
        assertThat(policy.cycleKey(9L, before.getContractEndDate(), 2))
                .isEqualTo("9:2026-08-01:2");

        request.setContractStartDate(before.getContractEndDate());
        assertThatThrownBy(() -> policy.validateTransition(
                before, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("新合同开始日期必须严格晚于旧合同结束日期");
    }

    @Test
    @DisplayName("续签快照只应用新合同和递增后的冻结次数")
    void appliesRenewalSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot = activeSnapshot();

        policy.apply(snapshot, renewRequest(), 3);

        assertThat(snapshot.getContractStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(snapshot.getContractEndDate())
                .isEqualTo(LocalDate.of(2027, 8, 1));
        assertThat(snapshot.getContractTypeCode())
                .isEqualTo("LABOR_CONTRACT");
        assertThat(snapshot.getLegalEntityCode()).isEqualTo("LE-SH");
        assertThat(snapshot.getRenewalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("幂等重放同时核对冻结payload和当前合同终态")
    void matchesFrozenPayloadAndCurrentState()
    {
        HrRenewalDecisionRequest request = renewRequest();
        HrEmployeeSigningSnapshot expected = activeSnapshot();
        policy.apply(expected, request, 3);
        HrEmployeeSigningSnapshot current = activeSnapshot();
        policy.apply(current, request, 3);

        assertThat(policy.matchesRequest(expected, request)).isTrue();
        assertThat(policy.sameState(current, expected)).isTrue();

        request.setLegalEntityName("另一个主体");
        assertThat(policy.matchesRequest(expected, request)).isFalse();
        request.setLegalEntityName("上海示例有限公司");
        current.setRenewalCount(4);
        assertThat(policy.sameState(current, expected)).isFalse();
    }

    private HrRenewalDecisionRequest renewRequest()
    {
        HrRenewalDecisionRequest request =
                new HrRenewalDecisionRequest();
        request.setRequestId("renew-1");
        request.setDecision(
                HrRenewalDecisionRequest.Decision.RENEW);
        request.setContractStartDate(LocalDate.of(2026, 8, 2));
        request.setContractEndDate(LocalDate.of(2027, 8, 1));
        request.setContractTypeCode("LABOR_CONTRACT");
        request.setContractTermCode("FIXED_TERM");
        request.setLegalEntityId(300L);
        request.setLegalEntityCode("LE-SH");
        request.setLegalEntityName("上海示例有限公司");
        return request;
    }

    private HrEmployeeSigningSnapshot activeSnapshot()
    {
        HrEmployeeSigningSnapshot snapshot =
                new HrEmployeeSigningSnapshot();
        snapshot.setEmployeeId(9L);
        snapshot.setEmployeeStatus("在职");
        snapshot.setContractStartDate(LocalDate.of(2025, 8, 2));
        snapshot.setContractEndDate(LocalDate.of(2026, 8, 1));
        snapshot.setContractTypeCode("LABOR_CONTRACT");
        snapshot.setContractTermCode("FIXED_TERM");
        snapshot.setRenewalCount(2);
        snapshot.setLegalEntityId(300L);
        snapshot.setLegalEntityCode("LE-SH");
        snapshot.setLegalEntityName("上海示例有限公司");
        return snapshot;
    }
}
