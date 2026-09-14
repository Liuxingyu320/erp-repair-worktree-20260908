package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.erp.system.api.domain.UserNotificationResult;
import com.erp.system.config.InAppPushProperties;
import com.erp.system.domain.SysUserNotificationPushOutbox;
import com.erp.system.mapper.SysUserNotificationPushOutboxMapper;
import com.erp.system.service.ISysUserNotificationService;

@ExtendWith(MockitoExtension.class)
class InAppPushOutboxDispatcherTest
{
    private static final Instant NOW = Instant.parse("2026-09-12T04:00:00Z");
    @Mock SysUserNotificationPushOutboxMapper mapper;
    @Mock ISysUserNotificationService notificationService;
    InAppPushProperties properties;
    InAppPushOutboxDispatcher dispatcher;

    @BeforeEach void setup()
    {
        properties = new InAppPushProperties();
        dispatcher = new InAppPushOutboxDispatcher(mapper, notificationService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void defaultDisabledDoesNotReadNewTableOrHistoricalMessages()
    {
        dispatcher.dispatchDue();
        verifyNoInteractions(mapper, notificationService);
    }

    @Test void losingConcurrentClaimNeverCallsProvider()
    {
        properties.setEnabled(true);
        when(mapper.selectDue(any(), any(), eq(50))).thenReturn(List.of(row()));
        when(mapper.claim(eq(1L), eq(0L), any(), any())).thenReturn(0);
        dispatcher.dispatchDue();
        verifyNoInteractions(notificationService);
        verify(mapper, never()).finishAttempt(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({ "DELIVERED,true,SENT", "DUPLICATE,true,SENT", "NO_DEVICE,true,SKIPPED",
            "PERMANENT_FAILURE,false,DEAD", "DISABLED,false,RETRY", "RETRYABLE_FAILURE,false,RETRY" })
    void resultClassificationNeverRecordsDisabledOrMissingDevicesAsSent(String resultStatus,
            boolean accepted, String expected)
    {
        ready(row());
        UserNotificationResult result = new UserNotificationResult();
        result.setAccepted(accepted);
        result.setStatus(resultStatus);
        when(notificationService.publish(any())).thenReturn(result);

        dispatcher.dispatchDue();

        verify(mapper).finishAttempt(1L, 1L, expected, resultStatus,
                "RETRY".equals(expected) ? Date.from(NOW.plusSeconds(30)) : null, Date.from(NOW));
    }

    @Test void crashedSendingClaimCanBeReclaimedWithStaleTimestampAndSamePayload()
    {
        SysUserNotificationPushOutbox row = row();
        row.setStatus("SENDING");
        row.setVersion(4L);
        row.setAttemptCount(2);
        ready(row);
        UserNotificationResult result = new UserNotificationResult();
        result.setAccepted(true);
        result.setStatus("DUPLICATE");
        when(notificationService.publish(any())).thenReturn(result);

        dispatcher.dispatchDue();

        verify(mapper).claim(1L, 4L, Date.from(NOW), Date.from(NOW.minusSeconds(300)));
        verify(mapper).finishAttempt(1L, 5L, "SENT", "DUPLICATE", null, Date.from(NOW));
    }

    @Test void callFailureRetriesAndStopsAtBoundedAttemptLimit()
    {
        SysUserNotificationPushOutbox row = row();
        row.setAttemptCount(11);
        ready(row);
        when(notificationService.publish(any())).thenThrow(new IllegalStateException("do-not-log-token"));

        dispatcher.dispatchDue();

        verify(mapper).finishAttempt(1L, 1L, "DEAD", "RETRIES_EXHAUSTED_PUSH_CALL_FAILED", null, Date.from(NOW));
    }

    @Test void expiredQueuedMessageNeverReachesProvider()
    {
        SysUserNotificationPushOutbox row = row();
        row.setCreateTime(Date.from(NOW.minusSeconds(86400)));
        ready(row);
        dispatcher.dispatchDue();
        verifyNoInteractions(notificationService);
        verify(mapper).finishAttempt(1L, 1L, "DEAD", "EXPIRED", null, Date.from(NOW));
    }

    @Test void repeatedProcessCrashesCannotReclaimPastTheAttemptLimit()
    {
        SysUserNotificationPushOutbox row = row();
        row.setStatus("SENDING");
        row.setAttemptCount(12);
        ready(row);
        dispatcher.dispatchDue();
        verifyNoInteractions(notificationService);
        verify(mapper).finishAttempt(1L, 1L, "DEAD", "RETRIES_EXHAUSTED", null, Date.from(NOW));
    }

    @Test void invalidQueuedRouteNeverReachesProvider()
    {
        SysUserNotificationPushOutbox row = row();
        row.setPayloadJson("{\"channel\":\"MOBILE_PUSH\",\"routeType\":\"EXTERNAL_URL\"}");
        ready(row);
        dispatcher.dispatchDue();
        verifyNoInteractions(notificationService);
        verify(mapper).finishAttempt(1L, 1L, "DEAD", "INVALID_PAYLOAD", null, Date.from(NOW));
    }

    @Test void mapperBindsAtomicClaimsAndMigrationCopiesContainNoHistoricalBackfill() throws Exception
    {
        Configuration configuration = new Configuration();
        String resource = "mapper/system/SysUserNotificationPushOutboxMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String ns = SysUserNotificationPushOutboxMapper.class.getName() + ".";
        for (String method : List.of("insert", "selectDue", "claim", "finishAttempt"))
            assertThat(configuration.hasStatement(ns + method)).isTrue();
        String claim = configuration.getMappedStatement(ns + "claim").getBoundSql(Map.of(
                "outboxId", 1L, "version", 0L, "now", Date.from(NOW),
                "staleBefore", Date.from(NOW.minusSeconds(300)))).getSql().replaceAll("\\s+", " ");
        assertThat(claim).contains("version = ?", "status in ('PENDING', 'RETRY')",
                "next_attempt_at <= ?", "status = 'SENDING' and updated_time <= ?");
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.exists(root.resolve("sql"))) root = root.resolve("../..").normalize();
        String file = "erp_system_notification_push_outbox_20260912.sql";
        String migration = Files.readString(root.resolve("sql").resolve(file));
        assertThat(migration).isEqualTo(Files.readString(root.resolve("docker/mysql/db").resolve(file)))
                .isEqualTo(Files.readString(root.resolve("erp-modules/erp-system/src/main/resources/db/migration").resolve(file)))
                .contains("UNIQUE KEY uk_notification_push_outbox_notification (notification_id)")
                .doesNotContain("INSERT INTO", "SELECT ", "ALTER TABLE", "DROP TABLE");
    }

    private void ready(SysUserNotificationPushOutbox row)
    {
        properties.setEnabled(true);
        when(mapper.selectDue(any(), any(), eq(50))).thenReturn(List.of(row));
        when(mapper.claim(eq(1L), eq(row.getVersion()), any(), any())).thenReturn(1);
        when(mapper.finishAttempt(eq(1L), any(), any(), any(), any(), any())).thenReturn(1);
    }

    private SysUserNotificationPushOutbox row()
    {
        SysUserNotificationPushOutbox row = new SysUserNotificationPushOutbox();
        row.setOutboxId(1L);
        row.setNotificationId(81L);
        row.setPayloadJson(JSON.toJSONString(InAppNotificationWriter.pushCommand(InAppNotificationWriterTest.notification())));
        row.setStatus("PENDING");
        row.setVersion(0L);
        row.setAttemptCount(0);
        row.setCreateTime(Date.from(NOW.minusSeconds(5)));
        return row;
    }
}
