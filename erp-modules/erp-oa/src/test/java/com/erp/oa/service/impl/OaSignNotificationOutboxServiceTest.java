package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;
import com.erp.oa.domain.vo.OaSignReminderCandidate;
import com.erp.oa.mapper.OaSignNotificationOutboxMapper;
import com.erp.system.api.domain.UserNotificationCommand;

@ExtendWith(MockitoExtension.class)
class OaSignNotificationOutboxServiceTest
{
    @Mock
    private OaSignNotificationOutboxMapper mapper;

    private OaSignNotificationOutboxService service;

    @BeforeEach
    void setUp()
    {
        service = new OaSignNotificationOutboxService(mapper);
    }

    @Test
    void businessEventMustCreateSeparateInAppAndMobileRowsWithSafePayload()
    {
        AtomicLong id = new AtomicLong(10);
        when(mapper.insertOaSignNotificationOutbox(any())).thenAnswer(invocation -> {
            OaSignNotificationOutbox row = invocation.getArgument(0);
            row.setOutboxId(id.incrementAndGet());
            return 1;
        });
        UserNotificationCommand command = command();

        List<OaSignNotificationOutbox> rows = service.enqueueBoth(command, 101L, 9L, 1171L);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(OaSignNotificationOutbox::getChannel)
                .containsExactly("IN_APP", "MOBILE_PUSH");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(201L);
            assertThat(row.getBusinessKey()).isEqualTo("SIGN_SENT:90:SP-90-V1");
            assertThat(row.getStatus()).isEqualTo("PENDING");
            assertThat(row.getRetryCount()).isZero();
            assertThat(row.getVersion()).isZero();
            assertThat(row.getPayloadJson())
                    .contains("合同待签署", "OA_SIGN_PACKAGE_SIGN", "packageId", "90",
                            "hrUserId", "101", "taskId", "9", "shopDeptId", "1171")
                    .doesNotContain("identityNumber", "salary", "contractBody", "snapshotHash");
        });
        verify(mapper, times(2)).insertOaSignNotificationOutbox(any());
    }

    @Test
    void duplicateBusinessKeyMustReturnExistingRowsWithoutThrowingOrDuplicating()
    {
        when(mapper.insertOaSignNotificationOutbox(any())).thenReturn(0);
        when(mapper.selectByBusinessKey(any(), any(), any())).thenAnswer(invocation -> {
            OaSignNotificationOutbox row = new OaSignNotificationOutbox();
            row.setOutboxId("IN_APP".equals(invocation.getArgument(0)) ? 21L : 22L);
            row.setChannel(invocation.getArgument(0));
            row.setRecipientUserId(invocation.getArgument(1));
            row.setBusinessKey(invocation.getArgument(2));
            return row;
        });

        List<OaSignNotificationOutbox> rows = service.enqueueBoth(command(), 101L, 9L, 1171L);

        assertThat(rows).extracting(OaSignNotificationOutbox::getOutboxId).containsExactly(21L, 22L);
    }

    @Test
    void onlyOneInstanceCanClaimTheSameVersion()
    {
        OaSignNotificationOutbox first = row(31L, "PENDING", 4L, 0);
        OaSignNotificationOutbox second = row(31L, "PENDING", 4L, 0);
        when(mapper.claimForSending(31L, "PENDING", 4L)).thenReturn(1, 0);

        assertThat(service.claim(first)).isTrue();
        assertThat(service.claim(second)).isFalse();
        assertThat(first.getStatus()).isEqualTo("SENDING");
        assertThat(first.getVersion()).isEqualTo(5L);
        assertThat(second.getStatus()).isEqualTo("PENDING");
        assertThat(second.getVersion()).isEqualTo(4L);
    }

    @Test
    void staleSendingRowCanBeReclaimedOnlyOnceByVersion()
    {
        OaSignNotificationOutbox stale = row(32L, "SENDING", 8L, 1);
        OaSignNotificationOutbox competitor = row(32L, "SENDING", 8L, 1);
        when(mapper.claimForSending(32L, "SENDING", 8L)).thenReturn(1, 0);

        assertThat(service.claim(stale)).isTrue();
        assertThat(service.claim(competitor)).isFalse();
        assertThat(stale.getStatus()).isEqualTo("SENDING");
        assertThat(stale.getVersion()).isEqualTo(9L);
    }

    @Test
    void dueReadIsCappedAtOneHundredAndCanRecoverStaleClaims()
    {
        Date now = Date.from(Instant.parse("2026-07-11T12:00:00Z"));
        Date staleBefore = Date.from(Instant.parse("2026-07-11T11:55:00Z"));
        when(mapper.selectDueNotifications(now, staleBefore, 100)).thenReturn(List.of());

        assertThat(service.selectDue(now, staleBefore, 500)).isEmpty();

        verify(mapper).selectDueNotifications(now, staleBefore, 100);
    }

    @Test
    void terminalAndRetryUpdatesUseTheClaimedVersion()
    {
        OaSignNotificationOutbox sent = row(41L, "SENDING", 3L, 0);
        OaSignNotificationOutbox retry = row(42L, "SENDING", 7L, 1);
        OaSignNotificationOutbox dead = row(43L, "SENDING", 9L, 5);
        Date next = Date.from(Instant.parse("2026-07-11T12:01:00Z"));
        when(mapper.markSent(41L, 3L, "DELIVERED")).thenReturn(1);
        when(mapper.markRetry(42L, 7L, 2, next, "REMOTE_TIMEOUT")).thenReturn(1);
        when(mapper.markDead(43L, 9L, "PERMANENT_FAILURE")).thenReturn(1);

        service.markSent(sent, "DELIVERED");
        service.markRetry(retry, 2, next, "REMOTE_TIMEOUT");
        service.markDead(dead, "PERMANENT_FAILURE");

        assertThat(sent.getStatus()).isEqualTo("SENT");
        assertThat(retry.getStatus()).isEqualTo("RETRY");
        assertThat(retry.getRetryCount()).isEqualTo(2);
        assertThat(retry.getNextRetryTime()).isEqualTo(next);
        assertThat(dead.getStatus()).isEqualTo("DEAD");
    }

    @Test
    void businessKeysSeparateEveryStageVersionDeadlineAndOffset()
    {
        Date deadline = Date.from(Instant.parse("2026-07-31T15:59:59Z"));

        assertThat(OaSignNotificationOutboxService.needsDataKey(9L, "profile-v3"))
                .isEqualTo("SIGN_NEEDS_DATA:9:profile-v3");
        assertThat(OaSignNotificationOutboxService.waitingHrKey(9L, "SP-90-V1"))
                .isEqualTo("SIGN_WAITING_HR:9:SP-90-V1");
        assertThat(OaSignNotificationOutboxService.sentKey(90L, "SP-90-V1"))
                .isEqualTo("SIGN_SENT:90:SP-90-V1");
        assertThat(OaSignNotificationOutboxService.initialSignedKey(90L, "SP-90-V1"))
                .isEqualTo("SIGN_INITIAL_SIGNED:90:SP-90-V1");
        assertThat(OaSignNotificationOutboxService.finalReadyKey(90L, "SP-90-F1"))
                .isEqualTo("SIGN_FINAL_READY:90:SP-90-F1");
        assertThat(OaSignNotificationOutboxService.reminderKey(90L, deadline))
                .isEqualTo("SIGN_REMINDER:90:2026-07-31T15:59:59Z:24H");
        assertThat(OaSignNotificationOutboxService.reminderKey(
                90L, "FINAL", "SP-90-F1", deadline, 72))
                .isEqualTo("SIGN_REMINDER:90:FINAL:SP-90-F1:2026-07-31T15:59:59Z:72H");
        assertThat(OaSignNotificationOutboxService.refusedKey(90L, 700L))
                .isEqualTo("SIGN_REFUSED:90:700");
        assertThat(OaSignNotificationOutboxService.expiredKey(90L, "SP-90-V1"))
                .isEqualTo("SIGN_EXPIRED:90:SP-90-V1");
        assertThat(OaSignNotificationOutboxService.completedKey(90L, "SP-90-V1"))
                .isEqualTo("SIGN_COMPLETED:90:SP-90-V1");
    }

    @Test
    void refusedNotifiesHrAndExpiredNotifiesHrAndEmployeeWithIdempotentKeys()
    {
        AtomicLong id = new AtomicLong(100);
        when(mapper.insertOaSignNotificationOutbox(any())).thenAnswer(invocation -> {
            OaSignNotificationOutbox row = invocation.getArgument(0);
            row.setOutboxId(id.incrementAndGet());
            return 1;
        });
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setEmployeeId(201L);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setPlanVersionId(301L);
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setPlanVersionId(301L);
        signPackage.setDocumentVersion("SP-90-V1");

        List<OaSignNotificationOutbox> refused = service.enqueueRefused(task, signPackage, 700L);
        List<OaSignNotificationOutbox> expired = service.enqueueExpired(task, signPackage);

        assertThat(refused).hasSize(2).allSatisfy(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(101L);
            assertThat(row.getBusinessKey()).isEqualTo("SIGN_REFUSED:90:700");
        });
        assertThat(expired).hasSize(4);
        assertThat(expired).extracting(OaSignNotificationOutbox::getRecipientUserId)
                .containsExactly(101L, 101L, 201L, 201L);
        assertThat(expired).extracting(OaSignNotificationOutbox::getBusinessKey)
                .containsOnly("SIGN_EXPIRED:90:SP-90-V1");
    }

    @Test
    void missingDataInitialFinalReminderAndCompletionUseTheRequiredRecipientsAndRoutes()
    {
        AtomicLong id = new AtomicLong(200);
        when(mapper.insertOaSignNotificationOutbox(any())).thenAnswer(invocation -> {
            OaSignNotificationOutbox row = invocation.getArgument(0);
            row.setOutboxId(id.incrementAndGet());
            return 1;
        });
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setEmployeeId(201L);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setPlanVersionId(301L);
        task.setSourceEventVersion("profile-v3");
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setPlanVersionId(301L);
        signPackage.setDocumentVersion("SP-90-V1");
        signPackage.setFinalDocumentVersion("SP-90-F1");
        OaSignReminderCandidate reminder = new OaSignReminderCandidate();
        reminder.setPackageId(90L);
        reminder.setTaskId(9L);
        reminder.setEmployeeId(201L);
        reminder.setAssignedHrUserId(101L);
        reminder.setShopDeptId(1171L);
        reminder.setPackageStatus("pending_final_confirm");
        reminder.setReminderStage("FINAL");
        reminder.setActiveDocumentVersion("SP-90-F1");
        reminder.setSignDeadline(Date.from(Instant.parse("2026-07-31T15:59:59Z")));
        reminder.setReminderBeforeHours(24);
        when(mapper.lockCurrentReminderCandidate(any(), any())).thenReturn(90L);

        assertThat(service.enqueueNeedsData(task)).hasSize(2)
                .allSatisfy(row -> assertThat(row.getRecipientUserId()).isEqualTo(101L));
        assertThat(service.enqueueInitialSigned(task, signPackage)).hasSize(2)
                .allSatisfy(row -> assertThat(row.getRecipientUserId()).isEqualTo(101L));
        assertThat(service.enqueueFinalReady(task, signPackage)).hasSize(2)
                .allSatisfy(row -> assertThat(row.getRecipientUserId()).isEqualTo(201L));
        assertThat(service.enqueueReminder(reminder)).hasSize(2)
                .allSatisfy(row -> assertThat(row.getRecipientUserId()).isEqualTo(201L));
        List<OaSignNotificationOutbox> completed = service.enqueueCompleted(task, signPackage);
        assertThat(completed).hasSize(4);
        assertThat(completed).extracting(OaSignNotificationOutbox::getRecipientUserId)
                .containsExactly(101L, 101L, 201L, 201L);
        assertThat(completed).extracting(OaSignNotificationOutbox::getBusinessKey)
                .containsOnly("SIGN_COMPLETED:90:SP-90-F1");
        assertThat(completed.get(0).getPayloadJson()).contains("OA_SIGN_HR_TASK", "taskId");
        assertThat(completed.get(2).getPayloadJson()).contains("OA_SIGN_PACKAGE_SIGN", "packageId");
    }

    @Test
    void staleReminderCandidateMustNotCreateAnyOutboxRow()
    {
        OaSignReminderCandidate reminder = new OaSignReminderCandidate();
        reminder.setPackageId(90L);
        reminder.setTaskId(9L);
        reminder.setEmployeeId(201L);
        reminder.setAssignedHrUserId(101L);
        reminder.setShopDeptId(1171L);
        reminder.setPackageStatus("pending_sign");
        reminder.setReminderStage("INITIAL");
        reminder.setActiveDocumentVersion("SP-90-V1");
        reminder.setSignDeadline(Date.from(Instant.parse("2026-07-31T15:59:59Z")));
        reminder.setReminderBeforeHours(24);
        when(mapper.lockCurrentReminderCandidate(any(), any())).thenReturn(null);

        assertThat(service.enqueueReminder(reminder)).isEmpty();

        verify(mapper, times(0)).insertOaSignNotificationOutbox(any());
    }

    @Test
    void currentHrCanRequeueLegacyDeadChannelsAfterHrReassignment()
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setAssignedHrUserId(101L);
        OaSignNotificationOutbox inApp = row(91L, "DEAD", 4L, 5);
        inApp.setChannel("IN_APP");
        inApp.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        inApp.setPayloadJson("{\"routeType\":\"OA_SIGN_HR_TASK\",\"hrUserId\":101,\"taskId\":9}");
        OaSignNotificationOutbox mobile = row(92L, "DEAD", 6L, 5);
        mobile.setChannel("MOBILE_PUSH");
        mobile.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        mobile.setPayloadJson("{\"routeType\":\"OA_SIGN_HR_TASK\",\"hrUserId\":101,\"taskId\":9}");
        when(mapper.selectNotificationsForTaskBusinessKey(101L, 9L, "SIGN_SENT:90:SP-90-V1"))
                .thenReturn(List.of(inApp, mobile));
        when(mapper.requeueDead(91L, 4L, 9L, 101L)).thenReturn(1);
        when(mapper.requeueDead(92L, 6L, 9L, 101L)).thenReturn(1);

        int requeued = service.requeueDead(task, "SIGN_SENT:90:SP-90-V1");

        assertThat(requeued).isEqualTo(2);
        assertThat(List.of(inApp, mobile)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo("PENDING");
            assertThat(row.getRetryCount()).isZero();
        });
    }

    @Test
    void reassignmentBetweenReadAndRequeueCasMustLeaveTheDeadRowClosed()
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setAssignedHrUserId(101L);
        OaSignNotificationOutbox dead = row(93L, "DEAD", 7L, 5);
        dead.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        when(mapper.selectNotificationsForTaskBusinessKey(101L, 9L, dead.getBusinessKey()))
                .thenReturn(List.of(dead));
        when(mapper.requeueDead(93L, 7L, 9L, 101L)).thenReturn(0);

        assertThat(service.requeueDead(task, dead.getBusinessKey())).isZero();
        assertThat(dead.getStatus()).isEqualTo("DEAD");
        assertThat(dead.getVersion()).isEqualTo(7L);
    }

    @Test
    void notificationCreationRejectsEveryBrokenTaskPackageIdentityLink()
    {
        OaSignTask task = linkedTask();
        OaSignPackage signPackage = linkedPackage();
        signPackage.setTaskId(10L);
        assertRejectedLink(task, signPackage);

        task = linkedTask();
        signPackage = linkedPackage();
        task.setPackageId(91L);
        assertRejectedLink(task, signPackage);

        task = linkedTask();
        signPackage = linkedPackage();
        task.setEmployeeId(202L);
        assertRejectedLink(task, signPackage);

        task = linkedTask();
        signPackage = linkedPackage();
        task.setShopDeptId(1172L);
        assertRejectedLink(task, signPackage);

        task = linkedTask();
        signPackage = linkedPackage();
        task.setPlanVersionId(302L);
        assertRejectedLink(task, signPackage);

        task = linkedTask();
        signPackage = linkedPackage();
        signPackage.setPlanVersionId(null);
        assertRejectedLink(task, signPackage);
    }

    private void assertRejectedLink(OaSignTask task, OaSignPackage signPackage)
    {
        assertThatThrownBy(() -> service.enqueueWaitingHr(task, signPackage))
                .isInstanceOf(ServiceException.class)
                .hasMessage("签约通知上下文不完整");
    }

    private static OaSignTask linkedTask()
    {
        OaSignTask task = new OaSignTask();
        task.setTaskId(9L);
        task.setPackageId(90L);
        task.setEmployeeId(201L);
        task.setAssignedHrUserId(101L);
        task.setShopDeptId(1171L);
        task.setPlanVersionId(301L);
        return task;
    }

    private static OaSignPackage linkedPackage()
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(90L);
        signPackage.setTaskId(9L);
        signPackage.setEmployeeId(201L);
        signPackage.setShopDeptId(1171L);
        signPackage.setPlanVersionId(301L);
        signPackage.setDocumentVersion("SP-90-V1");
        return signPackage;
    }

    private static UserNotificationCommand command()
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setRecipientUserId(201L);
        command.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        command.setTitle("合同待签署");
        command.setBody("请进入系统阅读并签署合同");
        command.setRouteType("OA_SIGN_PACKAGE_SIGN");
        command.setRouteParams("{\"packageId\":90}");
        return command;
    }

    private static OaSignNotificationOutbox row(Long id, String status, Long version, int retryCount)
    {
        OaSignNotificationOutbox row = new OaSignNotificationOutbox();
        row.setOutboxId(id);
        row.setStatus(status);
        row.setVersion(version);
        row.setRetryCount(retryCount);
        return row;
    }
}
