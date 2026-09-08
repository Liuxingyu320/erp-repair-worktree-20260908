package com.erp.system.service.impl;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.api.constant.SigningProfileCodes;
import com.erp.system.domain.dto.HrRenewalDecisionRequest;

/**
 * Pure business rules for renewal scanning, request normalization, frozen
 * contract validation, snapshot mutation, cycle identity, and replay checks.
 *
 * <p>The policy does not read or write the database, acquire locks, check
 * operator scope, publish events, or own transactions.</p>
 */
final class HrRenewalPolicy
{
    void validateScanParameters(Long employeeId,
            LocalDate windowStart, LocalDate windowEnd)
    {
        if (employeeId == null || employeeId <= 0
                || windowStart == null || windowEnd == null
                || windowEnd.isBefore(windowStart))
        {
            throw new ServiceException("续签扫描参数无效");
        }
    }

    boolean eligibleForScan(HrEmployeeSigningSnapshot snapshot,
            LocalDate windowStart, LocalDate windowEnd)
    {
        if (snapshot == null || trim(snapshot.getEmployeeStatus()) == null
                || "离职".equals(trim(snapshot.getEmployeeStatus()))
                || snapshot.getContractEndDate() == null)
        {
            return false;
        }
        LocalDate endDate = snapshot.getContractEndDate();
        return !endDate.isBefore(windowStart)
                && !endDate.isAfter(windowEnd);
    }

    void normalizeAndValidate(Long employeeId,
            HrRenewalDecisionRequest request)
    {
        if (employeeId == null || employeeId <= 0)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (request == null)
        {
            throw new ServiceException("续签决定请求不能为空");
        }
        request.setRequestId(trim(request.getRequestId()));
        requireText(request.getRequestId(), "requestId不能为空");
        if (request.getRequestId().length() > 64)
        {
            throw new ServiceException(
                    "requestId长度不能超过64个字符");
        }
        if (request.getDecision() == null)
        {
            throw new ServiceException("续签决定不能为空");
        }
        if (request.getDecision()
                == HrRenewalDecisionRequest.Decision.DECLINE)
        {
            return;
        }

        request.setContractTypeCode(
                code(request.getContractTypeCode()));
        request.setContractTermCode(
                code(request.getContractTermCode()));
        request.setLegalEntityCode(
                code(request.getLegalEntityCode()));
        request.setLegalEntityName(
                trim(request.getLegalEntityName()));
        requireDate(request.getContractStartDate(),
                "新合同开始日期不能为空");
        requireDate(request.getContractEndDate(),
                "新合同结束日期不能为空");
        if (!request.getContractEndDate().isAfter(
                request.getContractStartDate()))
        {
            throw new ServiceException(
                    "新合同结束日期必须晚于开始日期");
        }
        if (!SigningProfileCodes.isKnownContractType(
                request.getContractTypeCode()))
        {
            throw new ServiceException("合同类型代码不受支持");
        }
        if (!SigningProfileCodes.isKnownContractTerm(
                request.getContractTermCode()))
        {
            throw new ServiceException("合同期限代码不受支持");
        }
        if (request.getLegalEntityId() == null
                || request.getLegalEntityId() <= 0)
        {
            throw new ServiceException("法律主体ID必须为正数");
        }
        requireText(request.getLegalEntityCode(),
                "法律主体代码不能为空");
        requireText(request.getLegalEntityName(),
                "法律主体名称不能为空");
    }

    void validateBeforeSnapshot(
            HrEmployeeSigningSnapshot before)
    {
        if (before.getContractStartDate() == null
                || before.getContractEndDate() == null
                || trim(before.getContractTypeCode()) == null
                || trim(before.getContractTermCode()) == null)
        {
            throw new ServiceException("员工旧合同信息不完整");
        }
        if (!before.getContractEndDate().isAfter(
                before.getContractStartDate())
                || !SigningProfileCodes.isKnownContractType(
                        code(before.getContractTypeCode()))
                || !SigningProfileCodes.isKnownContractTerm(
                        code(before.getContractTermCode())))
        {
            throw new ServiceException("员工旧合同日期或代码无效");
        }
        if (before.getLegalEntityId() == null
                || before.getLegalEntityId() <= 0
                || trim(before.getLegalEntityCode()) == null
                || trim(before.getLegalEntityName()) == null)
        {
            throw new ServiceException(
                    "员工旧法律主体信息不完整");
        }
    }

    void validateTransition(HrEmployeeSigningSnapshot before,
            HrRenewalDecisionRequest request)
    {
        if (!request.getContractStartDate().isAfter(
                before.getContractEndDate()))
        {
            throw new ServiceException(
                    "新合同开始日期必须严格晚于旧合同结束日期");
        }
    }

    int renewalCount(HrEmployeeSigningSnapshot snapshot)
    {
        Integer value = snapshot.getRenewalCount();
        if (value == null)
        {
            return 0;
        }
        if (value < 0 || value == Integer.MAX_VALUE)
        {
            throw new ServiceException("员工续签次数无效");
        }
        return value;
    }

    String cycleKey(Long employeeId, LocalDate oldContractEndDate,
            int renewalCount)
    {
        return employeeId + ":" + oldContractEndDate
                + ":" + renewalCount;
    }

    void apply(HrEmployeeSigningSnapshot snapshot,
            HrRenewalDecisionRequest request, int newRenewalCount)
    {
        snapshot.setContractStartDate(request.getContractStartDate());
        snapshot.setContractEndDate(request.getContractEndDate());
        snapshot.setContractTypeCode(request.getContractTypeCode());
        snapshot.setContractTermCode(request.getContractTermCode());
        snapshot.setLegalEntityId(request.getLegalEntityId());
        snapshot.setLegalEntityCode(request.getLegalEntityCode());
        snapshot.setLegalEntityName(request.getLegalEntityName());
        snapshot.setRenewalCount(newRenewalCount);
    }

    boolean matchesRequest(HrEmployeeSigningSnapshot snapshot,
            HrRenewalDecisionRequest request)
    {
        return Objects.equals(snapshot.getContractStartDate(),
                request.getContractStartDate())
                && Objects.equals(snapshot.getContractEndDate(),
                        request.getContractEndDate())
                && Objects.equals(code(snapshot.getContractTypeCode()),
                        request.getContractTypeCode())
                && Objects.equals(code(snapshot.getContractTermCode()),
                        request.getContractTermCode())
                && Objects.equals(snapshot.getLegalEntityId(),
                        request.getLegalEntityId())
                && Objects.equals(code(snapshot.getLegalEntityCode()),
                        request.getLegalEntityCode())
                && Objects.equals(trim(snapshot.getLegalEntityName()),
                        request.getLegalEntityName());
    }

    boolean sameState(HrEmployeeSigningSnapshot left,
            HrEmployeeSigningSnapshot right)
    {
        return left != null && right != null
                && Objects.equals(left.getContractStartDate(),
                        right.getContractStartDate())
                && Objects.equals(left.getContractEndDate(),
                        right.getContractEndDate())
                && Objects.equals(code(left.getContractTypeCode()),
                        code(right.getContractTypeCode()))
                && Objects.equals(code(left.getContractTermCode()),
                        code(right.getContractTermCode()))
                && Objects.equals(left.getLegalEntityId(),
                        right.getLegalEntityId())
                && Objects.equals(code(left.getLegalEntityCode()),
                        code(right.getLegalEntityCode()))
                && Objects.equals(trim(left.getLegalEntityName()),
                        trim(right.getLegalEntityName()))
                && Objects.equals(left.getRenewalCount(),
                        right.getRenewalCount());
    }

    private void requireText(String value, String message)
    {
        if (trim(value) == null)
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

    private String code(String value)
    {
        String normalized = trim(value);
        return normalized == null
                ? null : normalized.toUpperCase(Locale.ROOT);
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
