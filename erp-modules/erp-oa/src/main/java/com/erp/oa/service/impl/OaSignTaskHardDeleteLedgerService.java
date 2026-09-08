package com.erp.oa.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignTaskHardDeleteOperation;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteItem;
import com.erp.oa.domain.vo.OaSignTaskBatchDeleteResult;
import com.erp.oa.mapper.OaSignTaskHardDeleteOperationMapper;

/** Durable claim, progress snapshot and replay boundary for task hard-delete commands. */
@Service
public class OaSignTaskHardDeleteLedgerService
{
    static final String PROCESSING = "PROCESSING";
    static final String RETRY = "RETRY";
    static final String COMPLETED = "COMPLETED";
    private static final long DEFAULT_LEASE_SECONDS = 600L;

    private final OaSignTaskHardDeleteOperationMapper mapper;
    private final Clock clock;
    private final long leaseSeconds;

    @Autowired
    public OaSignTaskHardDeleteLedgerService(OaSignTaskHardDeleteOperationMapper mapper,
            @Value("${oa.sign.hard-delete.lease-seconds:600}") long leaseSeconds)
    {
        this(mapper, Clock.systemUTC(), leaseSeconds);
    }

    OaSignTaskHardDeleteLedgerService(OaSignTaskHardDeleteOperationMapper mapper,
            Clock clock)
    {
        this(mapper, clock, DEFAULT_LEASE_SECONDS);
    }

    OaSignTaskHardDeleteLedgerService(OaSignTaskHardDeleteOperationMapper mapper,
            Clock clock, long leaseSeconds)
    {
        this.mapper = mapper;
        this.clock = clock;
        if (leaseSeconds < 60L || leaseSeconds > 86_400L)
        {
            throw new IllegalArgumentException("签约任务硬删除租约必须介于60秒和86400秒之间");
        }
        this.leaseSeconds = leaseSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Claim claim(String requestId, Long administratorUserId, String payloadHash,
            int totalCount)
    {
        validateClaimInput(requestId, administratorUserId, payloadHash, totalCount);
        Instant now = clock.instant();
        String candidateToken = UUID.randomUUID().toString();
        OaSignTaskBatchDeleteResult emptyResult = emptyResult(totalCount);
        OaSignTaskHardDeleteOperation candidate = new OaSignTaskHardDeleteOperation();
        candidate.setRequestId(requestId);
        candidate.setAdministratorUserId(administratorUserId);
        candidate.setPayloadHash(payloadHash);
        candidate.setStatus(PROCESSING);
        candidate.setClaimToken(candidateToken);
        candidate.setLeaseExpiresTime(leaseExpires(now));
        candidate.setTotalCount(totalCount);
        candidate.setProcessedCount(0);
        candidate.setResultJson(serialize(emptyResult));
        candidate.setVersion(0L);
        if (mapper.register(candidate) <= 0)
        {
            throw new IllegalStateException("删除请求台账登记失败");
        }

        OaSignTaskHardDeleteOperation persisted = mapper.lockByRequestId(requestId);
        if (persisted == null)
        {
            throw new IllegalStateException("删除请求台账不存在");
        }
        assertSameCommand(persisted, administratorUserId, payloadHash, totalCount);
        OaSignTaskBatchDeleteResult snapshot = parseAndValidateSnapshot(persisted);
        if (COMPLETED.equals(persisted.getStatus()))
        {
            return new Claim(persisted, snapshot, true);
        }
        if (PROCESSING.equals(persisted.getStatus())
                && Objects.equals(candidateToken, persisted.getClaimToken()))
        {
            return new Claim(persisted, snapshot, false);
        }
        if (PROCESSING.equals(persisted.getStatus())
                && persisted.getLeaseExpiresTime() != null
                && persisted.getLeaseExpiresTime().toInstant().isAfter(now))
        {
            throw new ServiceException("删除请求正在处理，请稍后使用同一请求编号重试");
        }
        if (!RETRY.equals(persisted.getStatus()) && !PROCESSING.equals(persisted.getStatus()))
        {
            throw new IllegalStateException("删除请求台账状态无效");
        }
        Date leaseExpiresTime = leaseExpires(now);
        if (mapper.claimForProcessing(persisted.getOperationId(), persisted.getVersion(),
                Date.from(now), candidateToken, leaseExpiresTime) != 1)
        {
            throw new ServiceException("删除请求已由其他进程接管，请稍后重试");
        }
        persisted.setStatus(PROCESSING);
        persisted.setClaimToken(candidateToken);
        persisted.setLeaseExpiresTime(leaseExpiresTime);
        persisted.setLastError(null);
        persisted.setVersion(persisted.getVersion() + 1L);
        return new Claim(persisted, snapshot, false);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void recordProgressInCurrentTransaction(OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult result)
    {
        recordProgress(operation, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordProgressRequiresNew(OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult result)
    {
        recordProgress(operation, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void complete(OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult result)
    {
        requireOwnedProcessing(operation);
        validateResult(result, operation.getTotalCount(), operation.getTotalCount());
        String resultJson = serialize(result);
        if (mapper.markCompleted(operation.getOperationId(), operation.getVersion(),
                operation.getClaimToken(), operation.getTotalCount(), resultJson) != 1)
        {
            throw new IllegalStateException("完成删除请求台账时发生状态冲突");
        }
        operation.setStatus(COMPLETED);
        operation.setProcessedCount(operation.getTotalCount());
        operation.setResultJson(resultJson);
        operation.setClaimToken(null);
        operation.setLeaseExpiresTime(null);
        operation.setLastError(null);
        operation.setVersion(operation.getVersion() + 1L);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRetry(OaSignTaskHardDeleteOperation operation, RuntimeException failure)
    {
        requireOwnedProcessing(operation);
        String error = errorType(failure);
        if (mapper.markRetry(operation.getOperationId(), operation.getVersion(),
                operation.getClaimToken(), error) != 1)
        {
            throw new IllegalStateException("安排删除请求恢复时发生状态冲突");
        }
        operation.setStatus(RETRY);
        operation.setClaimToken(null);
        operation.setLeaseExpiresTime(null);
        operation.setLastError(error);
        operation.setVersion(operation.getVersion() + 1L);
    }

    public OaSignTaskBatchDeleteResult appendResult(OaSignTaskBatchDeleteResult current,
            OaSignTaskBatchDeleteItem item, int totalCount)
    {
        if (item == null || item.getTaskId() == null || item.getResult() == null)
        {
            throw new IllegalArgumentException("删除结果明细不完整");
        }
        OaSignTaskBatchDeleteResult next = new OaSignTaskBatchDeleteResult();
        next.setTotalCount(totalCount);
        List<OaSignTaskBatchDeleteItem> items = new ArrayList<>();
        if (current != null && current.getItems() != null)
        {
            items.addAll(current.getItems());
        }
        items.add(item);
        next.setItems(items);
        for (OaSignTaskBatchDeleteItem value : items)
        {
            if ("DELETED".equals(value.getResult()))
            {
                next.setDeletedCount(next.getDeletedCount() + 1);
            }
            else
            {
                next.setFailedCount(next.getFailedCount() + 1);
            }
        }
        validateResult(next, totalCount, items.size());
        return next;
    }

    private void recordProgress(OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult result)
    {
        requireOwnedProcessing(operation);
        int previousCount = operation.getProcessedCount() == null ? 0 : operation.getProcessedCount();
        int processedCount = result == null || result.getItems() == null ? 0 : result.getItems().size();
        if (processedCount != previousCount + 1)
        {
            throw new IllegalStateException("删除请求进度不连续");
        }
        validateResult(result, operation.getTotalCount(), processedCount);
        String resultJson = serialize(result);
        Date leaseExpiresTime = leaseExpires(clock.instant());
        if (mapper.saveProgress(operation.getOperationId(), operation.getVersion(),
                operation.getClaimToken(), previousCount, processedCount,
                resultJson, leaseExpiresTime) != 1)
        {
            throw new IllegalStateException("保存删除请求进度时发生状态冲突");
        }
        operation.setProcessedCount(processedCount);
        operation.setResultJson(resultJson);
        operation.setLeaseExpiresTime(leaseExpiresTime);
        operation.setVersion(operation.getVersion() + 1L);
    }

    private OaSignTaskBatchDeleteResult parseAndValidateSnapshot(
            OaSignTaskHardDeleteOperation operation)
    {
        try
        {
            OaSignTaskBatchDeleteResult result = JSON.parseObject(
                    operation.getResultJson(), OaSignTaskBatchDeleteResult.class);
            validateResult(result, operation.getTotalCount(), operation.getProcessedCount());
            return result;
        }
        catch (RuntimeException exception)
        {
            throw new IllegalStateException("删除请求结果快照无效", exception);
        }
    }

    private void assertSameCommand(OaSignTaskHardDeleteOperation operation,
            Long administratorUserId, String payloadHash, int totalCount)
    {
        if (!Objects.equals(operation.getAdministratorUserId(), administratorUserId))
        {
            throw new ServiceException("请求编号已由其他管理员使用");
        }
        if (!Objects.equals(operation.getPayloadHash(), payloadHash)
                || !Objects.equals(operation.getTotalCount(), totalCount))
        {
            throw new ServiceException("请求编号已用于不同删除内容");
        }
    }

    private void validateClaimInput(String requestId, Long administratorUserId,
            String payloadHash, int totalCount)
    {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64
                || administratorUserId == null || administratorUserId <= 0
                || payloadHash == null || !payloadHash.matches("(?i)[0-9a-f]{64}")
                || totalCount <= 0 || totalCount > 20)
        {
            throw new ServiceException("删除请求台账参数无效");
        }
    }

    private void validateResult(OaSignTaskBatchDeleteResult result,
            Integer totalCount, Integer processedCount)
    {
        int total = totalCount == null ? -1 : totalCount;
        int processed = processedCount == null ? -1 : processedCount;
        List<OaSignTaskBatchDeleteItem> items = result == null || result.getItems() == null
                ? List.of() : result.getItems();
        if (result == null || total < 1 || result.getTotalCount() != total
                || processed < 0 || processed > total || items.size() != processed
                || result.getDeletedCount() < 0 || result.getFailedCount() < 0
                || result.getDeletedCount() + result.getFailedCount() != processed)
        {
            throw new IllegalStateException("删除请求结果快照与进度不一致");
        }
    }

    private void requireOwnedProcessing(OaSignTaskHardDeleteOperation operation)
    {
        if (operation == null || operation.getOperationId() == null
                || operation.getVersion() == null || operation.getProcessedCount() == null
                || !PROCESSING.equals(operation.getStatus())
                || operation.getClaimToken() == null || operation.getClaimToken().isBlank())
        {
            throw new IllegalStateException("删除请求未持有有效执行租约");
        }
    }

    private OaSignTaskBatchDeleteResult emptyResult(int totalCount)
    {
        OaSignTaskBatchDeleteResult result = new OaSignTaskBatchDeleteResult();
        result.setTotalCount(totalCount);
        return result;
    }

    private Date leaseExpires(Instant now)
    {
        return Date.from(now.plusSeconds(leaseSeconds));
    }

    private String serialize(OaSignTaskBatchDeleteResult result)
    {
        return JSON.toJSONString(result);
    }

    private String errorType(RuntimeException failure)
    {
        if (failure == null || failure.getClass().getSimpleName().isBlank())
        {
            return "RuntimeException";
        }
        String type = failure.getClass().getSimpleName();
        return type.length() > 64 ? type.substring(0, 64) : type;
    }

    public record Claim(OaSignTaskHardDeleteOperation operation,
            OaSignTaskBatchDeleteResult result, boolean completed)
    {
    }
}
