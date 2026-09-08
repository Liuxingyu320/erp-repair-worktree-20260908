package com.erp.system.service.impl;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.oa.api.RemoteSignTaskService;
import com.erp.oa.api.domain.HrSignBusinessEvent;
import com.erp.system.domain.SysHrSignEventOutbox;
import feign.FeignException;

/**
 * 定时投递人事签约事件，不在远程调用期间持有业务事务。
 */
@Service
@ConditionalOnProperty(prefix = "hr.sign.lifecycle-automation", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class HrSignEventDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(
            HrSignEventDispatcher.class);
    private static final int[] RETRY_MINUTES = { 1, 5, 30, 120, 360 };

    private final HrSignEventOutboxService outboxService;
    private final RemoteSignTaskService remoteSignTaskService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public HrSignEventDispatcher(HrSignEventOutboxService outboxService,
            RemoteSignTaskService remoteSignTaskService,
            ObjectMapper objectMapper)
    {
        this(outboxService, remoteSignTaskService, objectMapper,
                Clock.systemUTC());
    }

    HrSignEventDispatcher(HrSignEventOutboxService outboxService,
            RemoteSignTaskService remoteSignTaskService,
            ObjectMapper objectMapper, Clock clock)
    {
        this.outboxService = outboxService;
        this.remoteSignTaskService = remoteSignTaskService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hr.sign.outbox.dispatch-fixed-delay-ms:30000}")
    public void dispatchDue()
    {
        Instant now = clock.instant();
        List<SysHrSignEventOutbox> rows = outboxService.selectDue(
                Date.from(now),
                Date.from(now.minus(5, ChronoUnit.MINUTES)), 100);
        if (rows == null)
        {
            return;
        }
        for (SysHrSignEventOutbox row : rows)
        {
            try
            {
                dispatchOne(row, now);
            }
            catch (RuntimeException stateFailure)
            {
                log.error("签约事件状态转换失败，outboxId={}, actionId={}, errorType={}",
                        row == null ? null : row.getOutboxId(),
                        row == null ? null : row.getActionId(),
                        stateFailure.getClass().getSimpleName());
            }
        }
    }

    private void dispatchOne(SysHrSignEventOutbox row, Instant now)
    {
        if (!outboxService.claim(row))
        {
            return;
        }

        HrSignBusinessEvent event;
        try
        {
            event = objectMapper.readValue(row.getPayloadJson(),
                    HrSignBusinessEvent.class);
        }
        catch (JsonProcessingException | IllegalArgumentException invalid)
        {
            log.error("签约事件载荷无效，outboxId={}, actionId={}, errorCode={}",
                    row.getOutboxId(), row.getActionId(), "INVALID_PAYLOAD");
            outboxService.markDead(row, null, "INVALID_PAYLOAD");
            return;
        }

        R<Long> response;
        try
        {
            response = remoteSignTaskService.publishEvent(event,
                    SecurityConstants.INNER);
        }
        catch (RuntimeException remoteFailure)
        {
            Integer httpStatus = findHttpStatus(remoteFailure);
            if (httpStatus != null)
            {
                classifyHttp(row, now, httpStatus);
            }
            else
            {
                String code = containsTimeout(remoteFailure)
                        ? "REMOTE_TIMEOUT" : "REMOTE_UNAVAILABLE";
                scheduleRetry(row, now, null, code);
            }
            return;
        }

        if (response == null)
        {
            scheduleRetry(row, now, null, "REMOTE_UNAVAILABLE");
            return;
        }

        if (R.isSuccess(response))
        {
            Long taskId = response.getData();
            if (taskId == null || taskId <= 0)
            {
                scheduleRetry(row, now, R.SUCCESS,
                        "REMOTE_EMPTY_TASK_ID");
                return;
            }
            outboxService.markSent(row, taskId, R.SUCCESS);
            return;
        }

        classifyHttp(row, now, response.getCode());
    }

    private void classifyHttp(SysHrSignEventOutbox row, Instant now,
            int status)
    {
        String errorCode = "REMOTE_HTTP_" + status;
        if (isPermanentHttpStatus(status))
        {
            outboxService.markDead(row, status, errorCode);
            return;
        }
        scheduleRetry(row, now, status, errorCode);
    }

    private void scheduleRetry(SysHrSignEventOutbox row, Instant now,
            Integer httpStatus, String errorCode)
    {
        int previous = row.getRetryCount() == null ? 0
                : row.getRetryCount();
        int delay = RETRY_MINUTES[Math.min(previous,
                RETRY_MINUTES.length - 1)];
        outboxService.markRetry(row, previous + 1,
                Date.from(now.plus(delay, ChronoUnit.MINUTES)),
                httpStatus, errorCode);
    }

    static boolean isPermanentHttpStatus(int status)
    {
        return status >= 400 && status < 500
                && status != 408 && status != 425 && status != 429;
    }

    private Integer findHttpStatus(Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof FeignException feign
                    && feign.status() > 0)
            {
                return feign.status();
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean containsTimeout(Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException)
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
