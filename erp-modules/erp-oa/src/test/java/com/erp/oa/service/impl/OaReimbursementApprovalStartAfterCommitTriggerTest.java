package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("OA报销审批发起提交后触发器")
class OaReimbursementApprovalStartAfterCommitTriggerTest
{
    @AfterEach
    void cleanTransactionState()
    {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务提交前绝不派发远端请求")
    void shouldDispatchOnlyAfterCommit()
    {
        OaReimbursementApprovalStartDispatcher dispatcher = mock(
                OaReimbursementApprovalStartDispatcher.class);
        OaReimbursementApprovalStartAfterCommitTrigger trigger =
                new OaReimbursementApprovalStartAfterCommitTrigger(dispatcher);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        trigger.trigger(10L);
        verify(dispatcher, never()).dispatchOneNow(10L);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCommit();
        }

        verify(dispatcher).dispatchOneNow(10L);
    }

    @Test
    @DisplayName("本地事务回滚时不执行已注册的派发")
    void shouldNotDispatchAfterRollback()
    {
        OaReimbursementApprovalStartDispatcher dispatcher = mock(
                OaReimbursementApprovalStartDispatcher.class);
        OaReimbursementApprovalStartAfterCommitTrigger trigger =
                new OaReimbursementApprovalStartAfterCommitTrigger(dispatcher);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        trigger.trigger(10L);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations())
        {
            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(dispatcher, never()).dispatchOneNow(10L);
    }

    @Test
    @DisplayName("无本地事务时不绕过边界直接派发")
    void shouldNotDispatchWithoutTransaction()
    {
        OaReimbursementApprovalStartDispatcher dispatcher = mock(
                OaReimbursementApprovalStartDispatcher.class);
        OaReimbursementApprovalStartAfterCommitTrigger trigger =
                new OaReimbursementApprovalStartAfterCommitTrigger(dispatcher);

        trigger.trigger(10L);

        verify(dispatcher, never()).dispatchOneNow(10L);
    }

    @Test
    @DisplayName("提交后即时派发故障不把已成功的业务提交伪装成失败")
    void shouldContainAfterCommitDispatchFailure()
    {
        OaReimbursementApprovalStartDispatcher dispatcher = mock(
                OaReimbursementApprovalStartDispatcher.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(dispatcher).dispatchOneNow(10L);
        OaReimbursementApprovalStartAfterCommitTrigger trigger =
                new OaReimbursementApprovalStartAfterCommitTrigger(dispatcher);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        trigger.trigger(10L);

        assertThatCode(() ->
        {
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations())
            {
                synchronization.afterCommit();
            }
        }).doesNotThrowAnyException();
        verify(dispatcher).dispatchOneNow(10L);
    }
}
