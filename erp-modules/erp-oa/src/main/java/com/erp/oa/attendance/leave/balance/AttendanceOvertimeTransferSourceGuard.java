package com.erp.oa.attendance.leave.balance;

import static com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.UNITS_PER_MINUTE;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceModels.*;
import com.erp.oa.attendance.leave.balance.AttendanceOvertimeTransferModels.Transfer;

/** Read diagnostics only. C must lock the underlying days before reserving these buckets. */
@Component
public class AttendanceOvertimeTransferSourceGuard
{
    private final AttendanceOvertimeTransferMapper mapper;
    public AttendanceOvertimeTransferSourceGuard(AttendanceOvertimeTransferMapper mapper) { this.mapper=mapper; }
    /** Reject an unexpected joined RR transaction; a REQUIRED method cannot upgrade its caller's isolation. */
    public static void requireReadCommittedWriteTransaction()
    {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !Integer.valueOf(java.sql.Connection.TRANSACTION_READ_COMMITTED).equals(
                    org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new com.erp.common.core.exception.ServiceException("来源分配写入必须使用读取已提交事务，不能加入已有旧快照事务");
    }
    public String problem(Bucket bucket, EmployeeContext employee)
    {
        if (!"OVERTIME".equals(bucket.sourceType) || "REVERSED".equals(bucket.expiryState)) return null;
        List<Transfer> sources=mapper.selectBucketSources(bucket.bucketId);
        if (sources==null || sources.size()!=1) return "转休来源身份缺失或重复，请 HR 核对";
        Transfer source=sources.get(0);
        if (!Objects.equals(source.userId,bucket.userId) || !Objects.equals(source.leaveTypeId,bucket.leaveTypeId)
                || !Objects.equals(source.legalEntityId,bucket.legalEntityId) || !Objects.equals(source.ruleId,bucket.ruleId)
                || source.transferMinutes==null || source.transferMinutes<=0
                || bucket.ruleGrantedUnits!=Math.multiplyExact(source.transferMinutes.longValue(),UNITS_PER_MINUTE))
            return "转休来源与额度分桶不一致，请 HR 核对";
        if (mapper.countValidSource(source.transferId)!=1) return "原日结已失效、重算或来源发生变化，请先核对原核定";
        if (employee==null || !Objects.equals(employee.legalEntityId,source.legalEntityId)
                || mapper.countCurrentOwnership(source.userId,source.shopId,source.legalEntityId,false)!=1
                || mapper.countLaterTransfer(source.userId,source.sourceBusinessDate)>0)
            return "来源日后的归属发生变化或无法证明历史归属，请 HR 核对";
        return null;
    }
}
