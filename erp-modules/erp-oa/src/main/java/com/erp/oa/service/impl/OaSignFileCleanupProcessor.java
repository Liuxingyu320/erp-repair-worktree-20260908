package com.erp.oa.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignFileCleanup;

/** Executes idempotent cleanup immediately and retries persisted failures in the background. */
@Service
public class OaSignFileCleanupProcessor
{
    private static final Logger log = LoggerFactory.getLogger(OaSignFileCleanupProcessor.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360, 1440 };
    private static final long DEFAULT_LEASE_SECONDS = 3600L;

    private final OaSignFileCleanupService cleanupService;
    private final Clock clock;
    private final long leaseSeconds;

    @Autowired
    public OaSignFileCleanupProcessor(OaSignFileCleanupService cleanupService,
            @Value("${oa.sign.file-cleanup.lease-seconds:3600}") long leaseSeconds)
    {
        this(cleanupService, Clock.systemUTC(), leaseSeconds);
    }

    OaSignFileCleanupProcessor(OaSignFileCleanupService cleanupService, Clock clock)
    {
        this(cleanupService, clock, DEFAULT_LEASE_SECONDS);
    }

    OaSignFileCleanupProcessor(OaSignFileCleanupService cleanupService,
            Clock clock, long leaseSeconds)
    {
        this.cleanupService = cleanupService;
        this.clock = clock;
        if (leaseSeconds < 300L || leaseSeconds > 86_400L)
        {
            throw new IllegalArgumentException("签约文件清理租约必须介于300秒和86400秒之间");
        }
        this.leaseSeconds = leaseSeconds;
    }

    public boolean processById(Long cleanupId)
    {
        OaSignFileCleanup cleanup = cleanupService.selectById(cleanupId);
        if (cleanup == null)
        {
            throw new ServiceException("文件清理台账不存在");
        }
        if (process(cleanup, clock.instant()))
        {
            return true;
        }
        // The scheduler may win the optimistic claim between the initial read
        // and this immediate attempt. That is accepted work, not a cleanup failure.
        OaSignFileCleanup latest = cleanupService.selectById(cleanupId);
        return latest != null && (OaSignFileCleanupService.PROCESSING.equals(latest.getStatus())
                || OaSignFileCleanupService.COMPLETED.equals(latest.getStatus()));
    }

    @Scheduled(fixedDelayString = "${oa.sign.file-cleanup.fixed-delay-ms:60000}")
    public void processDue()
    {
        Instant now = clock.instant();
        List<OaSignFileCleanup> rows = cleanupService.selectDue(Date.from(now), 100);
        for (OaSignFileCleanup cleanup : rows)
        {
            try
            {
                process(cleanup, now);
            }
            catch (RuntimeException exception)
            {
                log.warn("签约文件清理失败，已保留可重试台账: cleanupId={}, type={}",
                        cleanup.getCleanupId(), exception.getClass().getSimpleName());
            }
        }
    }

    boolean process(OaSignFileCleanup cleanup, Instant now)
    {
        if (OaSignFileCleanupService.COMPLETED.equals(cleanup.getStatus()))
        {
            return true;
        }
        Date dueTime = Date.from(now);
        String processingToken = UUID.randomUUID().toString();
        Date leaseExpiresTime = Date.from(now.plusSeconds(leaseSeconds));
        if (!cleanupService.claim(cleanup, dueTime, processingToken, leaseExpiresTime))
        {
            return false;
        }
        try
        {
            cleanupService.deleteManagedFiles(cleanup);
            cleanupService.markCompleted(cleanup);
            return true;
        }
        catch (RuntimeException exception)
        {
            int previousRetries = cleanup.getRetryCount() == null ? 0 : cleanup.getRetryCount();
            int nextRetryCount = previousRetries + 1;
            int delayMinutes = RETRY_MINUTES[Math.min(previousRetries, RETRY_MINUTES.length - 1)];
            try
            {
                cleanupService.markRetry(cleanup, nextRetryCount,
                        Date.from(now.plusSeconds(delayMinutes * 60L)), errorType(exception));
            }
            catch (RuntimeException stateException)
            {
                // A stale PROCESSING row is still recoverable by the scheduler after five minutes.
                exception.addSuppressed(stateException);
            }
            throw exception;
        }
    }

    private static String errorType(RuntimeException exception)
    {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "RuntimeException" : simpleName;
    }
}
