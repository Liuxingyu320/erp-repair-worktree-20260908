package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.core.domain.R;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.api.RemoteSignTaskService;
import com.erp.system.domain.SysHrSignEventOutbox;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class HrSignEventDispatcherTest
{
    private static final Instant NOW = Instant.parse("2026-07-13T04:00:00Z");

    @Mock
    private HrSignEventOutboxService outboxService;
    @Mock
    private RemoteSignTaskService remoteSignTaskService;

    private HrSignEventDispatcher dispatcher;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp()
    {
        dispatcher = new HrSignEventDispatcher(outboxService,
                remoteSignTaskService, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(
                HrSignEventDispatcher.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown()
    {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(
                HrSignEventDispatcher.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void dispatchDueUsesFixedNowFiveMinuteStaleWindowAndBatchLimit()
    {
        when(outboxService.selectDue(Date.from(NOW),
                Date.from(NOW.minus(5, ChronoUnit.MINUTES)), 100))
                .thenReturn(List.of());

        dispatcher.dispatchDue();

        verify(outboxService).selectDue(Date.from(NOW),
                Date.from(NOW.minus(5, ChronoUnit.MINUTES)), 100);
    }

    @Test
    void failedClaimNeverCallsOa()
    {
        SysHrSignEventOutbox row = dueRow(validPayload(), 0);
        due(row);
        when(outboxService.claim(row)).thenReturn(false);

        dispatcher.dispatchDue();

        verify(remoteSignTaskService, never()).publishEvent(any(), any());
    }

    @Test
    void positiveCanonicalTaskIdMarksSent()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER))).thenReturn(R.ok(91L));

        dispatcher.dispatchDue();

        verify(outboxService).markSent(row, 91L, 200);
        verify(outboxService, never()).markRetry(any(), any(), any(), any(), any());
        verify(outboxService, never()).markDead(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidTaskIds")
    void successWithoutPositiveTaskIdRetries(Long taskId)
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER))).thenReturn(R.ok(taskId));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), 200,
                "REMOTE_EMPTY_TASK_ID");
        verify(outboxService, never()).markSent(any(), any(), any());
    }

    static Stream<Arguments> invalidTaskIds()
    {
        return Stream.of(Arguments.of((Long) null), Arguments.of(0L),
                Arguments.of(-1L));
    }

    @ParameterizedTest
    @CsvSource({ "400", "401", "403", "404", "409", "422" })
    void permanentClientErrorsGoDead(int status)
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(status, "sensitive remote message"));

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, status,
                "REMOTE_HTTP_" + status);
        verify(outboxService, never()).markRetry(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({ "408", "425", "429", "500", "502", "503", "504" })
    void transientHttpErrorsRetry(int status)
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(status, "sensitive remote message"));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), status,
                "REMOTE_HTTP_" + status);
        verify(outboxService, never()).markDead(any(), any(), any());
    }

    @Test
    void fallbackResponseCodeIsNotMistakenForBusinessSuccess()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenReturn(R.fail(503, "发布人事签约事件失败"));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), 503,
                "REMOTE_HTTP_503");
    }

    @Test
    void nullResponseRetriesAsUnavailable()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER))).thenReturn(null);

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), null,
                "REMOTE_UNAVAILABLE");
    }

    @Test
    void socketTimeoutRetriesWithStableCode()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenThrow(new CompletionException(new SocketTimeoutException()));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), null,
                "REMOTE_TIMEOUT");
    }

    @Test
    void connectionFailureRetriesAsUnavailable()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenThrow(new CompletionException(new ConnectException()));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), null,
                "REMOTE_UNAVAILABLE");
    }

    @Test
    void ordinaryRemoteFailureRetriesWithoutLoggingExceptionMessage()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER)))
                .thenThrow(new IllegalStateException(
                        "310101199001010019 13800000000"));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, 1,
                Date.from(NOW.plus(1, ChronoUnit.MINUTES)), null,
                "REMOTE_UNAVAILABLE");
        assertThat(formattedLogs()).doesNotContain("310101199001010019")
                .doesNotContain("13800000000");
    }

    @Test
    void invalidJsonGoesDeadWithoutCallingOaOrLeakingPayload()
    {
        String sensitive = "{\"idNumber\":\"310101199001010019\","
                + "\"phone\":\"13800000000\"";
        SysHrSignEventOutbox row = claimedRow(sensitive, 0);

        dispatcher.dispatchDue();

        verify(outboxService).markDead(row, null, "INVALID_PAYLOAD");
        verify(remoteSignTaskService, never()).publishEvent(any(), any());
        String logs = formattedLogs();
        assertThat(logs).doesNotContain("310101199001010019")
                .doesNotContain("13800000000")
                .doesNotContain(sensitive);
    }

    @ParameterizedTest
    @CsvSource({
            "0,1,1",
            "1,2,5",
            "2,3,30",
            "3,4,120",
            "4,5,360",
            "5,6,360",
            "19,20,360"
    })
    void retryBackoffNeverExhausts(int previousRetries, int nextRetries,
            int delayMinutes)
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), previousRetries);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER))).thenReturn(R.fail(503, "down"));

        dispatcher.dispatchDue();

        verify(outboxService).markRetry(row, nextRetries,
                Date.from(NOW.plus(delayMinutes, ChronoUnit.MINUTES)), 503,
                "REMOTE_HTTP_503");
    }

    @Test
    void optimisticConflictIsNotReclassifiedIntoSecondTransition()
    {
        SysHrSignEventOutbox row = claimedRow(validPayload(), 0);
        when(remoteSignTaskService.publishEvent(any(),
                eq(SecurityConstants.INNER))).thenReturn(R.ok(91L));
        org.mockito.Mockito.doThrow(new ServiceException("conflict"))
                .when(outboxService).markSent(row, 91L, 200);

        dispatcher.dispatchDue();

        verify(outboxService, never()).markRetry(any(), any(), any(), any(), any());
        verify(outboxService, never()).markDead(any(), any(), any());
    }

    private SysHrSignEventOutbox claimedRow(String payload, int retryCount)
    {
        SysHrSignEventOutbox row = dueRow(payload, retryCount);
        due(row);
        when(outboxService.claim(row)).thenReturn(true);
        return row;
    }

    private void due(SysHrSignEventOutbox row)
    {
        when(outboxService.selectDue(Date.from(NOW),
                Date.from(NOW.minus(5, ChronoUnit.MINUTES)), 100))
                .thenReturn(List.of(row));
    }

    private SysHrSignEventOutbox dueRow(String payload, int retryCount)
    {
        SysHrSignEventOutbox row = new SysHrSignEventOutbox();
        row.setOutboxId(11L);
        row.setActionId(51L);
        row.setEventVersion(1L);
        row.setPayloadJson(payload);
        row.setStatus("RETRY");
        row.setRetryCount(retryCount);
        row.setVersion(4L);
        return row;
    }

    private String validPayload()
    {
        return "{\"eventId\":\"HR-ACTION:51:1\","
                + "\"scenario\":\"TRANSFER\",\"employeeId\":7,"
                + "\"sourceType\":\"HR_LIFECYCLE_ACTION\","
                + "\"sourceBusinessId\":\"51\","
                + "\"sourceEventVersion\":1}";
    }

    private String formattedLogs()
    {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
