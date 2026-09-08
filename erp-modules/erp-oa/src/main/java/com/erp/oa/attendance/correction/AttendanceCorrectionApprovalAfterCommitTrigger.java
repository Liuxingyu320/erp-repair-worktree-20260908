package com.erp.oa.attendance.correction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Starts the durable dispatch only after the request/outbox transaction commits. */
@Component
public class AttendanceCorrectionApprovalAfterCommitTrigger
{
    private static final Logger log = LoggerFactory.getLogger(
            AttendanceCorrectionApprovalAfterCommitTrigger.class);
    private final AttendanceCorrectionApprovalDispatcher dispatcher;

    public AttendanceCorrectionApprovalAfterCommitTrigger(
            AttendanceCorrectionApprovalDispatcher dispatcher)
    { this.dispatcher = dispatcher; }

    public void trigger(Long outboxId)
    {
        if (outboxId == null
                || !TransactionSynchronizationManager
                        .isActualTransactionActive()
                || !TransactionSynchronizationManager
                        .isSynchronizationActive())
        {
            log.warn("correction approval after-commit trigger not registered, outboxId={}",
                    outboxId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override public void afterCommit()
                    {
                        try { dispatcher.dispatchOneNow(outboxId); }
                        catch (RuntimeException failure)
                        {
                            log.error("correction approval immediate dispatch failed, outboxId={}, type={}",
                                    outboxId,
                                    failure.getClass().getSimpleName());
                        }
                    }
                });
    }
}
