package com.erp.system.service.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.erp.common.security.service.TokenService;
import com.erp.system.mapper.SecuritySessionInvalidationOutboxMapper;

@Component
public class UserSecurityStateChangedListener
{
    private static final Logger log = LoggerFactory.getLogger(UserSecurityStateChangedListener.class);
    private final TokenService tokenService;
    private final SecuritySessionInvalidationOutboxMapper mapper;

    public UserSecurityStateChangedListener(TokenService tokenService,
            SecuritySessionInvalidationOutboxMapper mapper)
    {
        this.tokenService = tokenService;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSecurityStateChanged(UserSecurityStateChangedEvent event)
    {
        try
        {
            tokenService.invalidateUserSessions(event.getUserId(),
                    UserSessionInvalidationService.PASSWORD_CHANGED.equals(event.getReasonCode())
                            ? event.getRetainedToken() : null);
            mapper.markDone(event.getEventId());
        }
        catch (RuntimeException ex)
        {
            log.warn("安全会话即时失效失败，已进入补偿队列 eventId={} userId={} reason={} errorCode={}",
                    event.getEventId(), event.getUserId(), event.getReasonCode(),
                    ex.getClass().getSimpleName());
        }
    }
}
