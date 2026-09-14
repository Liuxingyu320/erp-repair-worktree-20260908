package com.erp.job.service;

import java.util.Objects;
import com.erp.common.core.constant.ScheduleConstants;
import com.erp.job.domain.SysJob;
import com.erp.job.mapper.SysJobMapper;
import org.springframework.stereotype.Service;

/** This prevents new execution from stale triggers; it does not interrupt an already-running job. */
@Service
public class SysJobExecutionGuard
{
    private final SysJobMapper jobs;
    public SysJobExecutionGuard(SysJobMapper jobs) {this.jobs=jobs;}
    public boolean isCurrent(SysJob scheduled, boolean manual)
    {
        if (scheduled == null || scheduled.getJobId() == null || scheduled.getRevision() == null) return false;
        SysJob current=jobs.selectJobById(scheduled.getJobId());
        return current != null && Objects.equals(current.getRevision(),scheduled.getRevision())
                && Objects.equals(current.getJobGroup(),scheduled.getJobGroup())
                && (manual || ScheduleConstants.Status.NORMAL.getValue().equals(current.getStatus()));
    }
}
