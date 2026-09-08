package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import com.erp.oa.domain.vo.OaSignReminderCandidate;
import com.erp.oa.mapper.OaSignPackageMapper;

class OaSignReminderSchedulerTest
{
    private static final Instant NOW = Instant.parse("2026-07-20T04:00:00Z");

    @Test
    void schedulerIsOptInAndRepositoryConfigKeepsItOff() throws Exception
    {
        ConditionalOnProperty condition = OaSignReminderScheduler.class
                .getAnnotation(ConditionalOnProperty.class);
        Method method = OaSignReminderScheduler.class.getMethod("enqueueDueReminders");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("oa.sign.reminder");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${oa.sign.reminder.fixed-delay-ms:60000}");

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("bootstrap.yml"))
        {
            assertThat(input).isNotNull();
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yaml)
                    .contains("enabled: ${OA_SIGN_REMINDER_ENABLED:false}")
                    .contains("fixed-delay-ms: ${OA_SIGN_REMINDER_FIXED_DELAY_MS:60000}")
                    .contains("enabled: ${OA_SIGN_EMERGENCY_CREATE_ENABLED:false}");
        }
    }

    @Test
    void oneBadReminderDoesNotBlockLaterCandidates()
    {
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignNotificationOutboxService outboxService = mock(OaSignNotificationOutboxService.class);
        OaSignReminderCandidate first = candidate(90L);
        OaSignReminderCandidate second = candidate(91L);
        Date scanTime = Date.from(NOW);
        when(packageMapper.selectReminderCandidates(scanTime, 100))
                .thenReturn(List.of(first, second));
        when(outboxService.enqueueReminder(first))
                .thenThrow(new IllegalStateException("broken reminder"));
        OaSignReminderScheduler scheduler = new OaSignReminderScheduler(packageMapper,
                outboxService, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.enqueueDueReminders();

        verify(outboxService).enqueueReminder(first);
        verify(outboxService).enqueueReminder(second);
    }

    @Test
    void mapperQueryUsesFrozenPolicyExactStageAndTwoChannelIdempotency() throws Exception
    {
        String xml;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "mapper/oa/OaSignPackageMapper.xml"))
        {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(xml).contains(
                "<select id=\"selectReminderCandidates\"",
                "v.reminder_policy_json",
                "$.daysBefore",
                "v.publish_status = 'PUBLISHED'",
                "t.plan_version_id = p.plan_version_id",
                "t.sign_deadline = p.sign_deadline",
                "p.status = 'pending_final_confirm' then 'FINAL' else 'INITIAL'",
                "p.final_document_version else p.document_version",
                "count(distinct notification.channel)",
                "notification.channel in ('IN_APP', 'MOBILE_PUSH')",
                "BINARY notification.business_key = BINARY concat(",
                "convert_tz(p.sign_deadline, '+08:00', '+00:00')",
                ") &lt; 2");
    }

    private static OaSignReminderCandidate candidate(Long packageId)
    {
        OaSignReminderCandidate candidate = new OaSignReminderCandidate();
        candidate.setPackageId(packageId);
        candidate.setTaskId(9L);
        candidate.setEmployeeId(201L);
        candidate.setAssignedHrUserId(101L);
        candidate.setShopDeptId(1171L);
        candidate.setReminderStage("INITIAL");
        candidate.setActiveDocumentVersion("SP-" + packageId + "-V1");
        candidate.setSignDeadline(Date.from(Instant.parse("2026-07-21T04:00:00Z")));
        candidate.setReminderBeforeHours(24);
        return candidate;
    }
}
