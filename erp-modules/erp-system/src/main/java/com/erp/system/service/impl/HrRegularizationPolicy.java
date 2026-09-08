package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.dto.HrRegularizationRequest;
import com.erp.system.support.HrPositionNoFormatter;

/**
 * Pure business rules for regularization request normalization, effective-date
 * validation, canonical post matching, snapshot mutation, and replay checks.
 *
 * <p>The policy does not read or write the database, acquire locks, check
 * operator scope, read the clock, publish events, or own transactions.</p>
 */
final class HrRegularizationPolicy
{
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;

    void normalizeAndValidate(Long employeeId,
            HrRegularizationRequest request)
    {
        if (employeeId == null || employeeId <= 0)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (request == null)
        {
            throw new ServiceException("转正确认请求不能为空");
        }
        request.setRequestId(trim(request.getRequestId()));
        request.setPostCode(trim(request.getPostCode()));
        request.setPostName(trim(request.getPostName()));
        request.setJobGradeCode(code(request.getJobGradeCode()));
        request.setJobGradeName(code(request.getJobGradeName()));
        request.setSalaryVersion(trim(request.getSalaryVersion()));

        requireText(request.getRequestId(), "requestId不能为空");
        requireMaxLength(request.getRequestId(), 64,
                "requestId长度不能超过64个字符");
        requireDate(request.getActualRegularizationDate(),
                "实际转正日期不能为空");
        if (request.getPostId() == null || request.getPostId() <= 0)
        {
            throw new ServiceException("岗位ID必须为正数");
        }
        requireText(request.getPostCode(), "岗位代码不能为空");
        requireMaxLength(request.getPostCode(), 64,
                "岗位代码长度不能超过64个字符");
        requireText(request.getPostName(), "岗位名称不能为空");
        requireMaxLength(request.getPostName(), 50,
                "岗位名称长度不能超过50个字符");
        requireText(request.getJobGradeCode(), "职级代码不能为空");
        requireText(request.getJobGradeName(), "职级名称不能为空");
        requireMaxLength(request.getJobGradeCode(), 64,
                "职级代码长度不能超过64个字符");
        requireMaxLength(request.getJobGradeName(), 64,
                "职级名称长度不能超过64个字符");
        if (!Objects.equals(request.getJobGradeCode(),
                request.getJobGradeName()))
        {
            throw new ServiceException(
                    "当前档案仅支持单一职级字段，职级代码和名称必须一致");
        }
        requireText(request.getSalaryVersion(), "薪资版本不能为空");
        requireMaxLength(request.getSalaryVersion(), 32,
                "薪资版本长度不能超过32个字符");
        validateSalary(request);
    }

    void validateEffectiveDate(HrEmployeeSigningSnapshot before,
            LocalDate effectiveDate, LocalDate businessToday)
    {
        if (effectiveDate.isAfter(businessToday))
        {
            throw new ServiceException(
                    "实际转正日期不能晚于上海业务当天");
        }
        if (before.getEntryDate() != null
                && effectiveDate.isBefore(before.getEntryDate()))
        {
            throw new ServiceException("实际转正日期不能早于入职日期");
        }
        if (before.getProbationStartDate() != null
                && effectiveDate.isBefore(
                        before.getProbationStartDate()))
        {
            throw new ServiceException(
                    "实际转正日期不能早于试用期开始日期");
        }
        if (before.getContractEndDate() != null
                && effectiveDate.isAfter(before.getContractEndDate()))
        {
            throw new ServiceException(
                    "实际转正日期不能晚于合同结束日期");
        }
    }

    void validateCanonicalPost(SysPost canonicalPost,
            HrRegularizationRequest request)
    {
        if (canonicalPost == null || canonicalPost.getPostId() == null)
        {
            throw new ServiceException("岗位不存在");
        }
        if (!"0".equals(trim(canonicalPost.getStatus())))
        {
            throw new ServiceException("岗位已停用");
        }
        String canonicalCode = trim(canonicalPost.getPostCode());
        String canonicalName = trim(canonicalPost.getPostName());
        if (!Objects.equals(canonicalPost.getPostId(),
                request.getPostId())
                || !Objects.equals(canonicalCode,
                        request.getPostCode())
                || !Objects.equals(canonicalName,
                        request.getPostName()))
        {
            throw new ServiceException(
                    "岗位代码或名称与系统主数据不一致");
        }
        requireMaxLength(canonicalCode, 64, "系统岗位代码长度无效");
        requireMaxLength(canonicalName, 50, "系统岗位名称长度无效");
    }

    void apply(HrEmployeeSigningSnapshot snapshot,
            HrRegularizationRequest request, SysPost canonicalPost)
    {
        snapshot.setEmployeeStatus("正式");
        snapshot.setActualRegularizationDate(
                request.getActualRegularizationDate());
        snapshot.setPostId(canonicalPost.getPostId());
        snapshot.setPostCode(trim(canonicalPost.getPostCode()));
        snapshot.setPostName(trim(canonicalPost.getPostName()));
        snapshot.setPositionNo(HrPositionNoFormatter.format(
                snapshot.getEmployeeNo(), canonicalPost.getPostCode()));
        snapshot.setJobGradeCode(request.getJobGradeCode());
        snapshot.setJobGradeName(request.getJobGradeCode());
        snapshot.setBaseSalary(request.getBaseSalary());
        snapshot.setPostSalary(request.getPostSalary());
        snapshot.setFieldAllowance(request.getFieldAllowance());
        snapshot.setPerformanceSalary(request.getPerformanceSalary());
        snapshot.setSalaryTotal(request.getSalaryTotal());
        snapshot.setSalaryVersion(request.getSalaryVersion());
    }

    boolean salaryChanged(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return !moneyEquals(before.getBaseSalary(), after.getBaseSalary())
                || !moneyEquals(before.getPostSalary(),
                        after.getPostSalary())
                || !moneyEquals(before.getFieldAllowance(),
                        after.getFieldAllowance())
                || !moneyEquals(before.getPerformanceSalary(),
                        after.getPerformanceSalary())
                || !moneyEquals(before.getSalaryTotal(),
                        after.getSalaryTotal())
                || !Objects.equals(trim(before.getSalaryVersion()),
                        trim(after.getSalaryVersion()));
    }

    boolean matchesRequest(HrEmployeeSigningSnapshot snapshot,
            HrRegularizationRequest request)
    {
        return snapshot != null
                && Objects.equals(
                        snapshot.getActualRegularizationDate(),
                        request.getActualRegularizationDate())
                && Objects.equals(snapshot.getPostId(),
                        request.getPostId())
                && Objects.equals(trim(snapshot.getPostCode()),
                        request.getPostCode())
                && Objects.equals(trim(snapshot.getPostName()),
                        request.getPostName())
                && Objects.equals(code(snapshot.getJobGradeCode()),
                        request.getJobGradeCode())
                && Objects.equals(code(snapshot.getJobGradeName()),
                        request.getJobGradeName())
                && moneyEquals(snapshot.getBaseSalary(),
                        request.getBaseSalary())
                && moneyEquals(snapshot.getPostSalary(),
                        request.getPostSalary())
                && moneyEquals(snapshot.getFieldAllowance(),
                        request.getFieldAllowance())
                && moneyEquals(snapshot.getPerformanceSalary(),
                        request.getPerformanceSalary())
                && moneyEquals(snapshot.getSalaryTotal(),
                        request.getSalaryTotal())
                && Objects.equals(trim(snapshot.getSalaryVersion()),
                        request.getSalaryVersion());
    }

    boolean sameState(HrEmployeeSigningSnapshot current,
            HrEmployeeSigningSnapshot expected)
    {
        return current != null && expected != null
                && Objects.equals(trim(current.getEmployeeStatus()),
                        trim(expected.getEmployeeStatus()))
                && Objects.equals(
                        current.getActualRegularizationDate(),
                        expected.getActualRegularizationDate())
                && Objects.equals(current.getPostId(),
                        expected.getPostId())
                && Objects.equals(trim(current.getPostCode()),
                        trim(expected.getPostCode()))
                && Objects.equals(trim(current.getPostName()),
                        trim(expected.getPostName()))
                && Objects.equals(code(current.getJobGradeCode()),
                        code(expected.getJobGradeCode()))
                && Objects.equals(code(current.getJobGradeName()),
                        code(expected.getJobGradeName()))
                && moneyEquals(current.getBaseSalary(),
                        expected.getBaseSalary())
                && moneyEquals(current.getPostSalary(),
                        expected.getPostSalary())
                && moneyEquals(current.getFieldAllowance(),
                        expected.getFieldAllowance())
                && moneyEquals(current.getPerformanceSalary(),
                        expected.getPerformanceSalary())
                && moneyEquals(current.getSalaryTotal(),
                        expected.getSalaryTotal())
                && Objects.equals(trim(current.getSalaryVersion()),
                        trim(expected.getSalaryVersion()));
    }

    private void validateSalary(HrRegularizationRequest request)
    {
        BigDecimal base = nonNegative(request.getBaseSalary(), "基本工资");
        BigDecimal post = nonNegative(request.getPostSalary(), "岗位工资");
        BigDecimal field = nonNegative(
                request.getFieldAllowance(), "外勤补贴");
        BigDecimal performance = nonNegative(
                request.getPerformanceSalary(), "绩效工资");
        BigDecimal total = nonNegative(request.getSalaryTotal(), "薪资合计");
        if (total.signum() <= 0)
        {
            throw new ServiceException("薪资合计必须大于0");
        }
        if (base.add(post).add(field).add(performance)
                .compareTo(total) != 0)
        {
            throw new ServiceException(
                    "薪资合计必须等于各薪资项之和");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String label)
    {
        if (value == null || value.signum() < 0)
        {
            throw new ServiceException(
                    label + "不能为空且不能为负数");
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
