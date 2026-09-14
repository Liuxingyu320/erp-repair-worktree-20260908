package com.erp.job.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.erp.common.core.constant.ScheduleConstants;
import com.erp.job.domain.SysJob;
import com.erp.job.domain.SysJobDeleteIntent;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.mapper.SysJobDeleteMapper;
import com.erp.job.util.ScheduleUtils;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Each scheduler reconciles its own jobs. Definitions remain authoritative across RAM/JDBC stores. */
@Service
public class SysJobSchedulerReconciler
{
    private static final Logger log = LoggerFactory.getLogger(SysJobSchedulerReconciler.class);
    private static final int LIMIT = 100;
    private final Scheduler scheduler;
    private final SysJobMapper jobs;
    private final SysJobDeleteMapper deletes;
    private final TransactionTemplate tx;
    private long definitionCursor;
    private String orphanCursor = "";

    public SysJobSchedulerReconciler(Scheduler scheduler, SysJobMapper jobs,
            SysJobDeleteMapper deletes, PlatformTransactionManager manager)
    {
        this.scheduler=scheduler;this.jobs=jobs;this.deletes=deletes;this.tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString="${erp.job.reconcile-delay-ms:10000}",initialDelayString="${erp.job.reconcile-initial-delay-ms:10000}")
    public synchronized void tick()
    {
        try
        {
            for (SysJobDeleteIntent intent : deletes.selectPending(LIMIT)) synchronizeIntent(intent);
            List<SysJob> page = jobs.selectReconcilePage(definitionCursor,LIMIT);
            for (SysJob job : page) synchronizeDefinition(job.getJobId());
            definitionCursor = page.size() < LIMIT ? 0L : page.get(page.size()-1).getJobId();
            reconcileLocalOrphans();
        }
        catch (RuntimeException ex) {log.warn("job_reconcile_retry type={}",ex.getClass().getSimpleName());}
    }

    public void synchronizeBatch(String id)
    {
        try {for (SysJobDeleteIntent intent : deletes.selectBatchIntents(id)) synchronizeIntent(intent);}
        catch (RuntimeException ex) {log.warn("job_delete_sync_pending type={}",ex.getClass().getSimpleName());}
    }

    private void synchronizeIntent(SysJobDeleteIntent candidate)
    {
        tx.executeWithoutResult(status -> {
            // All scheduler effects for a job take its definition lock before the intent lock.
            SysJob current = jobs.selectJobByIdForUpdate(candidate.getJobId());
            SysJobDeleteIntent intent = deletes.lockIntent(candidate.getIntentId());
            if (intent == null || !("PENDING".equals(intent.getStatus()) || "RETRYING".equals(intent.getStatus()))) return;
            try
            {
                JobKey key=ScheduleUtils.getJobKey(intent.getJobId(),intent.getJobGroup());
                JobDetail detail=scheduler.getJobDetail(key);
                SysJob scheduled=ownedDefinition(detail,key);
                if (detail != null && (scheduled == null || !Objects.equals(scheduled.getRevision(),intent.getRevision())))
                {
                    deletes.updateResult(intent.getIntentId(),"SUPERSEDED",null);
                    return;
                }
                if (current != null && Objects.equals(current.getRevision(),intent.getRevision()))
                {
                    deletes.updateResult(intent.getIntentId(),"RETRYING","DEFINITION_STILL_PRESENT");
                    return;
                }
                if (detail != null) scheduler.deleteJob(key);
                deletes.updateResult(intent.getIntentId(),"COMPLETED",null);
            }
            catch (Exception ex) {deletes.updateResult(intent.getIntentId(),"RETRYING","SCHEDULER_UNAVAILABLE");}
        });
    }

    public void synchronizeDefinition(Long id)
    {
        try
        {
            tx.executeWithoutResult(status -> {
                SysJob job=jobs.selectJobByIdForUpdate(id);
                if (job == null) return;
                try
                {
                    JobKey key=ScheduleUtils.getJobKey(job.getJobId(),job.getJobGroup());
                    JobDetail detail=scheduler.getJobDetail(key);
                    SysJob scheduled=ownedDefinition(detail,key);
                    if (detail != null && scheduled == null) throw new IllegalStateException("foreign scheduler job key");
                    if (scheduled == null || !Objects.equals(scheduled.getRevision(),job.getRevision())) ScheduleUtils.createScheduleJob(scheduler,job);
                }
                catch (Exception ex) {throw new IllegalStateException("scheduler synchronization pending",ex);}
            });
        }
        catch (RuntimeException ex) {log.warn("job_definition_sync_pending jobId={} type={}",id,ex.getClass().getSimpleName());}
    }

    private void reconcileLocalOrphans()
    {
        try
        {
            List<JobKey> all = new ArrayList<>(scheduler.getJobKeys(GroupMatcher.anyJobGroup()));
            all.sort(Comparator.comparing(JobKey::toString));
            List<JobKey> page=all.stream().filter(key -> key.toString().compareTo(orphanCursor)>0).limit(LIMIT).toList();
            for (JobKey key : page)
            {
                SysJob observed=ownedDefinition(scheduler.getJobDetail(key),key);
                if (observed == null) continue;
                tx.executeWithoutResult(status -> {
                    SysJob current=jobs.selectJobByIdForUpdate(observed.getJobId());
                    try
                    {
                        SysJob latest=ownedDefinition(scheduler.getJobDetail(key),key);
                        if (latest != null && Objects.equals(latest.getRevision(),observed.getRevision())
                                && (current == null || !Objects.equals(current.getRevision(),latest.getRevision())
                                || !Objects.equals(current.getJobGroup(),latest.getJobGroup()))) scheduler.deleteJob(key);
                    }
                    catch (Exception ex) {throw new IllegalStateException(ex);}
                });
            }
            orphanCursor=page.size()<LIMIT?"":page.get(page.size()-1).toString();
        }
        catch (Exception ex) {log.warn("job_orphan_sync_pending type={}",ex.getClass().getSimpleName());}
    }

    public static SysJob ownedDefinition(JobDetail detail, JobKey key)
    {
        if (detail == null || !(detail.getJobDataMap().get(ScheduleConstants.TASK_PROPERTIES) instanceof SysJob job)) return null;
        if (!com.erp.job.util.AbstractQuartzJob.class.isAssignableFrom(detail.getJobClass())
                || job.getJobId() == null || !ScheduleUtils.getJobKey(job.getJobId(),job.getJobGroup()).equals(key)) return null;
        return job;
    }
}
