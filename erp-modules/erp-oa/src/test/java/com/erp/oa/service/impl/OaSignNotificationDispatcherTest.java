package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.oa.ErpOaApplication;
import com.erp.oa.domain.OaSignNotificationOutbox;
import com.erp.system.api.RemoteNotificationService;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.api.domain.UserNotificationResult;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;

@ExtendWith(MockitoExtension.class)
class OaSignNotificationDispatcherTest
{
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Mock
    private OaSignNotificationOutboxService outboxService;

    @Mock
    private RemoteNotificationService remoteNotificationService;

    private OaSignNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp()
    {
        dispatcher = new OaSignNotificationDispatcher(outboxService, remoteNotificationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void schedulerRunsEveryThirtySecondsAndApplicationEnablesScheduling() throws Exception
    {
        Method scheduledMethod = OaSignNotificationDispatcher.class.getMethod("dispatchDue");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(30_000L);
        assertThat(ErpOaApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }

    @Test
    void claimedRowIsPublishedOutsideClaimAndMarkedSent()
    {
        OaSignNotificationOutbox row = row(51L, "IN_APP", 0, payload());
        when(outboxService.selectDue(Date.from(NOW), Date.from(NOW.minusSeconds(300)), 100))
                .thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(true);
        UserNotificationResult delivered = result(true, "CREATED");
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(delivered));

        dispatcher.dispatchDue();

        ArgumentCaptor<UserNotificationCommand> command = ArgumentCaptor.forClass(UserNotificationCommand.class);
        verify(remoteNotificationService).publish(command.capture(), eq(SecurityConstants.INNER));
        assertThat(command.getValue().getChannel()).isEqualTo("IN_APP");
        assertThat(command.getValue().getRecipientUserId()).isEqualTo(201L);
        assertThat(command.getValue().getBusinessKey()).isEqualTo("SIGN_SENT:90:SP-90-V1");
        assertThat(command.getValue().getRouteParams()).isEqualTo("{\"packageId\":90}");
        verify(outboxService).markSent(row, "CREATED");
    }

    @Test
    void rowLostToAnotherInstanceIsSkipped()
    {
        OaSignNotificationOutbox row = row(52L, "MOBILE_PUSH", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(false);

        dispatcher.dispatchDue();

        verify(remoteNotificationService, never()).publish(any(), any());
    }

    @Test
    void inAppSuccessIsCommittedEvenWhenMobileChannelNeedsRetry()
    {
        OaSignNotificationOutbox inApp = row(53L, "IN_APP", 0, payload());
        OaSignNotificationOutbox mobile = row(54L, "MOBILE_PUSH", 0, payload());
        when(outboxService.selectDue(Date.from(NOW), Date.from(NOW.minusSeconds(300)), 100))
                .thenReturn(List.of(inApp, mobile));
        when(outboxService.claim(inApp)).thenReturn(true);
        when(outboxService.claim(mobile)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(result(true, "CREATED")),
                        R.ok(result(false, "RETRYABLE_FAILURE")));

        dispatcher.dispatchDue();

        verify(outboxService).markSent(inApp, "CREATED");
        verify(outboxService).markRetry(mobile, 1,
                Date.from(NOW.plusSeconds(60)), "RETRYABLE_FAILURE");
    }

    @Test
    void retryableFailuresUseFixedBackoffSequence()
    {
        int[] existingRetryCounts = { 0, 1, 2, 3, 4 };
        int[] expectedMinutes = { 1, 5, 30, 120, 360 };
        for (int index = 0; index < existingRetryCounts.length; index++)
        {
            OaSignNotificationOutbox row = row(60L + index, "MOBILE_PUSH",
                    existingRetryCounts[index], payload());
            when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
            when(outboxService.claim(row)).thenReturn(true);
            when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                    .thenReturn(R.ok(result(false, "RETRYABLE_FAILURE")));

            dispatcher.dispatchDue();

            verify(outboxService).markRetry(row, existingRetryCounts[index] + 1,
                    Date.from(NOW.plusSeconds(expectedMinutes[index] * 60L)), "RETRYABLE_FAILURE");
            org.mockito.Mockito.reset(outboxService, remoteNotificationService);
        }
    }

    @Test
    void failureAfterFifthRetryBecomesDeadAndIsNotRetriedAgain()
    {
        OaSignNotificationOutbox row = row(71L, "MOBILE_PUSH", 5, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.ok(result(false, "RETRYABLE_FAILURE")));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, "RETRYABLE_FAILURE");
        verify(outboxService, never()).markRetry(any(), any(Integer.class), any(), any());
    }

    @Test
    void invalidUserAndPermissionFailuresArePermanentButTimeoutsRetry()
    {
        OaSignNotificationOutbox invalidUser = row(81L, "IN_APP", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(invalidUser));
        when(outboxService.claim(invalidUser)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail("接收用户无效"));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(invalidUser, "接收用户无效");

        org.mockito.Mockito.reset(outboxService, remoteNotificationService);
        OaSignNotificationOutbox timeout = row(82L, "MOBILE_PUSH", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(timeout));
        when(outboxService.claim(timeout)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenThrow(new RuntimeException("Read timed out"));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(timeout, 1, Date.from(NOW.plusSeconds(60)), "REMOTE_TIMEOUT");
    }

    @Test
    void malformedStoredPayloadIsAPermanentFailureAndNeverReachesTheRemoteService()
    {
        OaSignNotificationOutbox row = row(83L, "IN_APP", 0, "{not-json");
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(true);

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, "INVALID_PAYLOAD");
        verify(remoteNotificationService, never()).publish(any(), any());
    }

    @Test
    void systemParameterValidationErrorsArePermanent()
    {
        OaSignNotificationOutbox row = row(84L, "IN_APP", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail("消息接收用户不能为空"));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, "消息接收用户不能为空");
    }

    @Test
    void feignHttpPermissionIsPermanentButRateLimitIsRetryable()
    {
        OaSignNotificationOutbox forbidden = row(85L, "IN_APP", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(forbidden));
        when(outboxService.claim(forbidden)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenThrow(httpFailure(403));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(forbidden, "REMOTE_HTTP_403");

        org.mockito.Mockito.reset(outboxService, remoteNotificationService);
        OaSignNotificationOutbox rateLimited = row(86L, "MOBILE_PUSH", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(rateLimited));
        when(outboxService.claim(rateLimited)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenThrow(httpFailure(429));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(rateLimited, 1, Date.from(NOW.plusSeconds(60)), "REMOTE_HTTP_429");
    }

    @Test
    void fallbackHttp422IsPermanentEvenWithGenericSafeMessage()
    {
        OaSignNotificationOutbox row = row(87L, "IN_APP", 0, payload());
        when(outboxService.selectDue(any(), any(), eq(100))).thenReturn(List.of(row));
        when(outboxService.claim(row)).thenReturn(true);
        when(remoteNotificationService.publish(any(), eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(422, "发布定向用户消息失败"));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, "发布定向用户消息失败");
    }

    private static OaSignNotificationOutbox row(Long id, String channel, int retryCount, String payload)
    {
        OaSignNotificationOutbox row = new OaSignNotificationOutbox();
        row.setOutboxId(id);
        row.setChannel(channel);
        row.setRecipientUserId(201L);
        row.setBusinessKey("SIGN_SENT:90:SP-90-V1");
        row.setPayloadJson(payload);
        row.setStatus("PENDING");
        row.setRetryCount(retryCount);
        row.setVersion(0L);
        return row;
    }

    private static String payload()
    {
        return "{\"title\":\"合同待签署\",\"body\":\"请进入系统阅读并签署合同\","
                + "\"routeType\":\"OA_SIGN_PACKAGE_SIGN\","
                + "\"routeParams\":\"{\\\"packageId\\\":90}\","
                + "\"hrUserId\":101,\"taskId\":9,\"shopDeptId\":1171}";
    }

    private static UserNotificationResult result(boolean accepted, String status)
    {
        UserNotificationResult result = new UserNotificationResult();
        result.setAccepted(accepted);
        result.setStatus(status);
        return result;
    }

    private static FeignException httpFailure(int status)
    {
        Request request = Request.create(Request.HttpMethod.POST, "http://erp-system/user-notification",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder().status(status).reason("test")
                .request(request).build();
        return FeignException.errorStatus("publish", response);
    }
}
