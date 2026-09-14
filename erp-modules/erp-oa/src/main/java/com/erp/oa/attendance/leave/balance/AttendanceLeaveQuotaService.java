package com.erp.oa.attendance.leave.balance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.leave.AttendanceLeaveAmountPolicy;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaModels.*;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Internal bridge: public HTTP identity/scope checks stay in the leave owner. */
@Service
public class AttendanceLeaveQuotaService
{
    private final AttendanceLeaveQuotaMapper mapper;
    private final AttendanceLeaveMapper leaves;
    private final AttendanceLeaveBalanceMapper balances;
    private final AttendanceLeaveBalanceService core;
    private final AttendanceTimeCreditMapper credits;
    private final AttendanceOvertimeTransferSourceGuard sources;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired
    public AttendanceLeaveQuotaService(AttendanceLeaveQuotaMapper mapper,AttendanceLeaveMapper leaves,AttendanceLeaveBalanceMapper balances,
            AttendanceLeaveBalanceService core,AttendanceTimeCreditMapper credits,AttendanceOvertimeTransferSourceGuard sources,ObjectMapper json)
    { this(mapper,leaves,balances,core,credits,sources,json,Clock.systemDefaultZone()); }
    AttendanceLeaveQuotaService(AttendanceLeaveQuotaMapper mapper,AttendanceLeaveMapper leaves,AttendanceLeaveBalanceMapper balances,
            AttendanceLeaveBalanceService core,AttendanceTimeCreditMapper credits,AttendanceOvertimeTransferSourceGuard sources,ObjectMapper json,Clock clock)
    {this.mapper=mapper;this.leaves=leaves;this.balances=balances;this.core=core;this.credits=credits;this.sources=sources;this.json=json;this.clock=clock;}

    public PolicySnapshot copyPolicy(PolicySnapshot policy)
    {return policy==null?null:json.convertValue(policy,PolicySnapshot.class);}

    public LeaveRequest hydrate(LeaveRequest request)
    {
        if(request==null)return null;
        if(request.quotaPolicyJson==null){request.quotaPolicySnapshot=null;request.quotaStatus="LEGACY_NO_QUOTA";return request;}
        try{request.quotaPolicySnapshot=json.readValue(request.quotaPolicyJson,PolicySnapshot.class);}
        catch(Exception invalid){throw new ServiceException("请假额度政策快照无法核对，请HR检查原申请");}
        return request;
    }
    public void validateApprovalSnapshot(Long requestId,com.fasterxml.jackson.databind.JsonNode snapshot)
    {
        LeaveRequest request=hydrate(leaves.selectLeaveRequestById(requestId));
        if(request==null)throw new ServiceException("请假申请不存在");
        if(request.quotaPolicySnapshot==null)return;
        try{
            var policyNode=snapshot.get("quotaPolicySnapshot");
            if(policyNode==null || !policyNode.isObject())throw new ServiceException("审批缺少保存的额度政策快照");
            AttendanceLeaveAmountPolicy.requireSame(request.quotaPolicySnapshot,json.treeToValue(policyNode,PolicySnapshot.class));
            var days=snapshot.get("requestedDays");
            if(request.requestedDays==null ? days!=null && !days.isNull() : days==null || days.isNull() || request.requestedDays.compareTo(new java.math.BigDecimal(days.asText()))!=0)
                throw new ServiceException("审批天数与保存的申请不同");
            var units=snapshot.get("quotaUnits");
            if(request.quotaUnits==null ? units!=null && !units.isNull() : units==null || units.isNull() || !request.quotaUnits.toString().equals(units.asText()))
                throw new ServiceException("审批额度与原占用数量不同");
        }catch(ServiceException business){throw business;}catch(Exception invalid){throw new ServiceException("审批额度快照无法核对");}
    }
    public void confirmLinked(LeaveRequest request,int round)
    {
        transaction();if(request.quotaPolicyJson!=null && request.quotaUnits!=null && Objects.equals(request.businessRound,round))
            must(mapper.updateRequestQuotaStatus(request.leaveRequestId,round,"RESERVED"));
    }

    public void freezeDraft(LeaveRequest request,LeaveType type,PolicySnapshot expected)
    {
        transaction();
        PolicySnapshot current=policy(request,type,true);
        PolicySnapshot expectedPolicy=copyPolicy(expected);
        // MIXED explicitly lets the applicant change their requested amount unit while editing.
        // Compare the frozen policy metadata, then persist the newly confirmed request unit.
        if(expectedPolicy!=null && "MIXED".equals(type.unitMode)
                && ("DAYS".equals(expectedPolicy.amountUnit) || "MINUTES".equals(expectedPolicy.amountUnit)))expectedPolicy.amountUnit=current.amountUnit;
        AttendanceLeaveAmountPolicy.requireSame(expectedPolicy,current);
        long units=AttendanceLeaveAmountPolicy.units(request,type,current);
        request.quotaPolicySnapshot=current;request.quotaPolicyJson=write(current);
        request.quotaUnits=Boolean.TRUE.equals(current.balanceRequired)?units:null;
        request.quotaStatus=request.quotaUnits==null?"NOT_REQUIRED":"NOT_RESERVED";
    }
    public void reserve(LeaveRequest request,LeaveType type,int round)
    {
        transaction();hydrate(request);LocalDate asOf=today();
        if(request.quotaPolicySnapshot==null)
        {
            if(AttendanceLeaveAmountPolicy.requiresBalance(type) || Set.of("DAY","HALF_DAY").contains(type.unitMode))
                throw new ServiceException("历史草稿请先明确天数和当前政策并保存后提交");
            return;
        }
        Event previous=mapper.selectEvent(request.leaveRequestId,round,"RESERVE");
        String fingerprint=reservationFingerprint(request);
        if(previous!=null){sameEvent(previous,fingerprint,"RESERVE");return;}
        FinancialLocks lockedSources=lockFinancialContext(request,true);
        PolicySnapshot current=policy(request,type,true);AttendanceLeaveAmountPolicy.requireSame(request.quotaPolicySnapshot,current);
        long units=AttendanceLeaveAmountPolicy.units(request,type,current);
        if(!Boolean.TRUE.equals(current.balanceRequired)){request.quotaStatus="NOT_REQUIRED";return;}
        if(!Objects.equals(request.quotaUnits,units) || units<=0)throw new ServiceException("保存的额度数量已变化，请重新核对");
        // Automatic rule reconciliation belongs to this authenticated request transaction.
        Balance prepared=core.prepareForLeave(request.userId,request.leaveTypeId);
        if(!"READY".equals(prepared.status) || prepared.availableUnits==null)throw new ServiceException(prepared.reason==null?"额度来源待核对":prepared.reason);
        EmployeeContext employee=core.lockContext(request.userId);
        Account account=core.lockAccount(request.userId,request.leaveTypeId);
        List<Bucket> buckets=balances.selectBuckets(account.accountId,true);
        verifyLockedSources(request,lockedSources);
        long remaining=units;
        List<Allocation> allocated=new ArrayList<>();
        for(Bucket bucket:buckets)
        {
            String problem=sources.problem(bucket,employee);if(problem!=null)throw new ServiceException(problem);
            if(remaining==0)continue;
            if(!"OPEN".equals(bucket.expiryState) || bucket.expiresOn.isBefore(asOf) || bucket.availableUnits()<=0)continue;
            if(!Objects.equals(bucket.legalEntityId,current.legalEntityId))throw new ServiceException("历史额度归属与当前公司不同，请HR核对");
            long taken=Math.min(remaining,bucket.availableUnits());bucket.reservedUnits=Math.addExact(bucket.reservedUnits,taken);update(bucket);
            Allocation allocation=new Allocation();allocation.leaveRequestId=request.leaveRequestId;allocation.businessRound=round;
            allocation.accountId=account.accountId;allocation.bucketId=bucket.bucketId;allocation.userId=request.userId;allocation.leaveTypeId=request.leaveTypeId;
            allocation.units=taken;allocation.status="RESERVED";allocation.sourceExpiresOn=bucket.expiresOn;must(mapper.insertAllocation(allocation));allocated.add(allocation);
            ledger(account,bucket,request,round,"RESERVE",taken,"请假提交按原来源占用",allocation,asOf);remaining-=taken;
        }
        if(remaining!=0)throw new ServiceException("可用额度不足，申请尚未占用，请核对额度或调整天数");
        event(request,round,"RESERVE","RESERVE",fingerprint,units,allocated);must(balances.bumpAccount(account.accountId));request.quotaStatus="RESERVED";
    }

    /** Called after instance/round/event validation, before the leave owner persists its terminal decision. */
    public void decision(Long requestId,int round,String action,String eventKey)
    {
        transaction();LeaveRequest request=hydrate(leaves.selectLeaveRequestByIdForUpdate(requestId));
        if(request==null)throw new ServiceException("请假申请不存在");
        if(!Objects.equals(request.businessRound,round))throw new ServiceException("审批轮次已经变化");
        if(request.quotaPolicySnapshot==null || request.quotaUnits==null)return;
        String normalized=action==null?"":action.trim().toUpperCase(Locale.ROOT);
        String transition=switch(normalized){case "APPROVE"->"CONSUME";case "REJECT","RETURN","WITHDRAW","CANCEL"->"RELEASE";case "TERMINATE"->"APPROVED".equals(request.status)?"REFUND":"RELEASE";default->throw new ServiceException("不支持的额度审批动作");};
        transfer(request,round,transition,eventKey);
    }
    /** Safe only when the dispatcher can prove the remote start was never attempted. */
    public void failBeforeRemote(LeaveRequest request,int round,String eventKey)
    {
        transaction();hydrate(request);
        if(request==null || !Objects.equals(request.businessRound,round) || !"SUBMITTING".equals(request.status) || request.approvalInstanceId!=null)
            throw new ServiceException("失败发起已不属于当前申请轮次");
        if(request.quotaPolicySnapshot!=null && request.quotaUnits!=null)transfer(request,round,"RELEASE",eventKey);
        must(mapper.returnFailedSubmission(request.leaveRequestId,round));
    }
    public void reviewUnknown(LeaveRequest request,int round)
    {
        transaction();if(request!=null && request.quotaPolicyJson!=null && request.quotaUnits!=null && Objects.equals(request.businessRound,round))
            must(mapper.updateRequestQuotaStatus(request.leaveRequestId,round,"REVIEW"));
    }
    private void transfer(LeaveRequest request,int round,String action,String key)
    {
        LocalDate asOf=today();
        String fingerprint=AttendanceClientRequestSupport.fingerprint("LEAVE_QUOTA_TRANSITION_V1",request.leaveRequestId,round,action,request.quotaUnits);
        Event replay=mapper.selectEvent(request.leaveRequestId,round,key);if(replay!=null){sameEvent(replay,fingerprint,action);return;}
        boolean restoreConsumed="REFUND".equals(action),consume="CONSUME".equals(action);
        FinancialLocks lockedSources=lockFinancialContext(request,consume || restoreConsumed);
        EmployeeContext employee=core.lockContext(request.userId);
        Account account=balances.selectAccount(request.userId,request.leaveTypeId,true);
        if(account==null)throw new ServiceException("原额度账户缺失，不能伪造释放");
        Map<Long,Bucket> buckets=new LinkedHashMap<>();for(Bucket b:balances.selectBuckets(account.accountId,true))buckets.put(b.bucketId,b);
        verifyLockedSources(request,lockedSources);
        List<Allocation> allocations=mapper.selectAllocations(request.leaveRequestId,round,true);
        if(allocations==null || allocations.isEmpty())throw new ServiceException("当前申请轮次缺少原额度分配");
        long total=0;String expected=restoreConsumed?"CONSUMED":"RESERVED",target=consume?"CONSUMED":restoreConsumed?"REFUNDED":"RELEASED";
        for(Allocation allocation:allocations)
        {
            if(!expected.equals(allocation.status) || allocation.units<=0 || !Objects.equals(allocation.accountId,account.accountId))throw new ServiceException("原额度分配状态已变化，不能重复消费或释放");
            Bucket bucket=buckets.get(allocation.bucketId);if(bucket==null || !Objects.equals(bucket.userId,request.userId) || !Objects.equals(bucket.leaveTypeId,request.leaveTypeId))throw new ServiceException("原额度来源不匹配");
            if(consume){String problem=sources.problem(bucket,employee);if(problem!=null)throw new ServiceException(problem);}
            if(restoreConsumed){if(bucket.consumedUnits<allocation.units)throw new ServiceException("原额度消费不足，不能伪造冲回");bucket.consumedUnits-=allocation.units;}
            else {if(bucket.reservedUnits<allocation.units)throw new ServiceException("原额度占用不足，不能伪造释放");bucket.reservedUnits-=allocation.units;if(consume)bucket.consumedUnits=Math.addExact(bucket.consumedUnits,allocation.units);}
            // Preserve original expiry and year. Expired releases cannot become current spendable credit.
            if(!consume && allocation.sourceExpiresOn.isBefore(asOf))bucket.expiredUnits=Math.addExact(bucket.expiredUnits,allocation.units);
            if(bucket.reservedUnits==0 && "WAITING_RESERVED".equals(bucket.expiryState))bucket.expiryState="OPEN";
            update(bucket);must(mapper.transitionAllocation(allocation.allocationId,expected,target));ledger(account,bucket,request,round,action,allocation.units,"审批按原分配结算",allocation,asOf);total=Math.addExact(total,allocation.units);
        }
        if(!Objects.equals(request.quotaUnits,total))throw new ServiceException("原分配总量与申请不一致");
        event(request,round,key,action,fingerprint,total,allocations);must(balances.bumpAccount(account.accountId));
        must(mapper.updateRequestQuotaStatus(request.leaveRequestId,round,consume?"CONSUMED":"RELEASED"));
    }

    private PolicySnapshot policy(LeaveRequest request,LeaveType type,boolean lock)
    {
        PolicySnapshot p=new PolicySnapshot();p.leaveTypeId=type.leaveTypeId;p.leaveTypeVersion=type.rowVersion;p.unitMode=type.unitMode;
        p.balanceRequired=AttendanceLeaveAmountPolicy.requiresBalance(type);p.amountUnit=request.requestedDays==null?"MINUTES":"DAYS";p.minutesPerDay=type.minutesPerDay;
        if(Boolean.TRUE.equals(p.balanceRequired))
        {
            EmployeeContext employee=lock?core.lockContext(request.userId):balances.selectContext(request.userId);
            Match match=core.match(employee,type.leaveTypeId,lock);if(!"READY".equals(match.status))throw new ServiceException(match.reason==null?"请假额度规则未配置或不唯一":match.reason);
            p.ruleId=match.rule.ruleId;p.ruleVersion=match.rule.version;p.mappingId=match.mapping.mappingId;p.mappingVersion=match.mapping.rowVersion;
            p.legalEntityId=employee.legalEntityId;p.displayUnit=match.rule.config.unit;p.minutesPerDay=match.rule.config.minutesPerDay;
        }
        return p;
    }
    private FinancialLocks lockFinancialContext(LeaveRequest request,boolean guardAffected)
    {
        List<DayLock> sourceDays=request.quotaUnits==null?List.of():safe(mapper.selectOvertimeSourceDays(request.userId,request.leaveTypeId));
        LocalDate from=request.startTime.toLocalDate(),to=request.endTime.minusNanos(1).toLocalDate();
        List<DayLock> hintedAffected=affectedDays(request);
        Set<Period> periods=new TreeSet<>();
        // Published shifts can belong to the previous business date even when the leave starts after midnight.
        // Include the previous date's month even if settlement has not created that shift's day row yet.
        for(YearMonth month=YearMonth.from(from.minusDays(1));!month.isAfter(YearMonth.from(to));month=month.plusMonths(1))periods.add(new Period(request.shopId,month.toString()));
        for(DayLock day:sourceDays)periods.add(period(day));
        for(DayLock day:hintedAffected)periods.add(period(day));
        for(Period period:periods){credits.ensurePeriodLock(period.shop,period.month);credits.lockPeriod(period.shop,period.month);}
        List<DayLock> affected=affectedDays(request);
        if(affected.stream().anyMatch(day->!periods.contains(period(day))))throw new ServiceException("受影响排班业务月份已变化，请重新核对");
        TreeSet<Long> ids=new TreeSet<>();for(DayLock d:sourceDays)if(d.dayResultId!=null)ids.add(d.dayResultId);for(DayLock d:affected)if(d.dayResultId!=null)ids.add(d.dayResultId);
        if(!ids.isEmpty())credits.selectDayResultsForUpdate(new ArrayList<>(ids));
        Set<Long> sourceIds=new HashSet<>();for(DayLock d:sourceDays)sourceIds.add(d.dayResultId);
        if(guardAffected)
        {
            Set<Period> businessPeriods=new TreeSet<>();for(DayLock d:affected)businessPeriods.add(period(d));
            if(businessPeriods.isEmpty())for(YearMonth month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1))businessPeriods.add(new Period(request.shopId,month.toString()));
            for(Period p:businessPeriods)if(credits.countSalaryRecords(request.userId,p.month)>0)throw new ServiceException("申请时段已进入工资，请HR核对后处理");
            for(DayLock day:affected)if(day.dayResultId!=null && (value(credits.selectNetSourceUsed(day.dayResultId))>0 || value(credits.selectNetSourceTransferred(day.dayResultId))>0))throw new ServiceException("申请时段已用于抵扣或转休，请先核对原来源");
        }
        return new FinancialLocks(sourceIds,affectedIdentity(affected));
    }
    private List<DayLock> affectedDays(LeaveRequest request)
    {return safe(mapper.selectAffectedDays(request.userId,request.shopId,request.startTime,request.endTime));}
    private Period period(DayLock day){return new Period(day.shopId,YearMonth.from(day.businessDate).toString());}
    private Set<String> affectedIdentity(List<DayLock> rows)
    {Set<String> result=new HashSet<>();for(DayLock d:rows)result.add(d.shopId+"|"+d.businessDate+"|"+d.dayResultId);return result;}
    private void verifyLockedSources(LeaveRequest request,FinancialLocks locked)
    {
        Set<Long> actual=new HashSet<>();if(request.quotaUnits!=null)for(DayLock d:safe(mapper.selectOvertimeSourceDays(request.userId,request.leaveTypeId)))actual.add(d.dayResultId);
        if(locked==null || !locked.sources.equals(actual))throw new ServiceException("额度来源集合已变化，请重新提交核对");
        if(!locked.affected.equals(affectedIdentity(affectedDays(request))))throw new ServiceException("受影响排班日结集合已变化，请重新提交核对");
    }
    private record FinancialLocks(Set<Long> sources,Set<String> affected) { }
    private record Period(Long shop,String month) implements Comparable<Period>{public int compareTo(Period other){int shopOrder=shop.compareTo(other.shop);return shopOrder==0?month.compareTo(other.month):shopOrder;}}
    private <T>List<T> safe(List<T> rows){return rows==null?List.of():rows;}
    private int value(Integer n){return n==null?0:n;}
    private String reservationFingerprint(LeaveRequest request){return AttendanceClientRequestSupport.fingerprint("LEAVE_QUOTA_RESERVE_V1",request.leaveRequestId,request.quotaUnits,request.quotaPolicyJson);}
    private void update(Bucket bucket){must(balances.updateBucket(bucket));bucket.rowVersion++;}
    private void ledger(Account account,Bucket bucket,LeaveRequest request,int round,String action,long units,String reason,Allocation allocation,LocalDate asOf)
    {
        Ledger row=new Ledger();row.accountId=account.accountId;row.bucketId=bucket.bucketId;row.ruleId=bucket.ruleId;
        row.eventKey="LEAVE|"+request.leaveRequestId+"|"+round+"|"+action+"|"+bucket.bucketId;row.action="LEAVE_"+action;
        row.units="CONSUME".equals(action)?0:("RESERVE".equals(action)?-units:(allocation.sourceExpiresOn.isBefore(asOf)?0:units));row.reason=reason;row.sourceKey=bucket.sourceKey;
        row.contextFingerprint=bucket.contextFingerprint;row.contextJson=write(allocation);must(balances.insertLedger(row));
    }
    private void event(LeaveRequest request,int round,String key,String action,String fingerprint,long units,Object source)
    {Event event=new Event();event.leaveRequestId=request.leaveRequestId;event.businessRound=round;event.eventKey=key;event.action=action;event.fingerprint=fingerprint;event.units=units;event.sourceJson=write(source);must(mapper.insertEvent(event));}
    private void sameEvent(Event event,String fingerprint,String action){if(!fingerprint.equals(event.fingerprint) || !action.equals(event.action))throw new ServiceException("同一额度事件内容不同");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception failed){throw new ServiceException("额度快照无法保存");}}
    private void must(int count){if(count!=1)throw new ServiceException("额度数据版本已变化，请重新核对");}
    private LocalDate today(){return LocalDate.now(clock);}
    private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new ServiceException("请假额度必须与业务状态在同一事务处理");AttendanceOvertimeTransferSourceGuard.requireReadCommittedWriteTransaction();}
}
