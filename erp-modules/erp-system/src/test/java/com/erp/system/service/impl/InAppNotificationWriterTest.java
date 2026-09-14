package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson2.JSON;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.config.InAppPushProperties;
import com.erp.system.domain.SysUserNotification;
import com.erp.system.domain.SysUserNotificationPushOutbox;
import com.erp.system.mapper.SysUserNotificationMapper;
import com.erp.system.mapper.SysUserNotificationPushOutboxMapper;

@ExtendWith(MockitoExtension.class)
class InAppNotificationWriterTest
{
    @Mock SysUserNotificationMapper notificationMapper;
    @Mock SysUserNotificationPushOutboxMapper outboxMapper;
    InAppPushProperties properties;
    InAppNotificationWriter writer;

    @BeforeEach void setup()
    {
        properties = new InAppPushProperties();
        writer = new InAppNotificationWriter(notificationMapper, outboxMapper, properties);
    }

    @Test void disabledByDefaultNeverTouchesNewTable()
    {
        when(notificationMapper.insertNotification(any())).thenReturn(1);
        assertThat(writer.insert(notification())).isEqualTo(1);
        verifyNoInteractions(outboxMapper);
    }

    @Test void duplicateNeverBackfillsHistoricalMessageAfterEnablement()
    {
        properties.setEnabled(true);
        when(notificationMapper.insertNotification(any())).thenReturn(0);
        assertThat(writer.insert(notification())).isZero();
        verifyNoInteractions(outboxMapper);
    }

    @Test void newMessageAndPushIntentCommitInTheSameTransaction()
    {
        properties.setEnabled(true);
        RecordingTransactions transactions = new RecordingTransactions();
        when(notificationMapper.insertNotification(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });
        when(outboxMapper.insert(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return 1;
        });

        assertThat(proxiedWriter(transactions).insert(notification())).isEqualTo(1);

        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
        ArgumentCaptor<SysUserNotificationPushOutbox> saved = ArgumentCaptor.forClass(SysUserNotificationPushOutbox.class);
        verify(outboxMapper).insert(saved.capture());
        UserNotificationCommand command = JSON.parseObject(saved.getValue().getPayloadJson(), UserNotificationCommand.class);
        assertThat(command.getChannel()).isEqualTo("MOBILE_PUSH");
        assertThat(command.getRecipientUserId()).isEqualTo(42L);
        assertThat(command.getBusinessKey()).isEqualTo("SIGN_WAITING_HR:9:3");
        assertThat(command.getRouteType()).isEqualTo("OA_SIGN_HR_TASK");
        assertThat(command.getRouteParams()).isEqualTo("{\"taskId\":9}");
    }

    @Test void outboxWriteFailureRollsBackTheMessageTransaction()
    {
        properties.setEnabled(true);
        when(notificationMapper.insertNotification(any())).thenReturn(1);
        when(outboxMapper.insert(any())).thenReturn(0);
        RecordingTransactions transactions = new RecordingTransactions();

        assertThatThrownBy(() -> proxiedWriter(transactions).insert(notification()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("推送任务保存失败");

        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test void mirroredSignCommandSharesTheExistingDirectPushLedgerHash()
    {
        SysUserNotification notification = notification();
        UserNotificationCommand mirror = InAppNotificationWriter.pushCommand(notification);
        UserNotificationCommand direct = new UserNotificationCommand();
        direct.setChannel("MOBILE_PUSH");
        direct.setRecipientUserId(42L);
        direct.setBusinessKey("SIGN_WAITING_HR:9:3");
        direct.setTitle(notification.getTitle());
        direct.setBody(notification.getBody());
        direct.setRouteType("OA_SIGN_HR_TASK");
        direct.setRouteParams("{\"taskId\":9}");
        assertThat(SysUserNotificationServiceImpl.pushPayloadHash(mirror))
                .isEqualTo(SysUserNotificationServiceImpl.pushPayloadHash(direct));
    }

    @Test void healthReminderSharesExistingMobileBusinessKeyWhileUnknownRouteUsesOwnMessageId()
    {
        SysUserNotification health = notification();
        health.setRouteType("HR_HEALTH_CERT_DUE");
        health.setBusinessKey("HR_HEALTH_CERT:19:2026-10-01:30:IN_APP");
        health.setRouteParams("{\"userId\":42,\"certificateId\":19}");
        UserNotificationCommand mirror = InAppNotificationWriter.pushCommand(health);
        assertThat(mirror.getBusinessKey()).isEqualTo("HR_HEALTH_CERT:19:2026-10-01:30:MOBILE_PUSH");
        assertThat(mirror.getRouteParams()).isEqualTo(health.getRouteParams());
        SysUserNotification other = notification();
        other.setRouteType("SOME_BUSINESS_EVENT");
        other.setRouteParams("{\"url\":\"https://invalid.example\"}");
        UserNotificationCommand generic = InAppNotificationWriter.pushCommand(other);
        assertThat(generic.getRouteType()).isEqualTo("USER_NOTIFICATION");
        assertThat(generic.getRouteParams()).isEqualTo("{\"notificationId\":81}");
    }

    private InAppNotificationWriter proxiedWriter(RecordingTransactions transactions)
    {
        ProxyFactory factory = new ProxyFactory(writer);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (InAppNotificationWriter) factory.getProxy();
    }

    static SysUserNotification notification()
    {
        SysUserNotification notification = new SysUserNotification();
        notification.setNotificationId(81L);
        notification.setUserId(42L);
        notification.setChannel("IN_APP");
        notification.setBusinessKey("SIGN_WAITING_HR:9:3");
        notification.setTitle("合同待确认");
        notification.setBody("请处理合同");
        notification.setRouteType("OA_SIGN_HR_TASK");
        notification.setRouteParams("{\"taskId\":9}");
        return notification;
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager
    {
        int commits;
        int rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
