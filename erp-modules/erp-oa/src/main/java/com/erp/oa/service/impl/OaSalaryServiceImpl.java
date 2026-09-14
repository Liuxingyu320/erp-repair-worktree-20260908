package com.erp.oa.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.common.security.utils.SecurityUtils;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.payroll.AttendancePayrollMapper;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.erp.oa.domain.OaSalaryConfig;
import com.erp.oa.domain.OaSalaryEmployee;
import com.erp.oa.domain.OaSalaryRecord;
import com.erp.oa.domain.vo.OaSalaryAttendanceIssueVo;
import com.erp.oa.domain.vo.OaSalaryAttendancePreflightVo;
import com.erp.oa.mapper.OaDeptScopeMapper;
import com.erp.oa.mapper.OaSalaryConfigMapper;
import com.erp.oa.mapper.OaSalaryEmployeeMapper;
import com.erp.oa.mapper.OaSalaryRecordMapper;
import com.erp.oa.service.IOaSalaryService;
import com.erp.oa.service.BusinessFeatureGate;

@Service
public class OaSalaryServiceImpl implements IOaSalaryService
{
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.security.service.LegacySalaryWriteGuard legacySalaryWrites =
            new com.erp.common.security.service.LegacySalaryWriteGuard();

    @Autowired
    private OaSalaryConfigMapper configMapper;

    @Autowired
    private OaSalaryRecordMapper salaryMapper;

    @Autowired
    private AttendancePayrollMapper attendancePayrollMapper;

    @Autowired
    private AttendanceTimeCreditMapper attendanceTimeCreditMapper;

    @Autowired
    private OaSalaryEmployeeMapper salaryEmployeeMapper;

    @Autowired
    private OaDeptScopeMapper deptScopeMapper;

    @Autowired
    private ShopScopeService shopScopeService;

    @Autowired
    private BusinessFeatureGate featureGate;

    private static final String ATTENDANCE_SOURCE_VERSION =
            "ATTENDANCE_V2_TIME_CREDIT_V2";
    private static final Set<String> PAYROLL_RESULT_STATUSES = Set.of(
            "NORMAL", "LATE", "EARLY", "LATE_EARLY", "LEAVE_FULL",
            "LEAVE_PARTIAL");

    @Override
    public OaSalaryConfig getConfig(Long shopDeptId, Long selectedShopDeptId)
    {
        Long targetShopDeptId = shopScopeService.resolveRequiredShopDept(
                shopDeptId != null ? shopDeptId : selectedShopDeptId);
        OaSalaryConfig config = configMapper.selectOaSalaryConfigByShopDeptId(targetShopDeptId);
        if (config == null)
        {
            config = defaultConfig(targetShopDeptId);
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OaSalaryConfig saveConfig(OaSalaryConfig config, Long selectedShopDeptId)
    {
        legacySalaryWrites.reject();
        Long shopDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        config.setShopDeptId(shopDeptId);
        OaSalaryConfig existing = configMapper.selectOaSalaryConfigByShopDeptId(shopDeptId);
        if (existing == null)
        {
            config.setCreateBy(SecurityUtils.getUsername());
            configMapper.insertOaSalaryConfig(config);
        }
        else
        {
            config.setConfigId(existing.getConfigId());
            config.setUpdateBy(SecurityUtils.getUsername());
            configMapper.updateOaSalaryConfig(config);
        }
        return configMapper.selectOaSalaryConfigById(config.getConfigId());
    }

    @Override
    public List<OaSalaryRecord> selectMyRecords(OaSalaryRecord record, Long selectedShopDeptId)
    {
        record.setUserId(SecurityUtils.getUserId());
        shopScopeService.appendShopScope(record, selectedShopDeptId);
        return salaryMapper.selectOaSalaryRecordList(record);
    }

    @Override
    public List<OaSalaryRecord> selectAllRecords(OaSalaryRecord record, Long selectedShopDeptId)
    {
        shopScopeService.appendShopScope(record, selectedShopDeptId);
        return salaryMapper.selectOaSalaryRecordList(record);
    }

    @Override
    public OaSalaryRecord getRecordById(Long salaryId, Long selectedShopDeptId)
    {
        return assertAndGetScopedSalary(salaryId, selectedShopDeptId);
    }

    @Override
    public OaSalaryAttendancePreflightVo preflightAttendance(
            Long shopDeptId, String salaryMonth, Long selectedShopDeptId)
    {
        requireAttendanceV2();
        Long targetShopId = shopScopeService.resolveRequiredShopDept(
                shopDeptId != null ? shopDeptId : selectedShopDeptId);
        YearMonth month = salaryMonth(salaryMonth);
        List<DayResult> results = attendanceResults(targetShopId, month);
        List<OaSalaryEmployee> employees = salaryEmployees(targetShopId,
                salaryMonth);
        return preflightAttendance(targetShopId, salaryMonth, results,
                employees);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<OaSalaryRecord> calculateSalary(Long shopDeptId, String salaryMonth, Long selectedShopDeptId)
    {
        legacySalaryWrites.reject();
        com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferSourceGuard.requireReadCommittedWriteTransaction();
        requireAttendanceV2();
        Long targetShopId = shopScopeService.resolveRequiredShopDept(shopDeptId != null ? shopDeptId : selectedShopDeptId);
        YearMonth month = salaryMonth(salaryMonth);
        attendanceTimeCreditMapper.ensurePeriodLock(targetShopId,
                salaryMonth);
        attendanceTimeCreditMapper.lockPeriod(targetShopId, salaryMonth);
        attendanceTimeCreditMapper.lockMonthDayResults(targetShopId, month.atDay(1), month.atEndOfMonth());
        List<DayResult> allResults = attendanceResults(targetShopId, month);
        List<OaSalaryEmployee> employees = salaryEmployees(targetShopId,
                salaryMonth);
        OaSalaryAttendancePreflightVo preflight = preflightAttendance(
                targetShopId, salaryMonth, allResults, employees);
        if (preflight.isBlocked())
        {
            throw new ServiceException(preflightMessage(preflight));
        }
        OaSalaryConfig config = configMapper.selectOaSalaryConfigByShopDeptId(targetShopId);
        if (config == null)
        {
            throw new ServiceException("未配置工资参数，请先配置");
        }

        Map<Long, UserAttendanceAgg> aggMap = new LinkedHashMap<>();
        for (OaSalaryEmployee employee : employees)
        {
            String salaryError = employee.validationError();
            if (salaryError != null)
                throw new ServiceException(formatSalaryEmployeeName(employee) + "：" + salaryError);
        }
        for (OaSalaryEmployee employee : employees)
        {
            UserAttendanceAgg agg = aggMap.computeIfAbsent(employee.getUserId(), k -> new UserAttendanceAgg());
            agg.userId = employee.getUserId();
            agg.userName = employee.getUserName();
            agg.deptId = employee.getDeptId();
            agg.shopDeptId = employee.getShopDeptId() != null ? employee.getShopDeptId() : targetShopId;
            agg.baseSalary = employee.getSalaryTotal();
        }

        for (DayResult row : allResults)
        {
            UserAttendanceAgg agg = aggMap.get(row.userId);
            if (agg == null)
            {
                throw new ServiceException("本月排班员工未进入工资名单，已停止重算");
            }
            requireSettledResult(row);
            int scheduled = nonNegative(row.scheduledMinutes);
            int worked = nonNegative(row.workedMinutes);
            int paidLeave = nonNegative(row.paidLeaveMinutes);
            int unpaidLeave = nonNegative(row.unpaidLeaveMinutes);
            int absence = nonNegative(row.absenceMinutes);
            agg.scheduledMinutes += scheduled;
            agg.workedMinutes += worked;
            agg.paidLeaveMinutes += paidLeave;
            agg.unpaidLeaveMinutes += unpaidLeave;
            agg.absenceMinutes += absence;
            agg.lateTotalMinutes += nonNegative(row.lateMinutes);
            int rawEarly = nonNegative(row.earlyLeaveMinutes);
            int earlyOffset = nonNegative(row.timeCreditOffsetMinutes);
            int rawOvertime = rawOvertime(row, worked, scheduled);
            int overtimeUsed = nonNegative(row.timeCreditUsedMinutes);
            agg.earlyTotalMinutes += rawEarly - earlyOffset;
            agg.overtimeMinutes += rawOvertime - overtimeUsed - nonNegative(row.overtimeTransferredMinutes);
            if (worked > 0) agg.workDays++;
            if (paidLeave + unpaidLeave > 0) agg.leaveDays++;
            if (absence > 0) agg.absentDays++;
        }

        salaryMapper.deleteByShopDeptIdAndMonth(targetShopId, salaryMonth);

        List<OaSalaryRecord> result = new ArrayList<>();
        for (UserAttendanceAgg agg : aggMap.values())
        {
            OaSalaryRecord sr = new OaSalaryRecord();
            sr.setUserId(agg.userId);
            sr.setUserName(agg.userName);
            sr.setDeptId(agg.deptId);
            sr.setShopDeptId(agg.shopDeptId);
            sr.setSalaryMonth(salaryMonth);
            sr.setWorkDays(agg.workDays);
            sr.setLeaveDays(agg.leaveDays);
            sr.setAbsentDays(agg.absentDays);
            sr.setScheduledMinutes(agg.scheduledMinutes);
            sr.setWorkedMinutes(agg.workedMinutes);
            sr.setPaidLeaveMinutes(agg.paidLeaveMinutes);
            sr.setUnpaidLeaveMinutes(agg.unpaidLeaveMinutes);
            sr.setAbsenceMinutes(agg.absenceMinutes);
            sr.setAttendanceSourceVersion(ATTENDANCE_SOURCE_VERSION);
            sr.setLateTotalMinutes(agg.lateTotalMinutes);
            sr.setEarlyTotalMinutes(agg.earlyTotalMinutes);

            BigDecimal overtimeHours = BigDecimal.valueOf(
                    agg.overtimeMinutes).divide(BigDecimal.valueOf(60), 2,
                            RoundingMode.HALF_UP);
            sr.setOvertimeHours(overtimeHours);

            sr.setBaseSalary(agg.baseSalary);
            sr.setLateDeduction(config.getLatePenaltyPerMin().multiply(BigDecimal.valueOf(agg.lateTotalMinutes)));
            sr.setEarlyDeduction(config.getEarlyPenaltyPerMin().multiply(BigDecimal.valueOf(agg.earlyTotalMinutes)));
            int dailyMinutes = dailyMinutes(config);
            int deductibleMinutes = agg.unpaidLeaveMinutes
                    + agg.absenceMinutes;
            sr.setAbsentDeduction(config.getAbsentPenaltyPerDay()
                    .multiply(BigDecimal.valueOf(deductibleMinutes))
                    .divide(BigDecimal.valueOf(dailyMinutes), 2,
                            RoundingMode.HALF_UP));
            BigDecimal attDeduction = sr.getLateDeduction().add(sr.getEarlyDeduction()).add(sr.getAbsentDeduction());
            sr.setAttendanceDeduction(attDeduction);
            sr.setOvertimePay(config.getOvertimePayPerHour().multiply(sr.getOvertimeHours()));
            sr.setOtherBonus(BigDecimal.ZERO);
            sr.setOtherDeduction(BigDecimal.ZERO);
            BigDecimal totalSalary = sr.getBaseSalary().subtract(attDeduction).add(sr.getOvertimePay())
                    .add(sr.getOtherBonus()).subtract(sr.getOtherDeduction());
            if (totalSalary.compareTo(BigDecimal.ZERO) < 0) totalSalary = BigDecimal.ZERO;
            sr.setTotalSalary(totalSalary);
            sr.setStatus("draft");
            sr.setCreateBy(SecurityUtils.getUsername());
            salaryMapper.insertOaSalaryRecord(sr);
            result.add(salaryMapper.selectOaSalaryRecordById(sr.getSalaryId()));
        }
        return result;
    }

    private List<DayResult> attendanceResults(Long targetShopId,
            YearMonth month)
    {
        List<DayResult> rows = attendancePayrollMapper
                .selectPublishedDayResultsForPayroll(targetShopId,
                        month.atDay(1), month.atEndOfMonth());
        return rows == null ? new ArrayList<>() : rows;
    }

    private List<OaSalaryEmployee> salaryEmployees(Long targetShopId,
            String salaryMonth)
    {
        List<OaSalaryEmployee> employees = salaryEmployeeMapper
                .selectSalaryEmployeesByShopDeptId(targetShopId, salaryMonth);
        if (employees == null || employees.isEmpty())
            throw new ServiceException("当前门店无可计算工资员工");
        return employees;
    }

    private OaSalaryAttendancePreflightVo preflightAttendance(
            Long targetShopId, String salaryMonth, List<DayResult> rows,
            List<OaSalaryEmployee> employees)
    {
        List<OaSalaryAttendanceIssueVo> issues = new ArrayList<>();
        Set<String> affectedEmployees = new HashSet<>();
        Set<Long> scheduledUsers = new HashSet<>();
        Set<Long> rosterUsers = new HashSet<>();
        for (OaSalaryEmployee employee : employees)
            if (employee.getUserId() != null) rosterUsers.add(employee.getUserId());
        Set<String> missingRoster = new HashSet<>();
        for (DayResult row : rows)
        {
            if (row.userId != null) scheduledUsers.add(row.userId);
            String employeeKey = affectedEmployeeKey(row);
            if ((row.userId == null || !rosterUsers.contains(row.userId))
                    && missingRoster.add(employeeKey))
            {
                OaSalaryAttendanceIssueVo issue = toIssue(row,
                        "本月排班员工未进入工资名单，请核对员工档案；原工资不会覆盖");
                issue.setCategory("SALARY_ROSTER");
                issues.add(issue);
                affectedEmployees.add(employeeKey);
            }
            String reason = resultIssue(row);
            if (reason != null)
            {
                issues.add(toIssue(row, reason));
                affectedEmployees.add(affectedEmployeeKey(row));
            }
        }
        for (OaSalaryEmployee employee : employees)
        {
            String salaryError = employee.validationError();
            if (salaryError != null)
            {
                OaSalaryAttendanceIssueVo salaryIssue = new OaSalaryAttendanceIssueVo();
                salaryIssue.setUserId(employee.getUserId());
                salaryIssue.setUserName(employee.getUserName());
                salaryIssue.setReason(salaryError);
                salaryIssue.setCategory("SALARY_PROFILE");
                issues.add(salaryIssue);
                affectedEmployees.add("id:" + employee.getUserId());
            }
            if (employee.getUserId() == null
                    || scheduledUsers.contains(employee.getUserId())) continue;
            OaSalaryAttendanceIssueVo issue = new OaSalaryAttendanceIssueVo();
            issue.setUserId(employee.getUserId());
            issue.setUserName(employee.getUserName());
            issue.setReason("本月没有已发布排班");
            issues.add(issue);
            affectedEmployees.add("id:" + employee.getUserId());
        }
        OaSalaryRecord previousQuery = new OaSalaryRecord();
        previousQuery.setShopDeptId(targetShopId);
        previousQuery.setSalaryMonth(salaryMonth);
        List<OaSalaryRecord> previous = salaryMapper.selectOaSalaryRecordList(previousQuery);
        for (OaSalaryRecord record : previous == null ? List.<OaSalaryRecord>of() : previous)
        {
            String employeeKey = "id:" + record.getUserId();
            if ((record.getUserId() == null || !rosterUsers.contains(record.getUserId()))
                    && missingRoster.add(employeeKey))
            {
                OaSalaryAttendanceIssueVo issue = new OaSalaryAttendanceIssueVo();
                issue.setUserId(record.getUserId());
                issue.setUserName(record.getUserName());
                issue.setCategory("SALARY_ROSTER");
                issue.setReason("已有工资员工未进入本次名单，请核对历史排班和员工档案；原工资不会覆盖");
                issues.add(issue);
                affectedEmployees.add(employeeKey);
            }
        }
        issues.sort(Comparator
                .comparing((OaSalaryAttendanceIssueVo issue) -> !"SALARY_PROFILE".equals(issue.getCategory()))
                .thenComparing(OaSalaryAttendanceIssueVo::getWorkDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OaSalaryAttendanceIssueVo::getUserName,
                        Comparator.nullsLast(String::compareTo)));

        int total = issues.size();
        List<OaSalaryAttendanceIssueVo> returned = new ArrayList<>(issues.subList(0, Math.min(20, total)));
        OaSalaryAttendancePreflightVo result = new OaSalaryAttendancePreflightVo();
        result.setShopDeptId(targetShopId);
        result.setSalaryMonth(salaryMonth);
        result.setBlocked(total > 0);
        result.setExceptionRecordCount(total);
        result.setAffectedEmployeeCount(affectedEmployees.size());
        result.setReturnedIssueCount(returned.size());
        result.setTruncated(total > returned.size());
        result.setIssues(returned);
        return result;
    }

    private String resultIssue(DayResult row)
    {
        if (row.dayResultId == null) return "已发布排班缺少每日结果";
        if (row.settledAt == null)
        {
            String codes = String.valueOf(row.exceptionCodes);
            if (codes.contains("MISSING_IN") && codes.contains("MISSING_OUT"))
                return "缺少上下班打卡，且未完成补卡审批";
            if (codes.contains("MISSING_IN")) return "未签到";
            if (codes.contains("MISSING_OUT")) return "未签退";
            return "每日结果未结算";
        }
        int scheduled = value(row.scheduledMinutes);
        int worked = value(row.workedMinutes);
        int paid = value(row.paidLeaveMinutes);
        int unpaid = value(row.unpaidLeaveMinutes);
        int absence = value(row.absenceMinutes);
        int early = value(row.earlyLeaveMinutes);
        int rawOvertime = rawOvertime(row, worked, scheduled);
        int overtimeUsed = value(row.timeCreditUsedMinutes);
        int transferred = value(row.overtimeTransferredMinutes);
        if (value(row.overtimeTransferInvalid) > 0) return "已核定转休来源失效或重算，请先核对原核定";
        int earlyOffset = value(row.timeCreditOffsetMinutes);
        if (scheduled < 0 || worked < 0 || paid < 0 || unpaid < 0
                || absence < 0 || value(row.lateMinutes) < 0
                || early < 0 || rawOvertime < 0 || overtimeUsed < 0
                || earlyOffset < 0 || transferred < 0)
            return "每日结果存在负数";
        if ((long)overtimeUsed + transferred > rawOvertime)
            return "加班抵扣超过当前可核定加班分钟";
        if (earlyOffset > early)
            return "早退抵扣超过当前早退分钟";
        if (row.netOvertimeMinutes != null
                && row.netOvertimeMinutes != rawOvertime - overtimeUsed - transferred)
            return "加班抵扣净额投影不一致";
        if (row.netEarlyLeaveMinutes != null
                && row.netEarlyLeaveMinutes != early - earlyOffset)
            return "早退抵扣净额投影不一致";
        if (Math.min(worked, scheduled) + paid + unpaid + absence
                != scheduled)
            return "每日结果分钟不守恒";
        if (row.resultStatus == null
                || !PAYROLL_RESULT_STATUSES.contains(row.resultStatus))
            return "每日结果状态异常";
        return null;
    }

    private OaSalaryAttendanceIssueVo toIssue(DayResult row, String reason)
    {
        OaSalaryAttendanceIssueVo issue = new OaSalaryAttendanceIssueVo();
        issue.setRecordId(row.dayResultId);
        issue.setUserId(row.userId);
        issue.setUserName(row.userName);
        if (row.businessDate != null)
            issue.setWorkDate(Date.from(row.businessDate.atStartOfDay(
                    ZoneId.of("Asia/Shanghai")).toInstant()));
        issue.setReason(reason);
        return issue;
    }

    private String affectedEmployeeKey(DayResult row)
    {
        if (row.userId != null) return "id:" + row.userId;
        return "name:" + String.valueOf(row.userName) + ":"
                + String.valueOf(row.businessDate);
    }

    private String preflightMessage(OaSalaryAttendancePreflightVo preflight)
    {
        SimpleDateFormat format = new SimpleDateFormat("MM-dd");
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        List<String> details = new ArrayList<>();
        for (OaSalaryAttendanceIssueVo issue : preflight.getIssues().subList(
                0, Math.min(5, preflight.getIssues().size())))
        {
            String employee = hasText(issue.getNickName()) ? issue.getNickName()
                    : hasText(issue.getUserName()) ? issue.getUserName()
                    : issue.getUserId() == null ? "未知员工" : String.valueOf(issue.getUserId());
            String date = issue.getWorkDate() == null ? "员工档案/本月排班" : format.format(issue.getWorkDate());
            details.add(employee + " " + date + "（" + issue.getReason() + "）");
        }
        return "本月有 " + preflight.getExceptionRecordCount() + " 条工资计算待处理项，涉及 "
                + preflight.getAffectedEmployeeCount() + " 名员工，工资尚未计算。请先处理："
                + String.join("、", details);
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private YearMonth salaryMonth(String value)
    {
        try
        {
            if (value == null || !value.matches("\\d{4}-\\d{2}"))
                throw new IllegalArgumentException();
            return YearMonth.parse(value,
                    DateTimeFormatter.ofPattern("uuuu-MM"));
        }
        catch (Exception error)
        {
            throw new ServiceException("工资月份格式应为yyyy-MM");
        }
    }

    private String formatSalaryEmployeeName(OaSalaryEmployee employee)
    {
        if (employee == null)
        {
            return "未知员工";
        }
        if (employee.getUserName() != null && !employee.getUserName().trim().isEmpty())
        {
            return employee.getUserName();
        }
        return employee.getUserId() == null ? "未知员工" : String.valueOf(employee.getUserId());
    }

    private void requireAttendanceV2()
    {
        featureGate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2);
    }

    private void requireSettledResult(DayResult row)
    {
        String issue = resultIssue(row);
        if (issue != null)
            throw new ServiceException("考勤预检与工资聚合状态不一致：" + issue);
    }

    private int dailyMinutes(OaSalaryConfig config)
    {
        if (config.getWorkHoursPerDay() == null
                || config.getWorkHoursPerDay().compareTo(BigDecimal.ZERO) <= 0)
            throw new ServiceException("工资参数中标准日工时必须大于0");
        int minutes = config.getWorkHoursPerDay()
                .multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        if (minutes <= 0) throw new ServiceException("标准日工时无效");
        return minutes;
    }

    private int nonNegative(Integer value)
    {
        return value == null ? 0 : Math.max(0, value);
    }

    private int value(Integer value)
    {
        return value == null ? 0 : value;
    }

    private int rawOvertime(DayResult row, int worked, int scheduled)
    {
        return row.rawOvertimeMinutes == null
                ? Math.max(0, worked - scheduled)
                : row.rawOvertimeMinutes;
    }

    private OaSalaryConfig defaultConfig(Long shopDeptId)
    {
        OaSalaryConfig config = new OaSalaryConfig();
        config.setShopDeptId(shopDeptId);
        config.setWorkStartTime("09:00");
        config.setWorkEndTime("18:00");
        config.setWorkHoursPerDay(new BigDecimal("8.00"));
        config.setLatePenaltyPerMin(new BigDecimal("1.00"));
        config.setEarlyPenaltyPerMin(new BigDecimal("1.00"));
        config.setAbsentPenaltyPerDay(new BigDecimal("200.00"));
        config.setOvertimePayPerHour(new BigDecimal("50.00"));
        return config;
    }

    private static class UserAttendanceAgg
    {
        Long userId;
        String userName;
        Long deptId;
        Long shopDeptId;
        int workDays = 0;
        int leaveDays = 0;
        int absentDays = 0;
        int scheduledMinutes = 0;
        int workedMinutes = 0;
        int paidLeaveMinutes = 0;
        int unpaidLeaveMinutes = 0;
        int absenceMinutes = 0;
        int overtimeMinutes = 0;
        int lateTotalMinutes = 0;
        int earlyTotalMinutes = 0;
        BigDecimal baseSalary = BigDecimal.ZERO;
    }

    private OaSalaryRecord assertAndGetScopedSalary(Long salaryId, Long selectedShopDeptId)
    {
        OaSalaryRecord db = salaryMapper.selectOaSalaryRecordById(salaryId);
        if (db == null) throw new ServiceException("工资记录不存在");
        if (SecurityUtils.isAdmin()) return db;
        Long rootDeptId = shopScopeService.resolveRequiredShopDept(selectedShopDeptId);
        if (deptScopeMapper.countDeptInScope(rootDeptId, db.getShopDeptId()) <= 0)
            throw new ServiceException("无权访问该店铺工资记录");
        return db;
    }

}
