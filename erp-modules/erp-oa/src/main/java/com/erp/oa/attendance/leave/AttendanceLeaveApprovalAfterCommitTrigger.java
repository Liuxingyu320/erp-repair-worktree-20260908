package com.erp.oa.attendance.leave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AttendanceLeaveApprovalAfterCommitTrigger
{
    private static final Logger log = LoggerFactory.getLogger(
            AttendanceLeaveApprovalAfterCommitTrigger.class);
    private final AttendanceLeaveApprovalDispatcher dispatcher;

    public AttendanceLeaveApprovalAfterCommitTrigger(
            AttendanceLeaveApprovalDispatcher dispatcher)
    { this.dispatcher = dispatcher; }

    public void trigger(Long outboxId)
    {
        if (outboxId == null
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
        {
            log.warn("leave approval after-commit trigger not registered, outboxId={}",
                    outboxId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCommit()
                    {
                        try { dispatcher.dispatchOneNow(outboxId); }
                        catch (RuntimeException failure)
                        {
                            log.error("leave approval immediate dispatch failed, outboxId={}, type={}",
                                    outboxId,
                                    failure.getClass().getSimpleName());
                        }
                    }
                });
    }
}
