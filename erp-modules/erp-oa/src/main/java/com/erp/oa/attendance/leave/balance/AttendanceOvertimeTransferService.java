package com.erp.oa.attendance.leave.balance;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferRequests.*;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.attendance.timecredit.AttendanceTimeCreditMapper;
import com.erp.oa.service.BusinessFeatureGate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AttendanceOvertimeTransferService
{
    private final AttendanceOvertimeTransferMapper mapper;
    private final AttendanceTimeCreditMapper credits;
    private final AttendanceLeaveBalanceService balances;
    private final AttendanceLeaveBalanceMapper balanceMapper;
    private final AttendanceLeaveBalanceAccess access;
    private final ShopScopeService shops;
    private final BusinessFeatureGate gate;
    private final ObjectMapper json;
    public AttendanceOvertimeTransferService(AttendanceOvertimeTransferMapper mapper, AttendanceTimeCreditMapper credits,
            AttendanceLeaveBalanceService balances, AttendanceLeaveBalanceMapper balanceMapper, AttendanceLeaveBalanceAccess access,
            ShopScopeService shops, BusinessFeatureGate gate, ObjectMapper json)
    { this.mapper=mapper;this.credits=credits;this.balances=balances;this.balanceMapper=balanceMapper;this.access=access;this.shops=shops;this.gate=gate;this.json=json; }

    public Context context(Long sourceId, Long typeId, Long selectedShopId)
    {
        enabled();Long shopId=shops.resolveRequiredShopDept(selectedShopId);
        // Resolve the original source scope before reading employee history or explaining ownership changes.
        DayResult source=scoped(sourceId,shopId,credits.selectDayResultById(sourceId));
        Context result=new Context();result.source=Source.from(source);result.history=mapper.selectHistory(sourceId);
        result.salaryLocked=credits.countSalaryRecords(source.userId,month(source))>0;
        result.rawOvertimeMinutes=raw(source);result.offsetMinutes=value(credits.selectNetSourceUsed(sourceId));
        result.transferredMinutes=value(credits.selectNetSourceTransferred(sourceId));
        EmployeeContext employee=balanceMapper.selectContext(source.userId);
        String problem=sourceProblem(source,employee,false);
        if (problem==null && (credits.countInvalidSourceTransfers(sourceId)>0 || result.offsetMinutes<0 || result.transferredMinutes<0
                || (long)result.offsetMinutes+result.transferredMinutes>result.rawOvertimeMinutes)) problem="来源分配或已核定版本失效，请先核对原核定";
        if (problem!=null) {result.status="SOURCE_REVIEW";result.reason=problem;return result;}
        result.balance=balances.previewOvertime(employee,typeId);
        Match match=balances.match(employee,typeId,false);
        if (!"READY".equals(match.status) || !"COMPENSATORY".equals(match.rule.config.leaveCategory)
                || !"SOURCE_ONLY".equals(match.rule.config.calculation)) {
            result.status="RULE_REVIEW";result.reason=match.reason==null?"未匹配到主管来源核定的调休规则":match.reason;return result;
        }
        result.availableMinutes=result.rawOvertimeMinutes-result.offsetMinutes-result.transferredMinutes;
        result.status=result.salaryLocked?"SALARY_LOCKED":"READY";result.reason=result.salaryLocked?"该员工本月工资已生成，不能重新分配加班来源":null;
        return result;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Transfer apply(Apply body, Long selectedShopId)
    {
        AttendanceOvertimeTransferSourceGuard.requireReadCommittedWriteTransaction();
        enabled();FrozenApply input=freeze(body);Long shopId=shops.resolveRequiredShopDept(selectedShopId);Long actor=access.actor();
        DayResult hint=scoped(input.sourceId,shopId,credits.selectDayResultById(input.sourceId));String salaryMonth=month(hint);
        lockPeriod(shopId,salaryMonth);
        String fingerprint=AttendanceClientRequestSupport.fingerprint("OVERTIME_TRANSFER_APPLY_V1",shopId,actor,input.sourceId,input.version,input.typeId,input.minutes,input.reason);
        Transfer replay=mapper.selectRequest(shopId,actor,input.key);if(replay!=null)return replay(replay,fingerprint,"APPLY");
        DayResult source=scoped(input.sourceId,shopId,locked(input.sourceId));
        if(!salaryMonth.equals(month(source)) || !Objects.equals(source.rowVersion,input.version))throw new ServiceException("日结版本或工资月份已变化，请重新核对来源");
        requireSalaryOpen(source.userId,salaryMonth);
        EmployeeContext employee=balances.lockContext(source.userId);
        String problem=sourceProblem(source,employee,true);if(problem!=null)throw new ServiceException(problem);
        int raw=raw(source),offset=value(credits.selectNetSourceUsed(source.dayResultId)),transferred=value(credits.selectNetSourceTransferred(source.dayResultId));
        if(offset<0 || transferred<0 || (long)offset+transferred>raw || credits.countInvalidSourceTransfers(source.dayResultId)>0)
            throw new ServiceException("来源分配或已核定版本失效，请先核对原核定");
        if(input.minutes>(long)raw-offset-transferred)throw new ServiceException("核定分钟超过扣除早退抵扣和已有转休后的可用来源");
        Match match=balances.match(employee,input.typeId,true);
        if(!"READY".equals(match.status))throw new ServiceException(match.reason);
        if(!"COMPENSATORY".equals(match.rule.config.leaveCategory) || !"SOURCE_ONLY".equals(match.rule.config.calculation))throw new ServiceException("必须使用匹配的调休来源核定规则");
        Transfer transfer=new Transfer();transfer.action="APPLY";transfer.clientRequestId=input.key;transfer.requestFingerprint=fingerprint;
        transfer.userId=source.userId;transfer.shopId=shopId;transfer.leaveTypeId=input.typeId;transfer.legalEntityId=employee.legalEntityId;transfer.ownerDeptId=employee.deptId;
        transfer.sourceDayResultId=source.dayResultId;transfer.sourceScheduleId=source.scheduleId;transfer.sourceBusinessDate=source.businessDate;transfer.sourceVersion=source.rowVersion;
        transfer.sourceSettledAt=source.settledAt;transfer.sourceWorkedMinutes=source.workedMinutes;transfer.sourceScheduledMinutes=source.scheduledMinutes;
        transfer.salaryMonth=salaryMonth;transfer.transferMinutes=input.minutes;transfer.reason=input.reason;transfer.operatorUserId=actor;
        balances.grantOvertime(employee,match,transfer);
        if(mapper.insertTransfer(transfer)!=1)throw new ServiceException("转休核定保存失败");
        return saved(transfer.transferId);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Transfer reverse(Long id, Reverse body, Long selectedShopId)
    {
        AttendanceOvertimeTransferSourceGuard.requireReadCommittedWriteTransaction();
        enabled();if(id==null || id<=0 || body==null)throw new ServiceException("撤销参数无效");
        String key=requestKey(body.clientRequestId),reason=reason(body.reason);Long actor=access.actor(),shopId=shops.resolveRequiredShopDept(selectedShopId);
        Transfer hint=original(id,shopId,mapper.selectTransfer(id,false));lockPeriod(shopId,hint.salaryMonth);
        String fingerprint=AttendanceClientRequestSupport.fingerprint("OVERTIME_TRANSFER_REVERSE_V1",shopId,actor,id,reason);
        Transfer replay=mapper.selectRequest(shopId,actor,key);if(replay!=null)return replay(replay,fingerprint,"REVERSE");
        credits.selectDayResultsForUpdate(List.of(hint.sourceDayResultId));
        Transfer original=original(id,shopId,mapper.selectTransfer(id,true));
        if(!Objects.equals(hint.sourceDayResultId,original.sourceDayResultId) || !Objects.equals(hint.salaryMonth,original.salaryMonth))throw new ServiceException("原核定来源已变化");
        requireSalaryOpen(original.userId,original.salaryMonth);
        if(mapper.countReversed(id)>0)throw new ServiceException("该核定已撤销");
        balances.lockContext(original.userId);
        Transfer reversal=json.convertValue(original,Transfer.class);reversal.transferId=null;reversal.originalTransferId=id;reversal.action="REVERSE";
        reversal.clientRequestId=key;reversal.requestFingerprint=fingerprint;reversal.reason=reason;reversal.operatorUserId=actor;reversal.createTime=null;
        balances.reverseOvertime(original,reversal);
        if(mapper.insertTransfer(reversal)!=1)throw new ServiceException("转休撤销保存失败");
        return saved(reversal.transferId);
    }
    private String sourceProblem(DayResult source, EmployeeContext employee, boolean lock)
    {
        if(source.settledAt==null || source.rowVersion==null || source.rowVersion<0) return "日结尚未完成或已失效，不能核定转休";
        if(source.workedMinutes==null || source.scheduledMinutes==null || source.workedMinutes<0 || source.scheduledMinutes<0) return "日结分钟缺失或无效";
        if(mapper.countPublishedSource(source.dayResultId)!=1)return "来源排班或员工身份已变化，请重新核对";
        if(employee==null || employee.legalEntityId==null || mapper.countCurrentOwnership(source.userId,source.shopId,employee.legalEntityId,lock)!=1)
            return "当前归属不能证明该员工仍属于原门店及法人主体，请 HR 核对";
        if(mapper.countLaterTransfer(source.userId,source.businessDate)>0)return "来源当日或之后存在已确认调岗或日期缺失，历史归属需 HR 核对";
        return null;
    }
    private DayResult scoped(Long id,Long shopId,DayResult row)
    {if(id==null || id<=0 || row==null || !id.equals(row.dayResultId) || !shopId.equals(row.shopId) || row.userId==null || row.businessDate==null)throw new ServiceException("来源日结不存在或不属于当前门店");return row;}
    private DayResult locked(Long id)
    {List<DayResult> rows=credits.selectDayResultsForUpdate(List.of(id));return rows==null?null:rows.stream().filter(x->id.equals(x.dayResultId)).findFirst().orElse(null);}
    private Transfer original(Long id,Long shopId,Transfer row)
    {if(row==null || !id.equals(row.transferId) || !shopId.equals(row.shopId) || !"APPLY".equals(row.action))throw new ServiceException("原核定不存在或不属于当前门店");return row;}
    private Transfer replay(Transfer row,String fingerprint,String action)
    {if(!fingerprint.equals(row.requestFingerprint) || !action.equals(row.action))throw new ServiceException("同一请求身份的核定内容不同");return row;}
    private Transfer saved(Long id)
    {Transfer row=mapper.selectTransfer(id,false);if(row==null)throw new ServiceException("核定回执无法核对");return row;}
    private void lockPeriod(Long shop,String month){credits.ensurePeriodLock(shop,month);credits.lockPeriod(shop,month);}
    private void requireSalaryOpen(Long user,String month){if(credits.countSalaryRecords(user,month)>0)throw new ServiceException("该员工本月工资已生成，不能重新分配加班来源");}
    private int raw(DayResult row){return Math.max(0,value(row.workedMinutes)-value(row.scheduledMinutes));}
    private int value(Integer value){return value==null?0:value;}
    private String month(DayResult row){return YearMonth.from(row.businessDate).toString();}
    private String requestKey(String key){String value=AttendanceClientRequestSupport.normalizeOptional(key,"核定请求身份无效");if(value==null)throw new ServiceException("核定请求身份不能为空");return value;}
    private String reason(String reason){String value=reason==null?"":reason.trim();if(value.length()<2 || value.length()>500)throw new ServiceException("核定原因需填写2至500个字符");return value;}
    private FrozenApply freeze(Apply body){if(body==null || body.sourceDayResultId==null || body.sourceVersion==null || body.sourceVersion<0 || body.leaveTypeId==null || body.transferMinutes==null || body.transferMinutes<=0)throw new ServiceException("核定参数缺失或无效");return new FrozenApply(body.sourceDayResultId,body.sourceVersion,body.leaveTypeId,body.transferMinutes,requestKey(body.clientRequestId),reason(body.reason));}
    private record FrozenApply(Long sourceId,Long version,Long typeId,int minutes,String key,String reason) { }
    private void enabled(){gate.requireEnabled(BusinessFeatureGate.ATTENDANCE_V2);access.require("convert");}
}
