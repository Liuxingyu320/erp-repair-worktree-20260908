package com.erp.oa.attendance.leave;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceCalculator;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.PolicySnapshot;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;

/** User-confirmed days are separate from the time interval used by attendance. */
public final class AttendanceLeaveAmountPolicy
{
    public static final Set<String> STANDARD_CODES=Set.of("ANNUAL","COMPENSATORY","PERSONAL","SICK","MARRIAGE","BEREAVEMENT","MATERNITY","PATERNITY","CHILDCARE");
    private AttendanceLeaveAmountPolicy() { }
    public static boolean requiresBalance(LeaveType type)
    {return Boolean.TRUE.equals(type.balanceRequired) || "ANNUAL".equals(type.typeCode) || "COMPENSATORY".equals(type.typeCode);}
    public static LeaveType effectiveType(LeaveType type)
    {
        if(type==null)return null;
        if(requiresBalance(type))type.balanceRequired=true;
        if(requiresBalance(type) || (type.typeCode!=null && STANDARD_CODES.contains(type.typeCode)))type.approvalRequired=true;
        return type;
    }

    public static long units(LeaveRequest request, LeaveType type, PolicySnapshot policy)
    {
        if (request.requestedDays==null)
        {
            if (Set.of("DAY","HALF_DAY").contains(type.unitMode))
                throw new ServiceException("请明确填写申请天数，不按起止时间推算");
            if (request.totalMinutes==null || request.totalMinutes<=0) throw new ServiceException("请假时间分钟无效");
            return AttendanceLeaveBalanceCalculator.units(BigDecimal.valueOf(request.totalMinutes),"MINUTES",null);
        }
        BigDecimal days=request.requestedDays;
        if ("MINUTE".equals(type.unitMode)) throw new ServiceException("当前假种按分钟申请，不能携带天数");
        if (days.signum()<=0 || days.compareTo(BigDecimal.valueOf(366))>0 || days.stripTrailingZeros().scale()>6)
            throw new ServiceException("申请天数必须为正数且不能超过366天或六位小数");
        if ("DAY".equals(type.unitMode) && days.stripTrailingZeros().scale()>0)
            throw new ServiceException("当前假种需按整天申请");
        if ("HALF_DAY".equals(type.unitMode) && days.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale()>0)
            throw new ServiceException("当前假种需按半天递增申请");
        long units=AttendanceLeaveBalanceCalculator.units(days,"DAYS",policy.minutesPerDay);
        long minute=1_000_000L;
        if (type.minMinutes!=null && units<Math.multiplyExact(type.minMinutes.longValue(),minute)) throw new ServiceException("申请天数按配置换算后低于最低时长");
        if (type.maxMinutesPerRequest!=null && units>Math.multiplyExact(type.maxMinutesPerRequest.longValue(),minute)) throw new ServiceException("申请天数按配置换算后超过单次上限");
        int step=type.stepMinutes==null?1:type.stepMinutes;
        if (step<=0 || units%Math.multiplyExact((long)step,minute)!=0) throw new ServiceException("申请天数不符合配置的时长步长");
        return units;
    }
    public static String fingerprint(PolicySnapshot p)
    {
        if(p==null)return null;
        return AttendanceClientRequestSupport.fingerprint("LEAVE_QUOTA_POLICY_V1",p.schemaVersion,p.leaveTypeId,p.leaveTypeVersion,p.unitMode,p.amountUnit,p.balanceRequired,p.ruleId,p.ruleVersion,p.mappingId,p.mappingVersion,p.legalEntityId,p.displayUnit,p.minutesPerDay==null?null:p.minutesPerDay.stripTrailingZeros().toPlainString());
    }
    public static void requireSame(PolicySnapshot expected,PolicySnapshot actual)
    {
        if(expected!=null && !Objects.equals(fingerprint(expected),fingerprint(actual))) throw new ServiceException("请假政策或单位已变化，请重新读取并核对申请");
    }
}
