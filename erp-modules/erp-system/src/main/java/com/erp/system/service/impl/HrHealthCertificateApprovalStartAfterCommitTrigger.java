package com.erp.system.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在健康证提交事务真正成功后立即尝试派发。 */
@Component
public class HrHealthCertificateApprovalStartAfterCommitTrigger
{
    private static final Logger log = LoggerFactory.getLogger(
            HrHealthCertificateApprovalStartAfterCommitTrigger.class);
    private final HrHealthCertificateApprovalStartDispatcher dispatcher;

    public HrHealthCertificateApprovalStartAfterCommitTrigger(
            HrHealthCertificateApprovalStartDispatcher dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    public void trigger(Long outboxId)
    {
        if (outboxId == null
                || !TransactionSynchronizationManager
                        .isActualTransactionActive()
                || !TransactionSynchronizationManager
                        .isSynchronizationActive())
        {
            log.warn("健康证审批发起未注册提交后派发，outboxId={}", outboxId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization()
                {
                    @Override
                    public void afterCommit()
                    {
                        try
                        {
                            dispatcher.dispatchOneNow(outboxId);
                        }
                        catch (RuntimeException failure)
                        {
                            log.error("健康证审批提交后即时派发失败，将由定时任务恢复，outboxId={}, type={}",
                                    outboxId,
                                    failure.getClass().getSimpleName());
                        }
                    }
                });
    }
}
