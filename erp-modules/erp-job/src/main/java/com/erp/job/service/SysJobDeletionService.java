package com.erp.job.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import com.erp.common.core.exception.ServiceException;
import com.erp.job.domain.SysJob;
import com.erp.job.domain.SysJobDeleteBatch;
import com.erp.job.domain.SysJobDeleteIntent;
import com.erp.job.mapper.SysJobMapper;
import com.erp.job.mapper.SysJobDeleteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SysJobDeletionService
{
    public record Target(Long jobId, String revision) { }
    public record Request(String batchId, List<Target> jobs) { }
    public record Item(String jobId, String status, int attempts) { }
    public record Receipt(String batchId, String status, List<Item> items) { }

    private final SysJobMapper jobs;
    private final SysJobDeleteMapper deletes;
    private final TransactionTemplate tx;
    private final SysJobSchedulerReconciler reconciler;

    public SysJobDeletionService(SysJobMapper jobs, SysJobDeleteMapper deletes,
            PlatformTransactionManager manager, SysJobSchedulerReconciler reconciler)
    {
        this.jobs = jobs;
        this.deletes = deletes;
        this.tx = new TransactionTemplate(manager);
        this.reconciler = reconciler;
    }

    public Receipt delete(Request request, Long actorId)
    {
        return delete(request, actorId, true);
    }

    public Receipt deleteLegacy(Long[] ids, Long actorId)
    {
        List<Target> targets = ids == null ? List.of() : java.util.Arrays.stream(ids).map(id -> new Target(id,null)).toList();
        return delete(new Request("delete_"+UUID.randomUUID(),targets),actorId,false);
    }

    private Receipt delete(Request request, Long actorId, boolean requireRevision)
    {
        if (request == null || actorId == null || actorId <= 0) throw invalid("删除请求无效");
        validateBatchId(request.batchId());
        if (request.jobs() == null || request.jobs().isEmpty() || request.jobs().size() > 500) throw invalid("请选择1至500个定时任务");
        TreeMap<Long,String> targets = new TreeMap<>();
        for (Target target : request.jobs())
        {
            if (target == null || target.jobId() == null || target.jobId() <= 0
                    || requireRevision && (target.revision() == null || !target.revision().matches("[A-Za-z0-9-]{1,36}")))
                throw invalid("任务版本缺失，请刷新列表后重试");
            if (targets.containsKey(target.jobId()) && !Objects.equals(targets.get(target.jobId()),target.revision())) throw invalid("任务版本冲突");
            targets.put(target.jobId(),target.revision());
        }
        String fingerprint = fingerprint(targets.toString());
        tx.executeWithoutResult(status -> {
            SysJobDeleteBatch proposed = new SysJobDeleteBatch();
            proposed.setBatchId(request.batchId());proposed.setActorId(actorId);proposed.setFingerprint(fingerprint);
            int inserted = deletes.insertBatchIfAbsent(proposed);
            SysJobDeleteBatch batch = deletes.lockBatch(request.batchId());
            if (batch == null || !Objects.equals(batch.getActorId(),actorId)
                    || !Objects.equals(batch.getFingerprint(),fingerprint)) throw invalid("此删除编号不属于当前任务选择");
            if (inserted == 0)
            {
                // An outer REPEATABLE READ transaction may predate this committed batch.
                // Do not use its old snapshot to decide whether the command already ran.
                if (deletes.selectBatchIntentsForUpdate(request.batchId()).isEmpty())
                    throw invalid("原删除批次记录尚待核对，请查询原删除结果");
                return;
            }
            if (inserted != 1) throw invalid("删除批次建立失败，请查询原删除结果");
            // Validate and lock every target before any deletion or scheduler call.
            List<SysJob> locked = new ArrayList<>();
            for (var target : targets.entrySet())
            {
                SysJob job = jobs.selectJobByIdForUpdate(target.getKey());
                if (job == null || requireRevision && !Objects.equals(job.getRevision(),target.getValue()))
                    throw invalid("任务不存在或已被修改，请刷新列表后重新确认");
                if (job.getRevision() == null || job.getRevision().isBlank()) throw invalid("任务版本尚未初始化，暂不能删除");
                locked.add(job);
            }
            for (SysJob job : locked)
            {
                SysJobDeleteIntent intent = new SysJobDeleteIntent();
                intent.setIntentId(UUID.randomUUID().toString());intent.setBatchId(request.batchId());
                intent.setJobId(job.getJobId());intent.setJobGroup(job.getJobGroup());intent.setRevision(job.getRevision());
                if (deletes.insertIntent(intent) != 1 || jobs.deleteJobById(job.getJobId()) != 1) throw invalid("删除状态发生变化，请核对原删除结果");
            }
        });
        // The durable DB result survives scheduler failure, process crashes and a lost HTTP reply.
        if (TransactionSynchronizationManager.isActualTransactionActive())
        {
            // A caller may own the outer transaction. Never publish its uncommitted deletion to Quartz.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override public void afterCommit() { reconciler.synchronizeBatch(request.batchId()); }
            });
        }
        else reconciler.synchronizeBatch(request.batchId());
        return readReceipt(request.batchId(),actorId,TransactionSynchronizationManager.isActualTransactionActive());
    }

    public Receipt receipt(String id, Long actorId)
    {
        return readReceipt(id,actorId,false);
    }

    private Receipt readReceipt(String id, Long actorId, boolean currentRead)
    {
        validateBatchId(id);
        SysJobDeleteBatch batch = currentRead ? deletes.lockBatch(id) : deletes.selectBatch(id);
        if (batch == null) return new Receipt(id,"NOT_OBSERVED",List.of());
        if (!Objects.equals(batch.getActorId(),actorId)) throw new ServiceException("无权查询此删除结果",403);
        List<SysJobDeleteIntent> rows = currentRead ? deletes.selectBatchIntentsForUpdate(id) : deletes.selectBatchIntents(id);
        List<Item> items = rows.stream().map(row -> new Item(row.getJobId().toString(),row.getStatus(),
                row.getAttempts() == null ? 0 : row.getAttempts())).toList();
        String status = rows.isEmpty() ? "PENDING" : rows.stream().allMatch(row -> "COMPLETED".equals(row.getStatus()) || "SUPERSEDED".equals(row.getStatus()))
                ? "COMPLETED" : rows.stream().anyMatch(row -> "RETRYING".equals(row.getStatus())) ? "RETRYING" : "PENDING";
        return new Receipt(id,status,items);
    }

    private static void validateBatchId(String id)
    {
        if (id == null || !id.matches("[A-Za-z0-9_-]{20,64}")) throw invalid("删除编号无效");
    }
    private static String fingerprint(String value)
    {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch (java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
    private static ServiceException invalid(String message) {return new ServiceException(message,409);}
}
