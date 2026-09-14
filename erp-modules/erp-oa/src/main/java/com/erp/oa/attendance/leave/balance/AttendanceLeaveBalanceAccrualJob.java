package com.erp.oa.attendance.leave.balance;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.oa.service.BusinessFeatureGate;

/** Opt in only after migration and HR policy configuration. No login context. */
@Component
public class AttendanceLeaveBalanceAccrualJob
{
    private static final Logger log = LoggerFactory.getLogger(AttendanceLeaveBalanceAccrualJob.class);
    private final AttendanceLeaveBalanceMapper mapper;
    private final AttendanceLeaveBalanceService service;
    private final BusinessFeatureGate gate;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean();
    private long cursor;
    public AttendanceLeaveBalanceAccrualJob(AttendanceLeaveBalanceMapper mapper,
            AttendanceLeaveBalanceService service, BusinessFeatureGate gate,
            @Value("${oa.attendance-v2.leave.balance.accrual-enabled:false}") boolean enabled)
    { this.mapper = mapper; this.service = service; this.gate = gate; this.enabled = enabled; }

    @Scheduled(fixedDelayString="${oa.attendance-v2.leave.balance.accrual-delay-ms:60000}")
    public void accrue()
    {
        if (!enabled || !gate.isEnabled(BusinessFeatureGate.ATTENDANCE_V2) || !running.compareAndSet(false, true)) return;
        try
        {
            List<Long> types = mapper.selectConfiguredTypes();
            if (types.isEmpty()) return;
            List<Long> users = mapper.selectAccrualUsers(cursor, 100);
            if (users.isEmpty()) { cursor = 0; return; }
            for (Long userId : users)
            {
                for (Long typeId : types)
                {
                    try { service.recalculateScheduled(userId, typeId); }
                    catch (RuntimeException failure) {
                        log.warn("leave balance accrual failed, userId={}, typeId={}, failureType={}", userId, typeId, failure.getClass().getSimpleName());
                    }
                }
                cursor = userId;
            }
        }
        finally { running.set(false); }
    }
}
