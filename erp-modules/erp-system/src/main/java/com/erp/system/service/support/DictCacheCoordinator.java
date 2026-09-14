package com.erp.system.service.support;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.erp.common.security.utils.DictUtils;
import com.erp.common.core.web.domain.AjaxResult;

/** Redis is deliberately outside the database transaction. A failed refresh is a committed-write warning. */
@Component
public class DictCacheCoordinator
{
    private static final Logger log = LoggerFactory.getLogger(DictCacheCoordinator.class);
    private final Map<String, Retry> pending = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> lastRefreshPending = new ThreadLocal<>();
    private static final class Retry { final String key; int attempts; Retry(String key) { this.key = key; } }

    public void beginMutation() { lastRefreshPending.remove(); }
    public boolean isPending(String key) { return pending.containsKey(key); }
    public void afterCommit(Collection<String> keys)
    {
        List<String> snapshot = keys.stream().filter(Objects::nonNull).distinct().sorted().toList();
        Runnable invalidate = () -> {
            boolean failed = false;
            for (String key : snapshot) {
                Retry retry = new Retry(key); pending.put(key, retry);
                if (!attempt(retry)) failed = true;
            }
            lastRefreshPending.set(failed);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) lastRefreshPending.remove(); }
            });
        else invalidate.run();
    }
    private boolean attempt(Retry retry)
    {
        synchronized (retry) {
            retry.attempts++;
            try { DictUtils.removeDictCache(retry.key); pending.remove(retry.key, retry); return true; }
            catch (RuntimeException error) { log.warn("字典数据库已提交，缓存刷新待重试: {} (attempt {})", retry.key, retry.attempts, error); return false; }
        }
    }
    @Scheduled(fixedDelayString = "${system.dict-cache.retry-delay-ms:30000}")
    public void retryPending()
    {
        // At most 64 keys per run, five automatic attempts per generation. Manual refresh remains available.
        pending.values().stream().filter(item -> item.attempts < 5).limit(64).toList().forEach(this::attempt);
    }
    public AjaxResult attachOutcome(AjaxResult result)
    {
        boolean failed = Boolean.TRUE.equals(lastRefreshPending.get()); lastRefreshPending.remove();
        result.put("cacheRefreshPending", failed);
        if (failed) result.put("cacheRefreshMessage", "数据库操作已完成，缓存刷新待重试");
        return result;
    }
    public void clearAll()
    {
        List<Retry> before = new ArrayList<>(pending.values());
        DictUtils.clearDictCache();
        before.forEach(retry -> pending.remove(retry.key, retry));
    }
}
