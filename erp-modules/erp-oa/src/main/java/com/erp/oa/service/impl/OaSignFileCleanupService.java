package com.erp.oa.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignFileCleanup;
import com.erp.oa.mapper.OaSignFileCleanupMapper;

/** Coordinates the durable state transitions for managed signing-file cleanup. */
@Service
public class OaSignFileCleanupService
{
    static final String PENDING = "PENDING";
    static final String PROCESSING = "PROCESSING";
    static final String RETRY = "RETRY";
    static final String COMPLETED = "COMPLETED";
    private static final int MAX_BATCH_SIZE = 100;

    private final OaSignFileCleanupMapper mapper;
    private final OaSignFileStorageService fileStorageService;

    public OaSignFileCleanupService(OaSignFileCleanupMapper mapper,
            OaSignFileStorageService fileStorageService)
    {
        this.mapper = mapper;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Must join the hard-delete transaction so a committed delete can never exist
     * without a durable cleanup instruction.
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long enqueue(Long taskId, Long packageId, Collection<String> fileReferences)
    {
        requirePositive(taskId, "taskId");
        requirePositive(packageId, "packageId");
        LinkedHashSet<String> references = new LinkedHashSet<>();
        for (String reference : fileReferences == null ? List.<String>of() : fileReferences)
        {
            if (reference != null && !reference.isBlank())
            {
                references.add(reference);
            }
        }
        OaSignFileCleanup cleanup = new OaSignFileCleanup();
        cleanup.setTaskId(taskId);
        cleanup.setPackageId(packageId);
        cleanup.setFileReferencesJson(JSON.toJSONString(new ArrayList<>(references)));
        cleanup.setStatus(PENDING);
        cleanup.setRetryCount(0);
        cleanup.setVersion(0L);
        if (mapper.insertCleanup(cleanup) != 1 || cleanup.getCleanupId() == null)
        {
            throw new IllegalStateException("签约文件清理台账写入失败");
        }
        return cleanup.getCleanupId();
    }

    public OaSignFileCleanup selectById(Long cleanupId)
    {
        return cleanupId == null ? null : mapper.selectById(cleanupId);
    }

    public List<OaSignFileCleanup> selectDue(Date dueTime, int limit)
    {
        if (dueTime == null)
        {
            throw new ServiceException("文件清理调度时间不能为空");
        }
        return mapper.selectDue(dueTime, Math.max(1, Math.min(limit, MAX_BATCH_SIZE)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claim(OaSignFileCleanup cleanup, Date dueTime,
            String processingToken, Date leaseExpiresTime)
    {
        if (cleanup == null || cleanup.getCleanupId() == null || cleanup.getVersion() == null)
        {
            return false;
        }
        if (dueTime == null || processingToken == null || processingToken.isBlank()
                || leaseExpiresTime == null || !leaseExpiresTime.after(dueTime))
        {
            throw new ServiceException("文件清理租约参数无效");
        }
        int affected = mapper.claimForProcessing(cleanup.getCleanupId(), cleanup.getVersion(),
                dueTime, processingToken, leaseExpiresTime);
        if (affected == 1)
        {
            cleanup.setStatus(PROCESSING);
            cleanup.setProcessingToken(processingToken);
            cleanup.setLeaseExpiresTime(leaseExpiresTime);
            cleanup.setVersion(cleanup.getVersion() + 1L);
            return true;
        }
        return false;
    }

    void deleteManagedFiles(OaSignFileCleanup cleanup)
    {
        if (cleanup == null)
        {
            throw new ServiceException("文件清理台账不存在");
        }
        List<String> references;
        try
        {
            references = JSON.parseArray(cleanup.getFileReferencesJson(), String.class);
        }
        catch (RuntimeException exception)
        {
            throw new ServiceException("文件清理台账内容无效");
        }
        fileStorageService.deleteManagedPackageFiles(
                cleanup.getTaskId(), cleanup.getPackageId(), references);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCompleted(OaSignFileCleanup cleanup)
    {
        assertUpdated(mapper.markCompleted(cleanup.getCleanupId(), cleanup.getVersion(),
                cleanup.getProcessingToken()),
                "完成文件清理");
        cleanup.setStatus(COMPLETED);
        cleanup.setNextRetryTime(null);
        cleanup.setProcessingToken(null);
        cleanup.setLeaseExpiresTime(null);
        cleanup.setLastError(null);
        cleanup.setVersion(cleanup.getVersion() + 1L);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRetry(OaSignFileCleanup cleanup, int retryCount,
            Date nextRetryTime, String lastError)
    {
        assertUpdated(mapper.markRetry(cleanup.getCleanupId(), cleanup.getVersion(),
                cleanup.getProcessingToken(), retryCount, nextRetryTime, lastError),
                "安排文件清理重试");
        cleanup.setStatus(RETRY);
        cleanup.setRetryCount(retryCount);
        cleanup.setNextRetryTime(nextRetryTime);
        cleanup.setProcessingToken(null);
        cleanup.setLeaseExpiresTime(null);
        cleanup.setLastError(lastError);
        cleanup.setVersion(cleanup.getVersion() + 1L);
    }

    private static void requirePositive(Long value, String field)
    {
        if (value == null || value <= 0)
        {
            throw new ServiceException("文件清理参数无效: " + field);
        }
    }

    private static void assertUpdated(int affected, String action)
    {
        if (affected != 1)
        {
            throw new IllegalStateException(action + "状态冲突");
        }
    }
}
