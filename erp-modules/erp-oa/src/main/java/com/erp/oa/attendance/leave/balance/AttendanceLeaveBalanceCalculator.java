package com.erp.oa.attendance.leave.balance;

import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/** A small, closed vocabulary of HR-configured calculations, never scripts. */
@Component
public class AttendanceLeaveBalanceCalculator
{
    public static long units(BigDecimal amount, String unit, BigDecimal minutesPerDay)
    {
        if (amount == null) throw invalid("额度数值未配置");
        BigDecimal multiplier = switch (unit == null ? "" : unit) {
            case "MINUTES" -> BigDecimal.ONE;
            case "HOURS" -> BigDecimal.valueOf(60);
            case "DAYS" -> minutesPerDay;
            default -> throw invalid("额度单位未配置");
        };
        if (multiplier == null || multiplier.signum() <= 0)
            throw invalid("每日分钟换算未配置");
        try { return amount.multiply(multiplier).movePointRight(6).longValueExact(); }
        catch (ArithmeticException overflow) { throw invalid("额度无法精确换算或超出范围"); }
    }

    public void validate(Rule rule)
    {
        if (rule == null || rule.config == null || rule.effectiveFrom == null
                || rule.effectiveTo == null || rule.effectiveTo.isBefore(rule.effectiveFrom)
                || rule.priority == null || rule.priority < 0 || rule.priority > 10000)
            throw invalid("规则生效区间或优先级未配置");
        RuleConfig c = rule.config;
        require(Set.of("ANNUAL", "COMPENSATORY", "OTHER"), c.leaveCategory, "额度来源类别");
        if ("COMPENSATORY".equals(c.leaveCategory) && !"SOURCE_ONLY".equals(c.calculation))
            throw invalid("调休额度仅接受主管核定来源，不允许自动赠送");
        require(Set.of("FIXED", "TENURE", "SOURCE_ONLY"), c.calculation, "计算方式");
        require(Set.of("WORK_START", "ENTRY"), c.tenureBasis, "年资基础");
        require(Set.of("PERIOD_START", "AS_OF_DATE"), c.tenureAt, "年资核算时点");
        require(Set.of("NONE", "CALENDAR_DAYS"), c.proration, "折算方式");
        require(Set.of("UPFRONT", "EARNED_DAILY"), c.grantTiming, "发放方式");
        require(Set.of("DOWN", "HALF_UP", "UP"), c.rounding, "舍入方式");
        units(BigDecimal.ONE, c.unit, c.minutesPerDay);
        if (c.minutesPerDay != null) storedDecimal(c.minutesPerDay);
        long step = units(c.grantStepMinutes, "MINUTES", BigDecimal.ONE);
        if (step <= 0 || c.expiryMonthsAfterYear == null || c.expiryMonthsAfterYear < 0
                || c.expiryMonthsAfterYear > 120 || c.carryExpiryMonths == null
                || c.carryExpiryMonths < 0 || c.carryExpiryMonths > 120
                || c.carryLimit == null || c.carryLimit.signum() < 0)
            throw invalid("发放步长或到期结转配置无效");
        units(c.carryLimit, c.unit, c.minutesPerDay);
        if ("FIXED".equals(c.calculation)) nonnegative(c.amount, c);
        if ("SOURCE_ONLY".equals(c.calculation) && c.amount != null && c.amount.signum() != 0)
            throw invalid("来源核定型规则不能配置自动赠送额度");
        List<Tier> tiers = rule.tiers == null ? List.of() : rule.tiers;
        if ("TENURE".equals(c.calculation))
        {
            if (tiers.isEmpty() || tiers.size() > 100) throw invalid("年资档位未配置");
            List<Tier> sorted = tiers.stream().sorted(Comparator.comparing(t ->
                    t.minYears == null ? -1 : t.minYears)).toList();
            int end = 0;
            for (int index = 0; index < sorted.size(); index++)
            {
                Tier tier = sorted.get(index);
                if (tier.minYears == null || tier.minYears != end
                        || tier.minYears < 0 || tier.minYears > 200
                        || (tier.maxYearsExclusive == null && index != sorted.size() - 1)
                        || (tier.maxYearsExclusive != null && tier.maxYearsExclusive <= tier.minYears))
                    throw invalid("年资档位必须从零连续且不能重叠");
                nonnegative(tier.amount, c);
                end = tier.maxYearsExclusive == null ? Integer.MAX_VALUE : tier.maxYearsExclusive;
            }
            if (end != Integer.MAX_VALUE) throw invalid("最后年资档位应覆盖后续年资");
        }
        else if (!tiers.isEmpty()) throw invalid("当前计算方式不使用年资档位");
    }

    public long calculate(Rule rule, EmployeeContext employee, LocalDate asOf)
    {
        validate(rule);
        if (employee == null || employee.entryDate == null || employee.entryDate.isAfter(asOf))
            throw invalid("员工入职日期缺失或尚未入职");
        RuleConfig c = rule.config;
        if ("SOURCE_ONLY".equals(c.calculation)) return 0;
        LocalDate yearStart = LocalDate.of(asOf.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(asOf.getYear(), 12, 31);
        LocalDate basis = "WORK_START".equals(c.tenureBasis) ? employee.workStartDate : employee.entryDate;
        if (basis == null || basis.isAfter(asOf)) throw invalid("规则所需基础参加工作日期或入职日期缺失");
        LocalDate tenureDate = "PERIOD_START".equals(c.tenureAt) ? yearStart : asOf;
        int years = basis.isAfter(tenureDate) ? 0 : Period.between(basis, tenureDate).getYears();
        BigDecimal amount = c.amount;
        if ("TENURE".equals(c.calculation))
            amount = rule.tiers.stream().filter(t -> years >= t.minYears
                    && (t.maxYearsExclusive == null || years < t.maxYearsExclusive))
                    .findFirst().orElseThrow(() -> invalid("没有匹配的年资档位")).amount;
        long annual = units(amount, c.unit, c.minutesPerDay);
        LocalDate start = yearStart;
        LocalDate end = "EARNED_DAILY".equals(c.grantTiming) ? asOf : yearEnd;
        boolean prorate = "CALENDAR_DAYS".equals(c.proration) || "EARNED_DAILY".equals(c.grantTiming);
        if (prorate)
        {
            if (employee.entryDate.isAfter(start)) start = employee.entryDate;
            if (rule.effectiveFrom.isAfter(start)) start = rule.effectiveFrom;
            if (rule.effectiveTo.isBefore(end)) end = rule.effectiveTo;
        }
        long numerator = prorate ? Math.max(0, ChronoUnit.DAYS.between(start, end) + 1) : 1;
        long denominator = prorate ? yearStart.lengthOfYear() : 1;
        long step = units(c.grantStepMinutes, "MINUTES", BigDecimal.ONE);
        try {
            return BigDecimal.valueOf(annual).multiply(BigDecimal.valueOf(numerator))
                    .divide(BigDecimal.valueOf(denominator).multiply(BigDecimal.valueOf(step)),
                            0, RoundingMode.valueOf(c.rounding))
                    .multiply(BigDecimal.valueOf(step)).longValueExact();
        } catch (ArithmeticException overflow) { throw invalid("计算额度超出范围"); }
    }
    private static void nonnegative(BigDecimal amount, RuleConfig c)
    {
        if (amount == null || amount.signum() < 0) throw invalid("额度必须明确配置为非负数");
        storedDecimal(amount);
        units(amount, c.unit, c.minutesPerDay);
    }
    private static void storedDecimal(BigDecimal value)
    {
        try { value.setScale(6, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException rounded) { throw invalid("存储金额最多支持六位小数，不能静默舍入"); }
    }
    private static void require(Set<String> allowed, String value, String field)
    { if (value == null || !allowed.contains(value)) throw invalid(field + "未配置或不受支持"); }
    static ServiceException invalid(String message)
    { return new ServiceException("LEAVE_BALANCE_CONFIGURATION: " + message); }
}
