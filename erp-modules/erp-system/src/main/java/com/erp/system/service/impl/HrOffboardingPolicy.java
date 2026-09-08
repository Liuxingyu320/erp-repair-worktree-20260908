package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.dto.HrOffboardingCompletionStatus;
import com.erp.system.domain.dto.HrOffboardingConfirmRequest;
import com.erp.system.domain.dto.HrOffboardingNonCompeteDecision;
import com.erp.system.domain.dto.HrOffboardingRiskConfirmation;
import com.erp.system.domain.dto.HrOffboardingType;

/**
 * Pure business rules for offboarding request normalization and frozen-state
 * comparison.
 *
 * <p>The policy does not read or write the database, acquire locks, publish
 * events, or own transaction boundaries.</p>
 */
final class HrOffboardingPolicy
{
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;
    private static final String RISK_STATEMENT =
            "我已核对离职类型、最后工作日、工资结算、资产交接、竞业决定及补偿信息，并确认立即执行离职及账号停用";

    void normalizeAndValidate(Long employeeId,
            HrOffboardingConfirmRequest request)
    {
        if (employeeId == null || employeeId <= 0)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (request == null)
        {
            throw new ServiceException("离职确认请求不能为空");
        }
        request.setRequestId(trim(request.getRequestId()));
        request.setReason(trim(request.getReason()));
        request.setCompensationNote(trim(request.getCompensationNote()));
        requireText(request.getRequestId(), "requestId不能为空");
        requireMaxLength(request.getRequestId(), 64,
                "requestId长度不能超过64个字符");
        requireDate(request.getLastWorkingDate(), "最后工作日不能为空");
        if (request.getOffboardingType() == null)
        {
            throw new ServiceException("离职类型不能为空");
        }
        requireText(request.getReason(), "离职原因不能为空");
        requireMaxLength(request.getReason(), 500,
                "离职原因长度不能超过500个字符");
        if (request.getSalarySettlementStatus() == null)
        {
            throw new ServiceException("工资结算状态不能为空");
        }
        if (request.getAssetHandoverStatus() == null)
        {
            throw new ServiceException("资产交接状态不能为空");
        }
        if (request.getNonCompeteDecision() == null)
        {
            throw new ServiceException("竞业决定不能为空");
        }
        nonNegative(request.getCompensationAmount(), "补偿金额");
        requireMaxLength(request.getCompensationNote(), 500,
                "补偿说明长度不能超过500个字符");

        HrOffboardingRiskConfirmation confirmation =
                request.getRiskConfirmation();
        if (confirmation != null)
        {
            confirmation.setEmployeeName(trim(
                    confirmation.getEmployeeName()));
            confirmation.setRiskStatement(trim(
                    confirmation.getRiskStatement()));
            confirmation.setReason(trim(confirmation.getReason()));
            requireMaxLength(confirmation.getEmployeeName(), 64,
                    "二次确认员工姓名长度不能超过64个字符");
            requireMaxLength(confirmation.getRiskStatement(), 200,
                    "二次确认风险说明长度不能超过200个字符");
            requireMaxLength(confirmation.getReason(), 500,
                    "二次确认原因长度不能超过500个字符");
            nonNegative(confirmation.getCompensationAmount(),
                    "二次确认补偿金额");
        }
    }

    void apply(HrEmployeeSigningSnapshot snapshot,
            HrOffboardingConfirmRequest request)
    {
        snapshot.setEmployeeStatus("离职");
        snapshot.setAccountStatus("1");
        snapshot.setLeaveDate(request.getLastWorkingDate());
        snapshot.setOffboardingType(request.getOffboardingType().name());
        snapshot.setLeaveReason(request.getReason());
        snapshot.setSalarySettlementStatus(
                request.getSalarySettlementStatus().name());
        snapshot.setAssetHandoverStatus(
                request.getAssetHandoverStatus().name());
        snapshot.setNonCompeteDecision(
                request.getNonCompeteDecision().name());
        snapshot.setCompensationAmount(request.getCompensationAmount());
        snapshot.setCompensationNote(request.getCompensationNote());
    }

    List<String> riskCodes(HrOffboardingConfirmRequest request,
            boolean historical)
    {
        List<String> risks = new ArrayList<>();
        if (historical)
        {
            risks.add("HISTORICAL_OFFBOARDING");
        }
        if (request.getOffboardingType()
                != HrOffboardingType.VOLUNTARY_EXPECTED)
        {
            risks.add("NON_STANDARD_OFFBOARDING_TYPE");
        }
        if (request.getSalarySettlementStatus()
                != HrOffboardingCompletionStatus.COMPLETED)
        {
            risks.add("SALARY_SETTLEMENT_PENDING");
        }
        if (request.getAssetHandoverStatus()
                != HrOffboardingCompletionStatus.COMPLETED)
        {
            risks.add("ASSET_HANDOVER_PENDING");
        }
        if (request.getNonCompeteDecision()
                != HrOffboardingNonCompeteDecision.NOT_APPLICABLE)
        {
            risks.add("NON_COMPETE_REVIEW_REQUIRED");
        }
        if (request.getCompensationAmount().signum() > 0
                || request.getCompensationNote() != null)
        {
            risks.add("COMPENSATION_REVIEW_REQUIRED");
        }
        return risks;
    }

    void validateRiskConfirmation(
            HrOffboardingRiskConfirmation confirmation,
            HrEmployeeSigningSnapshot before,
            HrOffboardingConfirmRequest request,
            LocalDate operationDate)
    {
        boolean matches = confirmation != null && confirmation.isConfirmed()
                && Objects.equals(before.getEmployeeId(),
                        confirmation.getEmployeeId())
                && Objects.equals(trim(before.getEmployeeName()),
                        confirmation.getEmployeeName())
                && request.getOffboardingType()
                        == confirmation.getOffboardingType()
                && Objects.equals(request.getLastWorkingDate(),
                        confirmation.getLastWorkingDate())
                && Objects.equals(operationDate,
                        confirmation.getOperationDate())
                && request.getSalarySettlementStatus()
                        == confirmation.getSalarySettlementStatus()
                && request.getAssetHandoverStatus()
                        == confirmation.getAssetHandoverStatus()
                && request.getNonCompeteDecision()
                        == confirmation.getNonCompeteDecision()
                && moneyEquals(request.getCompensationAmount(),
                        confirmation.getCompensationAmount())
                && RISK_STATEMENT.equals(confirmation.getRiskStatement())
                && confirmation.getReason() != null;
        if (!matches)
        {
            throw new ServiceException(
                    "高风险离职必须完成二次确认，且二次确认内容与当前离职信息一致");
        }
    }

    boolean matchesRequest(HrEmployeeSigningSnapshot snapshot,
            HrOffboardingConfirmRequest request)
    {
        return snapshot != null
                && Objects.equals(snapshot.getLeaveDate(),
                        request.getLastWorkingDate())
                && Objects.equals(snapshot.getOffboardingType(),
                        request.getOffboardingType().name())
                && Objects.equals(trim(snapshot.getLeaveReason()),
                        request.getReason())
                && Objects.equals(snapshot.getSalarySettlementStatus(),
                        request.getSalarySettlementStatus().name())
                && Objects.equals(snapshot.getAssetHandoverStatus(),
                        request.getAssetHandoverStatus().name())
                && Objects.equals(snapshot.getNonCompeteDecision(),
                        request.getNonCompeteDecision().name())
                && moneyEquals(snapshot.getCompensationAmount(),
                        request.getCompensationAmount())
                && Objects.equals(trim(snapshot.getCompensationNote()),
                        request.getCompensationNote());
    }

    boolean sameState(HrEmployeeSigningSnapshot current,
            HrEmployeeSigningSnapshot expected)
    {
        return current != null && expected != null
                && Objects.equals(current.getEmployeeId(),
                        expected.getEmployeeId())
                && "离职".equals(trim(current.getEmployeeStatus()))
                && "1".equals(trim(current.getAccountStatus()))
                && Objects.equals(current.getLeaveDate(),
                        expected.getLeaveDate());
    }

    private BigDecimal nonNegative(BigDecimal value, String label)
    {
        if (value == null || value.signum() < 0)
        {
            throw new ServiceException(label + "不能为空且不能为负数");
        }
        if (value.scale() > MAX_MONEY_SCALE)
        {
            throw new ServiceException(label + "小数不能超过2位");
        }
        long integerDigits = value.signum() == 0
                ? 0L
                : Math.max((long) value.precision() - value.scale(), 0L);
        if (integerDigits > MAX_MONEY_INTEGER_DIGITS)
        {
            throw new ServiceException(label + "整数不能超过14位");
        }
        return value;
    }

    private boolean moneyEquals(BigDecimal left, BigDecimal right)
    {
        return left == null
                ? right == null
                : right != null && left.compareTo(right) == 0;
    }

    private void requireText(String value, String message)
    {
        if (trim(value) == null)
        {
            throw new ServiceException(message);
        }
    }

    private void requireMaxLength(String value, int maxLength,
            String message)
    {
        if (value != null && value.length() > maxLength)
        {
            throw new ServiceException(message);
        }
    }

    private void requireDate(LocalDate value, String message)
    {
        if (value == null)
        {
            throw new ServiceException(message);
        }
    }

    private String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
