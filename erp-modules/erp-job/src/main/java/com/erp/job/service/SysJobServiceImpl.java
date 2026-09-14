package com.erp.job.service;

import java.util.List;
import jakarta.annotation.PostConstruct;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.core.constant.ScheduleConstants;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.exception.job.TaskException;
import com.erp.job.domain.SysJob;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.util.CronUtils;
import com.erp.job.util.ScheduleUtils;

/**
 * 定时任务调度信息 服务层
 * 
 * @author erp
 */
@Service
public class SysJobServiceImpl implements ISysJobService
{
    @Autowired
    private Scheduler scheduler;

    @Autowired
    private SysJobMapper jobMapper;

    @Autowired
    private SysJobDeletionService deletionService;

    @Autowired
    private SysJobSchedulerReconciler reconciler;

    /**
     * 项目启动时，初始化定时器 主要是防止手动修改数据库导致未同步到定时任务处理（注：不能手动修改数据库ID和任务组名，否则会导致脏数据）
     */
    @PostConstruct
    public void init() throws SchedulerException, TaskException
    {
        // Never clear foreign jobs or another scheduler instance's state.
        for (SysJob job : jobMapper.selectJobAll()) reconciler.synchronizeDefinition(job.getJobId());
    }

    /**
     * 获取quartz调度器的计划任务列表
     * 
     * @param job 调度信息
     * @return
     */
    @Override
    public List<SysJob> selectJobList(SysJob job)
    {
        return jobMapper.selectJobList(job);
    }

    /**
     * 通过调度任务ID查询调度信息
     * 
     * @param jobId 调度任务ID
     * @return 调度任务对象信息
     */
    @Override
    public SysJob selectJobById(Long jobId)
    {
        return jobMapper.selectJobById(jobId);
    }

    /**
     * 暂停任务
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pauseJob(SysJob job) throws SchedulerException
    {
        SysJob current = lockCurrent(job);
        current.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        current.setRevision(java.util.UUID.randomUUID().toString());
        int rows = jobMapper.updateJob(current);
        synchronizeAfterCommit(current.getJobId());
        return rows;
    }

    /**
     * 恢复任务
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resumeJob(SysJob job) throws SchedulerException
    {
        SysJob current = lockCurrent(job);
        current.setStatus(ScheduleConstants.Status.NORMAL.getValue());
        current.setRevision(java.util.UUID.randomUUID().toString());
        int rows = jobMapper.updateJob(current);
        synchronizeAfterCommit(current.getJobId());
        return rows;
    }

    /**
     * 删除任务后，所对应的trigger也将被删除
     * 
     * @param job 调度信息
     */
    @Override
    public int deleteJob(SysJob job) throws SchedulerException
    {
        deletionService.deleteLegacy(new Long[] {job.getJobId()},com.erp.common.security.utils.SecurityUtils.getUserId());
        return 1;
    }

    /**
     * 批量删除调度信息
     * 
     * @param jobIds 需要删除的任务ID
     * @return 结果
     */
    @Override
    public void deleteJobByIds(Long[] jobIds) throws SchedulerException
    {
        deletionService.deleteLegacy(jobIds,com.erp.common.security.utils.SecurityUtils.getUserId());
    }

    /**
     * 任务调度状态修改
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(SysJob job) throws SchedulerException
    {
        int rows = 0;
        String status = job.getStatus();
        if (ScheduleConstants.Status.NORMAL.getValue().equals(status))
        {
            rows = resumeJob(job);
        }
        else if (ScheduleConstants.Status.PAUSE.getValue().equals(status))
        {
            rows = pauseJob(job);
        }
        return rows;
    }

    /**
     * 立即运行任务
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean run(SysJob job) throws SchedulerException
    {
        Long jobId = job.getJobId();
        SysJob properties = jobMapper.selectJobByIdForUpdate(jobId);
        if (properties == null || job.getRevision() == null
                || !java.util.Objects.equals(properties.getRevision(),job.getRevision()))
        {
            return false;
        }
        String jobGroup = properties.getJobGroup();
        JobKey jobKey = ScheduleUtils.getJobKey(jobId, jobGroup);
        ensureSchedulerJobExists(properties, jobKey);

        // 参数
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(ScheduleConstants.TASK_PROPERTIES, properties);
        dataMap.put("ERP_MANUAL_RUN", Boolean.TRUE);
        if (scheduler.checkExists(jobKey))
        {
            scheduler.triggerJob(jobKey, dataMap);
            return true;
        }
        return false;
    }

    private void ensureSchedulerJobExists(SysJob job, JobKey jobKey) throws SchedulerException
    {
        if (scheduler.checkExists(jobKey))
        {
            SysJob scheduled = SysJobSchedulerReconciler.ownedDefinition(scheduler.getJobDetail(jobKey),jobKey);
            if (scheduled != null && java.util.Objects.equals(scheduled.getRevision(),job.getRevision())) return;
        }
        try
        {
            ScheduleUtils.createScheduleJob(scheduler, job);
        }
        catch (TaskException e)
        {
            throw new ServiceException("任务调度配置无效：" + e.getMessage());
        }
    }

    /**
     * 新增任务
     * 
     * @param job 调度信息 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertJob(SysJob job) throws SchedulerException, TaskException
    {
        job.setJobId(null);
        job.setRevision(java.util.UUID.randomUUID().toString());
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        int rows = jobMapper.insertJob(job);
        if (rows > 0) synchronizeAfterCommit(job.getJobId());
        return rows;
    }

    /**
     * 更新任务的时间表达式
     * 
     * @param job 调度信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateJob(SysJob job) throws SchedulerException, TaskException
    {
        lockCurrent(job);
        job.setRevision(java.util.UUID.randomUUID().toString());
        int rows = jobMapper.updateJob(job);
        if (rows > 0) synchronizeAfterCommit(job.getJobId());
        return rows;
    }

    /**
     * 更新任务
     * 
     * @param job 任务对象
     * @param jobGroup 任务组名
     */
    public void updateSchedulerJob(SysJob job, String jobGroup) throws SchedulerException, TaskException
    {
        synchronizeAfterCommit(job.getJobId());
    }

    private SysJob lockCurrent(SysJob request)
    {
        if (request == null || request.getJobId() == null || request.getRevision() == null)
            throw new ServiceException("任务版本缺失，请刷新页面后重试",409);
        SysJob current = jobMapper.selectJobByIdForUpdate(request.getJobId());
        if (current == null || !java.util.Objects.equals(current.getRevision(),request.getRevision()))
            throw new ServiceException("任务已被修改或删除，请刷新后重试",409);
        return current;
    }

    private void synchronizeAfterCommit(Long id)
    {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
        {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {reconciler.synchronizeDefinition(id);}
                    });
        }
        else reconciler.synchronizeDefinition(id);
    }

    /**
     * 校验cron表达式是否有效
     * 
     * @param cronExpression 表达式
     * @return 结果
     */
    @Override
    public boolean checkCronExpressionIsValid(String cronExpression)
    {
        return CronUtils.isValid(cronExpression);
    }
}
