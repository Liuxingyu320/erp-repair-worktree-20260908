package com.erp.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erp.common.core.constant.ScheduleConstants;
import com.erp.job.domain.SysJob;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.util.ScheduleUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("定时任务服务")
class SysJobServiceImplTest
{
    @Test
    @DisplayName("立即执行时如果数据库有任务但调度器缺失，应先补注册再触发")
    void shouldRegisterMissingSchedulerJobBeforeRun()
            throws Exception
    {
        Scheduler scheduler = mock(Scheduler.class);
        SysJobMapper jobMapper = mock(SysJobMapper.class);
        SysJobServiceImpl service = new SysJobServiceImpl();
        ReflectionTestUtils.setField(service, "scheduler", scheduler);
        ReflectionTestUtils.setField(service, "jobMapper", jobMapper);

        SysJob request = new SysJob();
        request.setJobId(7L);
        request.setRevision("revision-1");
        request.setJobGroup("DEFAULT");

        SysJob stored = new SysJob();
        stored.setJobId(7L);
        stored.setRevision("revision-1");
        stored.setJobName("测试任务");
        stored.setJobGroup("DEFAULT");
        stored.setInvokeTarget("ryTask.ryNoParams");
        stored.setCronExpression("0 0 3 * * ?");
        stored.setMisfirePolicy(ScheduleConstants.MISFIRE_DO_NOTHING);
        stored.setConcurrent("1");
        stored.setStatus(ScheduleConstants.Status.NORMAL.getValue());
        when(jobMapper.selectJobByIdForUpdate(7L)).thenReturn(stored);

        JobKey jobKey = ScheduleUtils.getJobKey(7L, "DEFAULT");
        when(scheduler.checkExists(jobKey)).thenReturn(false, false, true);

        boolean result = service.run(request);

        assertThat(result).isTrue();
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        verify(scheduler).triggerJob(eq(jobKey), any(JobDataMap.class));
    }
}
