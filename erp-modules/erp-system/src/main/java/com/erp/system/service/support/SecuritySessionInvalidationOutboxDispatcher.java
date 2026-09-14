package com.erp.system.service.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.common.security.service.TokenService;
import com.erp.system.domain.SecuritySessionInvalidationOutbox;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;

@Component
public class SecuritySessionInvalidationOutboxDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(SecuritySessionInvalidationOutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;

    private final SecuritySessionInvalidationOutboxMapper mapper;
    private final TokenService tokenService;

    public SecuritySessionInvalidationOutboxDispatcher(SecuritySessionInvalidationOutboxMapper mapper,
            TokenService tokenService)
    {
        this.mapper = mapper;
        this.tokenService = tokenService;
    }

    @Scheduled(fixedDelayString = "${system.security-session-outbox.delay-ms:30000}",
            initialDelayString = "${system.security-session-outbox.initial-delay-ms:30000}")
    public void dispatch()
    {
        mapper.resetStaleClaims(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        String owner = UUID.randomUUID().toString().replace("-", "");
        if (mapper.claimPending(owner, BATCH_SIZE) <= 0)
        {
            return;
        }
        List<SecuritySessionInvalidationOutbox> claimed = mapper.selectClaimed(owner);
        for (SecuritySessionInvalidationOutbox item : claimed)
        {
            dispatchOne(item);
        }
    }

    void dispatchOne(SecuritySessionInvalidationOutbox item)
    {
        try
        {
            if (UserSessionInvalidationService.PASSWORD_CHANGED.equals(item.getReasonCode())
                    && item.getRetainedSessionDigest() != null)
                tokenService.invalidateUserSessionsExceptDigest(item.getUserId(), item.getRetainedSessionDigest());
            else
                tokenService.invalidateUserSessions(item.getUserId());
            mapper.markDone(item.getEventId());
        }
        catch (RuntimeException ex)
        {
            int nextAttempt = item.getAttempts() + 1;
            boolean terminal = nextAttempt >= MAX_ATTEMPTS;
            long delaySeconds = Math.min(900L, 5L << Math.min(nextAttempt, 8));
            mapper.releaseForRetry(item.getEventId(),
                    Date.from(Instant.now().plus(delaySeconds, ChronoUnit.SECONDS)),
                    ex.getClass().getSimpleName(), terminal);
            log.warn("安全会话失效补偿未完成 eventId={} userId={} attempt={} terminal={} errorCode={}",
                    item.getEventId(), item.getUserId(), nextAttempt, terminal,
                    ex.getClass().getSimpleName());
        }
    }
}

