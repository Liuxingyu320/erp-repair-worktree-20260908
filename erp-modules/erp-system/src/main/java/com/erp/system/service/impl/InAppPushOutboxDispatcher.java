package com.erp.system.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.config.InAppPushProperties;
import com.erp.system.domain.SysUserNotificationPushOutbox;
import com.erp.system.mapper.SysUserNotificationPushOutboxMapper;
import com.erp.system.service.ISysUserNotificationService;
import com.erp.system.service.push.PushDeliveryClient;

/** Consumes only explicit new-message intents; never scans historical notifications. */
@Component
public class InAppPushOutboxDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(InAppPushOutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 12;
    private static final long MAX_AGE_SECONDS = 86400;
    private static final long CLAIM_SECONDS = 300;

    private final SysUserNotificationPushOutboxMapper mapper;
    private final ISysUserNotificationService notificationService;
    private final InAppPushProperties properties;
    private final Clock clock;

    @Autowired
    public InAppPushOutboxDispatcher(SysUserNotificationPushOutboxMapper mapper,
            ISysUserNotificationService notificationService, InAppPushProperties properties)
    {
        this(mapper, notificationService, properties, Clock.systemUTC());
    }

    InAppPushOutboxDispatcher(SysUserNotificationPushOutboxMapper mapper,
            ISysUserNotificationService notificationService, InAppPushProperties properties, Clock clock)
    {
        this.mapper = mapper;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${erp.push.in-app.dispatch-delay-ms:30000}",
            initialDelayString = "${erp.push.in-app.dispatch-delay-ms:30000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void dispatchDue()
    {
        if (!properties.isEnabled()) return;
        Instant now = clock.instant();
        List<SysUserNotificationPushOutbox> rows = mapper.selectDue(
                Date.from(now), Date.from(now.minusSeconds(CLAIM_SECONDS)), 50);
        if (rows == null) return;
        for (SysUserNotificationPushOutbox row : rows)
        {
            try
            {
                dispatchOne(row);
            }
            catch (RuntimeException failure)
            {
                // A failed status write remains SENDING and can be reclaimed after the lease.
                log.error("站内消息推送任务状态更新失败，outboxId={}, errorType={}",
                        row.getOutboxId(), failure.getClass().getSimpleName());
            }
        }
    }

    private void dispatchOne(SysUserNotificationPushOutbox row)
    {
        Instant now = clock.instant();
        if (mapper.claim(row.getOutboxId(), row.getVersion(), Date.from(now),
                Date.from(now.minusSeconds(CLAIM_SECONDS))) != 1) return;
        row.setVersion(row.getVersion() + 1);
        row.setAttemptCount(row.getAttemptCount() + 1);
        if (row.getAttemptCount() > MAX_ATTEMPTS)
        {
            finish(row, "DEAD", "RETRIES_EXHAUSTED", null);
            return;
        }
        if (row.getCreateTime() == null
                || !row.getCreateTime().toInstant().plusSeconds(MAX_AGE_SECONDS).isAfter(now))
        {
            finish(row, "DEAD", "EXPIRED", null);
            return;
        }
        UserNotificationCommand command;
        try
        {
            command = JSON.parseObject(row.getPayloadJson(), UserNotificationCommand.class);
            if (command == null || !"MOBILE_PUSH".equals(command.getChannel()))
            {
                throw new IllegalArgumentException("Invalid push intent");
            }
            PushDeliveryClient.whitelistedRouteData(command);
        }
        catch (RuntimeException invalidPayload)
        {
            finish(row, "DEAD", "INVALID_PAYLOAD", null);
            return;
        }

        UserNotificationResult result;
        try
        {
            result = notificationService.publish(command);
        }
        catch (RuntimeException failure)
        {
            retry(row, "PUSH_CALL_FAILED");
            return;
        }
        String status = result == null ? "UNKNOWN" : result.getStatus();
        if (result != null && result.getAccepted()
                && ("DELIVERED".equals(status) || "DUPLICATE".equals(status)))
        {
            finish(row, "SENT", status, null);
        }
        else if ("NO_DEVICE".equals(status))
        {
            finish(row, "SKIPPED", "NO_DEVICE", null);
        }
        else if ("PERMANENT_FAILURE".equals(status))
        {
            finish(row, "DEAD", status, null);
        }
        else
        {
            retry(row, "DISABLED".equals(status) ? "DISABLED" : "RETRYABLE_FAILURE");
        }
    }

    private void retry(SysUserNotificationPushOutbox row, String code)
    {
        if (row.getAttemptCount() >= MAX_ATTEMPTS)
        {
            finish(row, "DEAD", "RETRIES_EXHAUSTED_" + code, null);
            return;
        }
        long seconds = Math.min(1800, 30L << Math.min(row.getAttemptCount() - 1, 6));
        finish(row, "RETRY", code, Date.from(clock.instant().plusSeconds(seconds)));
    }

    private void finish(SysUserNotificationPushOutbox row, String status, String code, Date next)
    {
        if (mapper.finishAttempt(row.getOutboxId(), row.getVersion(), status, code,
                next, Date.from(clock.instant())) != 1)
        {
            throw new ServiceException("站内消息推送任务已被其他实例更新");
        }
        if ("DEAD".equals(status))
        {
            log.warn("站内消息推送已终止，outboxId={}, notificationId={}, reason={}",
                    row.getOutboxId(), row.getNotificationId(), code);
        }
    }
}
