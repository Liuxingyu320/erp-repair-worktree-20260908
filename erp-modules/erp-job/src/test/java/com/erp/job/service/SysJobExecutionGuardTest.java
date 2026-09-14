package com.erp.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.erp.job.domain.SysJob;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.util.AbstractQuartzJob;
import com.erp.common.core.constant.ScheduleConstants;
import com.erp.common.core.utils.SpringUtils;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

class SysJobExecutionGuardTest
{
    @Test
    void deletedChangedPausedAndLegacyDefinitionsCannotStartButExplicitManualCurrentPauseCan()
    {
        SysJobMapper mapper=mock(SysJobMapper.class);
        var guard=new SysJobExecutionGuard(mapper);
        SysJob old=job("old","0"),current=job("new","0");
        assertThat(guard.isCurrent(old,false)).isFalse();
        when(mapper.selectJobById(7L)).thenReturn(current);
        assertThat(guard.isCurrent(old,false)).isFalse();
        assertThat(guard.isCurrent(current,false)).isTrue();
        current.setStatus("1");assertThat(guard.isCurrent(current,false)).isFalse();
        assertThat(guard.isCurrent(current,true)).isTrue();
        old.setRevision(null);assertThat(guard.isCurrent(old,true)).isFalse();
    }

    @Test
    void actualQuartzEntrySkipsInvocationWhenDatabaseRejectsItsStaleTrigger()
    {
        var guard=mock(SysJobExecutionGuard.class);
        JobExecutionContext context=mock(JobExecutionContext.class);
        JobDataMap data=new JobDataMap();data.put(ScheduleConstants.TASK_PROPERTIES,job("old","0"));
        when(context.getMergedJobDataMap()).thenReturn(data);
        class RecordingJob extends AbstractQuartzJob {
            int executions;
            @Override protected void doExecute(JobExecutionContext ignored,SysJob job) {executions++;}
        }
        try (var spring=mockStatic(SpringUtils.class))
        {
            spring.when(() -> SpringUtils.getBean(SysJobExecutionGuard.class)).thenReturn(guard);
            RecordingJob actual=new RecordingJob();actual.execute(context);
            assertThat(actual.executions).isZero();
            verify(guard).isCurrent(any(SysJob.class),eq(false));
        }
    }

    private static SysJob job(String revision,String status)
    {
        SysJob job=new SysJob();job.setJobId(7L);job.setRevision(revision);job.setJobGroup("DEFAULT");job.setStatus(status);return job;
    }
}
