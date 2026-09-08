package com.erp.oa.service.impl;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.erp.oa.domain.vo.OaSignReminderCandidate;
import com.erp.oa.mapper.OaSignPackageMapper;

/** Enqueues frozen-policy reminders without performing remote delivery. */
@Service
@ConditionalOnProperty(prefix = "oa.sign.reminder", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class OaSignReminderScheduler
{
    private static final Logger log = LoggerFactory.getLogger(OaSignReminderScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final OaSignPackageMapper packageMapper;
    private final OaSignNotificationOutboxService outboxService;
    private final Clock clock;

    @Autowired
    public OaSignReminderScheduler(OaSignPackageMapper packageMapper,
            OaSignNotificationOutboxService outboxService)
    {
        this(packageMapper, outboxService, Clock.systemUTC());
    }

    OaSignReminderScheduler(OaSignPackageMapper packageMapper,
            OaSignNotificationOutboxService outboxService, Clock clock)
    {
        this.packageMapper = packageMapper;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${oa.sign.reminder.fixed-delay-ms:60000}")
    public void enqueueDueReminders()
    {
        Date scanTime = Date.from(clock.instant());
        List<OaSignReminderCandidate> candidates =
                packageMapper.selectReminderCandidates(scanTime, BATCH_SIZE);
        if (candidates == null)
        {
            return;
        }
        for (OaSignReminderCandidate candidate : candidates)
        {
            try
            {
                outboxService.enqueueReminder(candidate);
            }
            catch (RuntimeException exception)
            {
                log.warn("签约提醒入队失败，packageId={}", candidate.getPackageId(), exception);
            }
        }
    }
}
