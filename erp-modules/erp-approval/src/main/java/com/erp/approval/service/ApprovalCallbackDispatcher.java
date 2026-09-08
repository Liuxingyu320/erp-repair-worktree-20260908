package com.erp.approval.service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.approval.api.domain.ApprovalBusinessCallbackRequest;
import com.erp.approval.callback.ApprovalBusinessCallbackRegistry;
import com.erp.approval.callback.ApprovalBusinessCallbackResult;
import com.erp.approval.domain.ApprovalCallbackOutbox;

@Component
public class ApprovalCallbackDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(
            ApprovalCallbackDispatcher.class);
    private static final int BATCH_SIZE = 30;

    private final String worker = "approval-" + UUID.randomUUID();
    private final ApprovalCallbackOutboxService outboxService;
    private final ApprovalBusinessCallbackRegistry callbackRegistry;

    public ApprovalCallbackDispatcher(ApprovalCallbackOutboxService outboxService,
            ApprovalBusinessCallbackRegistry callbackRegistry)
    {
        this.outboxService = outboxService;
        this.callbackRegistry = callbackRegistry;
    }

    @Scheduled(fixedDelayString =
            "${approval.callback.dispatch-fixed-delay-ms:15000}")
    public void dispatch()
    {
        for (ApprovalCallbackOutbox candidate : outboxService
                .dispatchable(BATCH_SIZE))
        {
            ApprovalCallbackOutbox claimed = outboxService.claim(
                    candidate.getOutboxId(), candidate.getLockVersion(), worker,
                    new Date(System.currentTimeMillis() + Duration.ofMinutes(2)
                            .toMillis()));
            if (claimed == null)
            {
                continue;
            }
            deliver(claimed);
        }
    }

    private void deliver(ApprovalCallbackOutbox outbox)
    {
        try
        {
            ApprovalBusinessCallbackRequest request =
                    new ApprovalBusinessCallbackRequest();
            request.setEventKey(outbox.getEventKey());
            request.setInstanceId(outbox.getInstanceId());
            request.setBusinessCode(outbox.getBusinessCode());
            request.setBusinessId(outbox.getBusinessId());
            request.setBusinessRound(outbox.getBusinessRound());
            request.setAction(outbox.getCallbackAction());
            request.setPayload(outbox.getPayload());
            ApprovalBusinessCallbackResult result = callbackRegistry
                    .require(outbox.getCallbackCode()).deliver(request);
            if (result.success())
            {
                outboxService.succeed(outbox.getOutboxId(),
                        outbox.getLockVersion(), result, worker);
                return;
            }
            fail(outbox, result.code(), result.message());
        }
        catch (RuntimeException exception)
        {
            log.error("审批业务回调失败，outboxId={}, type={}",
                    outbox.getOutboxId(), exception.getClass().getSimpleName());
            fail(outbox, exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
    }

    private void fail(ApprovalCallbackOutbox outbox, String code,
            String message)
    {
        int retry = (outbox.getRetryCount() == null ? 0
                : outbox.getRetryCount()) + 1;
        long delaySeconds = Math.min(3600L,
                30L * (1L << Math.min(retry - 1, 7)));
        outboxService.fail(outbox.getOutboxId(), outbox.getLockVersion(), code,
                message == null ? "未知回调错误" : message,
                new Date(System.currentTimeMillis() + delaySeconds * 1000L),
                worker);
    }
}
