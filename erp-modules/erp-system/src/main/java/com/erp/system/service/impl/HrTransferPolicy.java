package com.erp.system.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.domain.HrEmployeeSigningSnapshot;
import com.erp.system.api.domain.SysDept;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysPost;
import com.erp.system.domain.dto.HrEmployeeTransferRequest;
import com.erp.system.domain.dto.HrTransferRiskConfirmation;
import com.erp.system.support.HrPositionNoFormatter;

/**
 * Pure business rules for transfer request normalization, canonical master
 * data validation, snapshot mutation, risk derivation, and replay comparison.
 *
 * <p>The policy does not read or write the database, acquire locks, check
 * operator scope, publish events, or own transaction boundaries.</p>
 */
final class HrTransferPolicy
{
    private static final int MAX_MONEY_INTEGER_DIGITS = 14;
    private static final int MAX_MONEY_SCALE = 2;
    private static final String HISTORICAL_RISK_STATEMENT =
            "该操作将按历史日期补录并立即修改当前员工档案";

    void normalizeAndValidate(Long employeeId,
            HrEmployeeTransferRequest request)
    {
        if (employeeId == null || employeeId <= 0)
        {
            throw new ServiceException("员工ID不能为空");
        }
        if (request == null)
        {
            throw new ServiceException("调岗确认请求不能为空");
        }
        request.setRequestId(trim(request.getRequestId()));
        request.setTargetDeptName(trim(request.getTargetDeptName()));
        request.setPostCode(trim(request.getPostCode()));
        request.setPostName(trim(request.getPostName()));
        request.setJobGradeCode(code(request.getJobGradeCode()));
        request.setJobGradeName(code(request.getJobGradeName()));
        request.setWorkLocation(trim(request.getWorkLocation()));
        request.setWorkCityLevel(trim(request.getWorkCityLevel()));
        request.setDirectSupervisorName(trim(
                request.getDirectSupervisorName()));
        request.setLegalEntityCode(code(request.getLegalEntityCode()));
        request.setLegalEntityName(trim(request.getLegalEntityName()));
        request.setSalaryVersion(trim(request.getSalaryVersion()));

        requireText(request.getRequestId(), "requestId不能为空");
        requireMaxLength(request.getRequestId(), 64,
                "requestId长度不能超过64个字符");
        requireDate(request.getEffectiveDate(), "调岗生效日期不能为空");
        requirePositive(request.getTargetDeptId(), "目标组织ID必须为正数");
        requireText(request.getTargetDeptName(), "目标组织名称不能为空");
        requireMaxLength(request.getTargetDeptName(), 100,
                "目标组织名称长度不能超过100个字符");
        requirePositive(request.getPostId(), "岗位ID必须为正数");
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
        requireText(request.getWorkLocation(), "工作地点不能为空");
        requireMaxLength(request.getWorkLocation(), 100,
                "工作地点长度不能超过100个字符");
        requireText(request.getWorkCityLevel(), "工作城市级别不能为空");
        requireMaxLength(request.getWorkCityLevel(), 64,
                "工作城市级别长度不能超过64个字符");
        requireMaxLength(request.getDirectSupervisorName(), 100,
                "直属主管姓名长度不能超过100个字符");
        requirePositive(request.getLegalEntityId(), "法律主体ID必须为正数");
        requireText(request.getLegalEntityCode(), "法律主体代码不能为空");
        requireMaxLength(request.getLegalEntityCode(), 64,
                "法律主体代码长度不能超过64个字符");
        requireText(request.getLegalEntityName(), "法律主体名称不能为空");
        requireMaxLength(request.getLegalEntityName(), 100,
                "法律主体名称长度不能超过100个字符");
        requireMaxLength(request.getSalaryVersion(), 32,
                "薪资版本长度不能超过32个字符");
        if (request.isAdjustSalary()) validateSalary(request);
        normalizeConfirmation(request.getRiskConfirmation());
    }

    void validateDept(SysDept dept, HrEmployeeTransferRequest request)
    {
        if (dept == null || dept.getDeptId() == null)
        {
            throw new ServiceException("目标组织不存在");
        }
        if (!"0".equals(trim(dept.getStatus()))
                || !"0".equals(trim(dept.getDelFlag())))
        {
            throw new ServiceException("目标组织已停用或删除");
        }
        if (!Objects.equals(dept.getDeptId(), request.getTargetDeptId())
                || !Objects.equals(trim(dept.getDeptName()),
                        request.getTargetDeptName()))
        {
            throw new ServiceException("目标组织名称与系统主数据不一致");
        }
    }

    void validatePost(SysPost post, HrEmployeeTransferRequest request)
    {
        if (post == null || post.getPostId() == null)
        {
            throw new ServiceException("岗位不存在");
        }
        if (!"0".equals(trim(post.getStatus())))
        {
            throw new ServiceException("岗位已停用");
        }
        if (!Objects.equals(post.getPostId(), request.getPostId())
                || !Objects.equals(trim(post.getPostCode()),
                        request.getPostCode())
                || !Objects.equals(trim(post.getPostName()),
                        request.getPostName()))
        {
            throw new ServiceException("岗位代码或名称与系统主数据不一致");
        }
    }

    void validateSupervisor(SysUser supervisor,
            HrEmployeeTransferRequest request)
    {
        if (request.getDirectSupervisorId() == null)
        {
            if (request.getDirectSupervisorName() != null)
            {
                throw new ServiceException("直属主管ID和姓名必须同时提供");
            }
            return;
        }
        requirePositive(request.getDirectSupervisorId(),
                "直属主管ID必须为正数");
        requireText(request.getDirectSupervisorName(),
                "直属主管姓名不能为空");
        if (supervisor == null
                || !"0".equals(trim(supervisor.getStatus()))
                || !"0".equals(trim(supervisor.getDelFlag())))
        {
            throw new ServiceException("直属主管不存在或已停用");
        }
        if (!Objects.equals(trim(supervisor.getNickName()),
                request.getDirectSupervisorName()))
        {
            throw new ServiceException("直属主管姓名与系统主数据不一致");
        }
    }

    void apply(HrEmployeeSigningSnapshot snapshot,
            HrEmployeeTransferRequest request, SysDept dept,
            SysPost post, SysUser supervisor)
    {
        snapshot.setShopDeptId(dept.getDeptId());
        snapshot.setShopDeptName(trim(dept.getDeptName()));
        snapshot.setDeptId(dept.getDeptId());
        snapshot.setDeptName(trim(dept.getDeptName()));
        snapshot.setPostId(post.getPostId());
        snapshot.setPostCode(trim(post.getPostCode()));
        snapshot.setPostName(trim(post.getPostName()));
        snapshot.setPositionNo(HrPositionNoFormatter.format(
                snapshot.getEmployeeNo(), post.getPostCode()));
        snapshot.setJobGradeCode(request.getJobGradeCode());
        snapshot.setJobGradeName(request.getJobGradeCode());
        snapshot.setWorkLocation(request.getWorkLocation());
        snapshot.setWorkCityLevel(request.getWorkCityLevel());
        snapshot.setDirectSupervisorId(
                supervisor == null ? null : supervisor.getUserId());
        snapshot.setDirectSupervisorName(supervisor == null
                ? null : trim(supervisor.getNickName()));
        snapshot.setDepartmentSupervisorName(trim(dept.getLeader()));
        snapshot.setLegalEntityId(request.getLegalEntityId());
        snapshot.setLegalEntityCode(request.getLegalEntityCode());
        snapshot.setLegalEntityName(request.getLegalEntityName());
        if (request.isAdjustSalary())
        {
            snapshot.setBaseSalary(request.getBaseSalary());
            snapshot.setPostSalary(request.getPostSalary());
            snapshot.setFieldAllowance(request.getFieldAllowance());
            snapshot.setPerformanceSalary(request.getPerformanceSalary());
            snapshot.setSalaryTotal(request.getSalaryTotal());
        }
        snapshot.setTransferEffectiveDate(request.getEffectiveDate());
    }

    boolean changed(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after)
    {
        return !Objects.equals(before.getShopDeptId(), after.getShopDeptId())
                || !Objects.equals(before.getDeptId(), after.getDeptId())
                || !Objects.equals(before.getPostId(), after.getPostId())
                || !Objects.equals(code(before.getJobGradeCode()),
                        code(after.getJobGradeCode()))
                || !Objects.equals(trim(before.getWorkLocation()),
                        trim(after.getWorkLocation()))
                || !Objects.equals(trim(before.getWorkCityLevel()),
                        trim(after.getWorkCityLevel()))
                || !Objects.equals(before.getDirectSupervisorId(),
                        after.getDirectSupervisorId())
                || !Objects.equals(trim(
                        before.getDepartmentSupervisorName()),
                        trim(after.getDepartmentSupervisorName()))
                || !Objects.equals(before.getLegalEntityId(),
                        after.getLegalEntityId())
                || !Objects.equals(code(before.getLegalEntityCode()),
                        code(after.getLegalEntityCode()))
                || !Objects.equals(trim(before.getLegalEntityName()),
                        trim(after.getLegalEntityName()))
                || salaryChanged(before, after);
    }

    List<String> riskCodes(HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after, boolean historical)
    {
        List<String> codes = new ArrayList<>();
        if (historical)
        {
            codes.add("HISTORICAL_BACKFILL");
        }
        if (!Objects.equals(before.getLegalEntityId(),
                after.getLegalEntityId()))
        {
            codes.add("LEGAL_ENTITY_CHANGED");
        }
        if (!sameWorkCity(before.getWorkLocation(),
                after.getWorkLocation()))
        {
            codes.add("CROSS_CITY");
        }
        if (gradeRank(after.getJobGradeCode())
                < gradeRank(before.getJobGradeCode()))
        {
            codes.add("JOB_GRADE_DECREASED");
        }
        if (moneyLess(after.getSalaryTotal(), before.getSalaryTotal()))
        {
            codes.add("SALARY_DECREASED");
        }
        return codes;
    }

    void validateHistoricalConfirmation(
            HrTransferRiskConfirmation confirmation,
            HrEmployeeSigningSnapshot before,
            HrEmployeeSigningSnapshot after,
            LocalDate effectiveDate, LocalDate operationDate)
    {
        boolean matches = confirmation != null && confirmation.isConfirmed()
                && Objects.equals(before.getEmployeeId(),
                        confirmation.getEmployeeId())
                && Objects.equals(trim(before.getEmployeeName()),
                        confirmation.getEmployeeName())
                && Objects.equals(before.getDeptId(),
                        confirmation.getBeforeDeptId())
                && Objects.equals(trim(before.getDeptName()),
                        confirmation.getBeforeDeptName())
                && Objects.equals(before.getPostId(),
                        confirmation.getBeforePostId())
                && Objects.equals(trim(before.getPostName()),
                        confirmation.getBeforePostName())
                && Objects.equals(after.getDeptId(),
                        confirmation.getAfterDeptId())
                && Objects.equals(trim(after.getDeptName()),
                        confirmation.getAfterDeptName())
                && Objects.equals(after.getPostId(),
                        confirmation.getAfterPostId())
                && Objects.equals(trim(after.getPostName()),
                        confirmation.getAfterPostName())
                && Objects.equals(effectiveDate,
                        confirmation.getEffectiveDate())
                && Objects.equals(operationDate,
                        confirmation.getOperationDate())
                && HISTORICAL_RISK_STATEMENT.equals(
                        confirmation.getRiskStatement())
                && confirmation.getReason() != null;
        if (!matches)
        {
            throw new ServiceException(
                    "二次确认内容与当前调岗信息不一致");
        }
    }

    boolean matchesRequest(HrEmployeeSigningSnapshot snapshot,
            HrEmployeeTransferRequest request)
    {
        return snapshot != null
                && Objects.equals(snapshot.getTransferEffectiveDate(),
                        request.getEffectiveDate())
                && Objects.equals(snapshot.getDeptId(),
                        request.getTargetDeptId())
                && Objects.equals(trim(snapshot.getDeptName()),
                        request.getTargetDeptName())
                && Objects.equals(snapshot.getPostId(),
                        request.getPostId())
                && Objects.equals(trim(snapshot.getPostCode()),
                        request.getPostCode())
                && Objects.equals(trim(snapshot.getPostName()),
                        request.getPostName())
                && Objects.equals(code(snapshot.getJobGradeCode()),
                        request.getJobGradeCode())
                && Objects.equals(trim(snapshot.getWorkLocation()),
                        request.getWorkLocation())
                && Objects.equals(trim(snapshot.getWorkCityLevel()),
                        request.getWorkCityLevel())
                && Objects.equals(snapshot.getDirectSupervisorId(),
                        request.getDirectSupervisorId())
                && Objects.equals(trim(snapshot.getDirectSupervisorName()),
                        request.getDirectSupervisorName())
                && Objects.equals(snapshot.getLegalEntityId(),
                        request.getLegalEntityId())
                && Objects.equals(code(snapshot.getLegalEntityCode()),
                        request.getLegalEntityCode())
                && Objects.equals(trim(snapshot.getLegalEntityName()),
                        request.getLegalEntityName())
                && (!request.isAdjustSalary() || (moneyEquals(snapshot.getBaseSalary(),
                        request.getBaseSalary())
                && moneyEquals(snapshot.getPostSalary(),
                        request.getPostSalary())
                && moneyEquals(snapshot.getFieldAllowance(),
                        request.getFieldAllowance())
                && moneyEquals(snapshot.getPerformanceSalary(),
                        request.getPerformanceSalary())
                && moneyEquals(snapshot.getSalaryTotal(),
                        request.getSalaryTotal())));
    }

    boolean sameState(HrEmployeeSigningSnapshot current,
            HrEmployeeSigningSnapshot expected)
    {
        return current != null && expected != null
                && Objects.equals(current.getShopDeptId(),
                        expected.getShopDeptId())
                && Objects.equals(trim(current.getShopDeptName()),
                        trim(expected.getShopDeptName()))
                && Objects.equals(current.getDeptId(), expected.getDeptId())
                && Objects.equals(trim(current.getDeptName()),
                        trim(expected.getDeptName()))
                && Objects.equals(current.getPostId(), expected.getPostId())
                && Objects.equals(trim(current.getPostCode()),
                        trim(expected.getPostCode()))
                && Objects.equals(trim(current.getPostName()),
                        trim(expected.getPostName()))
                && Objects.equals(code(current.getJobGradeCode()),
                        code(expected.getJobGradeCode()))
                && Objects.equals(code(current.getJobGradeName()),
                        code(expected.getJobGradeName()))
                && Objects.equals(trim(current.getWorkLocation()),
                        trim(expected.getWorkLocation()))
                && Objects.equals(trim(current.getWorkCityLevel()),
                        trim(expected.getWorkCityLevel()))
                && Objects.equals(current.getDirectSupervisorId(),
                        expected.getDirectSupervisorId())
                && Objects.equals(trim(current.getDirectSupervisorName()),
                        trim(expected.getDirectSupervisorName()))
                && Objects.equals(trim(
                        current.getDepartmentSupervisorName()),
                        trim(expected.getDepartmentSupervisorName()))
                && Objects.equals(current.getLegalEntityId(),
                        expected.getLegalEntityId())
                && Objects.equals(code(current.getLegalEntityCode()),
                        code(expected.getLegalEntityCode()))
                && Objects.equals(trim(current.getLegalEntityName()),
                        trim(expected.getLegalEntityName()))
                && !salaryChanged(current, expected)
                && Objects.equals(trim(current.getSalaryVersion()),
                        trim(expected.getSalaryVersion()));
    }

    String replayMismatchDetail(HrEmployeeSigningSnapshot current,
            HrEmployeeSigningSnapshot expected,
            HrEmployeeTransferRequest request)
    {
        List<String> fields = new ArrayList<>();
        if (!matchesRequest(expected, request))
        {
            fields.add("frozenPayload");
        }
        if (current == null)
        {
            fields.add("currentSnapshot");
        }
        else
        {
            if (!Objects.equals(current.getDeptId(), expected.getDeptId()))
                fields.add("deptId");
            if (!Objects.equals(current.getShopDeptId(),
                    expected.getShopDeptId()))
                fields.add("shopDeptId");
            if (!Objects.equals(trim(current.getDeptName()),
                    trim(expected.getDeptName())))
                fields.add("deptName");
            if (!Objects.equals(current.getPostId(), expected.getPostId()))
                fields.add("postId");
            if (!Objects.equals(trim(current.getPostCode()),
                    trim(expected.getPostCode())))
                fields.add("postCode");
            if (!Objects.equals(trim(current.getPostName()),
                    trim(expected.getPostName())))
                fields.add("postName");
            if (!Objects.equals(code(current.getJobGradeCode()),
                    code(expected.getJobGradeCode())))
                fields.add("jobGradeCode");
            if (!Objects.equals(trim(current.getWorkLocation()),
                    trim(expected.getWorkLocation())))
                fields.add("workLocation");
            if (!Objects.equals(trim(current.getWorkCityLevel()),
                    trim(expected.getWorkCityLevel())))
                fields.add("workCityLevel");
            if (!Objects.equals(current.getDirectSupervisorId(),
                    expected.getDirectSupervisorId()))
                fields.add("directSupervisorId");
            if (!Objects.equals(current.getLegalEntityId(),
                    expected.getLegalEntityId()))
                fields.add("legalEntityId");
            if (!Objects.equals(code(current.getLegalEntityCode()),
                    code(expected.getLegalEntityCode())))
                fields.add("legalEntityCode");
            if (!Objects.equals(trim(current.getLegalEntityName()),
                    trim(expected.getLegalEntityName())))
                fields.add("legalEntityName");
            if (!moneyEquals(current.getBaseSalary(),
                    expected.getBaseSalary()))
                fields.add("baseSalary");
            if (!moneyEquals(current.getPostSalary(),
                    expected.getPostSalary()))
                fields.add("postSalary");
            if (!moneyEquals(current.getFieldAllowance(),
                    expected.getFieldAllowance()))
                fields.add("fieldAllowance");
            if (!moneyEquals(current.getPerformanceSalary(),
                    expected.getPerformanceSalary()))
                fields.add("performanceSalary");
            if (!moneyEquals(current.getSalaryTotal(),
                    expected.getSalaryTotal()))
                fields.add("salaryTotal");
            if (!Objects.equals(trim(current.getSalaryVersion()),
                    trim(expected.getSalaryVersion())))
                fields.add("salaryVersion");
        }
        return "mismatchFields=" + String.join(",", fields);
    }

    private void normalizeConfirmation(
            HrTransferRiskConfirmation confirmation)
    {
        if (confirmation == null)
        {
            return;
        }
        confirmation.setEmployeeName(trim(confirmation.getEmployeeName()));
        confirmation.setBeforeDeptName(trim(
                confirmation.getBeforeDeptName()));
        confirmation.setBeforePostName(trim(
                confirmation.getBeforePostName()));
        confirmation.setAfterDeptName(trim(
                confirmation.getAfterDeptName()));
        confirmation.setAfterPostName(trim(
                confirmation.getAfterPostName()));
        confirmation.setRiskStatement(trim(
                confirmation.getRiskStatement()));
        confirmation.setReason(trim(confirmation.getReason()));
        requireMaxLength(confirmation.getEmployeeName(), 64,
                "二次确认员工姓名长度不能超过64个字符");
        requireMaxLength(confirmation.getBeforeDeptName(), 100,
                "二次确认原组织名称长度不能超过100个字符");
        requireMaxLength(confirmation.getBeforePostName(), 100,
                "二次确认原岗位名称长度不能超过100个字符");
        requireMaxLength(confirmation.getAfterDeptName(), 100,
                "二次确认新组织名称长度不能超过100个字符");
        requireMaxLength(confirmation.getAfterPostName(), 100,
                "二次确认新岗位名称长度不能超过100个字符");
        requireMaxLength(confirmation.getRiskStatement(), 200,
                "二次确认风险说明长度不能超过200个字符");
        requireMaxLength(confirmation.getReason(), 500,
                "历史补录原因长度不能超过500个字符");
    }

    private void validateSalary(HrEmployeeTransferRequest request)
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
            throw new ServiceException("薪资合计必须等于各薪资项之和");
        }
    }

    private boolean sameWorkCity(String left, String right)
    {
        return Objects.equals(cityPrefix(left), cityPrefix(right));
    }

    private String cityPrefix(String location)
    {
        String value = trim(location);
        if (value == null)
        {
            return null;
        }
        int index = value.indexOf('市');
        return index < 0 ? value : value.substring(0, index + 1);
    }

    private int gradeRank(String grade)
    {
        String value = code(grade);
        if (value == null)
        {
            return Integer.MAX_VALUE;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty())
        {
            return Integer.MAX_VALUE;
        }
        try
        {
            return Integer.parseInt(digits);
        }
        catch (NumberFormatException ignored)
        {
            return Integer.MAX_VALUE;
        }
    }

    private boolean moneyLess(BigDecimal left, BigDecimal right)
    {
        return left != null && right != null
                && left.compareTo(right) < 0;
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
                        after.getSalaryTotal());
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

    private void requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException(message);
        }
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
