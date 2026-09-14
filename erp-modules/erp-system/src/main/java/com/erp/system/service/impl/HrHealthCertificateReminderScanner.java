package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateMapper;
import com.erp.system.metric.HrBusinessMetrics;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserNotificationService;

/** 每日按 30/15/7 天节点提醒员工、当前门店负责人和人事。 */
@Component
public class HrHealthCertificateReminderScanner
{
    static final String WARNING_DAYS_KEY = "todo.health-certificate.warning-days";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(
            HrHealthCertificateReminderScanner.class);
    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(30, 15, 7);
    private static final int BATCH_SIZE = 200;

    private final HrHealthCertificateMapper mapper;
    private final ISysConfigService configService;
    private final ISysUserNotificationService notificationService;
    private final Clock clock;
    private final HrBusinessMetrics metrics;

    @Autowired
    public HrHealthCertificateReminderScanner(
            HrHealthCertificateMapper mapper,
            ISysConfigService configService,
            ISysUserNotificationService notificationService,
            HrBusinessMetrics metrics)
    {
        this(mapper, configService, notificationService,
                Clock.system(SHANGHAI), metrics);
    }

    HrHealthCertificateReminderScanner(HrHealthCertificateMapper mapper,
            ISysConfigService configService,
            ISysUserNotificationService notificationService, Clock clock)
    {
        this(mapper, configService, notificationService, clock, null);
    }

    HrHealthCertificateReminderScanner(HrHealthCertificateMapper mapper,
            ISysConfigService configService,
            ISysUserNotificationService notificationService, Clock clock,
            HrBusinessMetrics metrics)
    {
        this.mapper = mapper;
        this.configService = configService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Scheduled(cron = "0 35 2 * * *", zone = "Asia/Shanghai")
    public void scan()
    {
        log.debug("健康证到期提醒扫描仅处理已审核当前证件，不受新受理开关影响");
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI));
        List<Integer> thresholds = thresholds();
        int maxDays = thresholds.stream().mapToInt(Integer::intValue)
                .max().orElse(30);
        long afterCertificateId = 0L;
        while (true)
        {
            List<HrHealthCertificateVo> rows = mapper.selectReminderCandidates(
                    today, today.plusDays(maxDays), afterCertificateId, BATCH_SIZE);
            if (rows == null || rows.isEmpty()) return;
            for (HrHealthCertificateVo row : rows)
            {
                afterCertificateId = Math.max(afterCertificateId,
                        row.getCertificateId());
                long days = ChronoUnit.DAYS.between(today, row.getExpiresOn());
                if (!thresholds.contains((int) days)) continue;
                try
                {
                    notifyRecipients(row, (int) days);
                }
                catch (RuntimeException failure)
                {
                    log.error("健康证到期提醒失败，certificateId={}, threshold={}, type={}",
                            row.getCertificateId(), days,
                            failure.getClass().getSimpleName());
                }
            }
            if (rows.size() < BATCH_SIZE) return;
        }
    }

    private void notifyRecipients(HrHealthCertificateVo row, int threshold)
    {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(row.getUserId());
        List<Long> managers = mapper.selectReminderRecipientUserIds(
                row.getCurrentDeptId());
        if (managers != null) recipients.addAll(managers);
        for (Long recipient : recipients)
        {
            if (recipient == null || recipient <= 0) continue;
            publish(row, threshold, recipient, "IN_APP");
            publish(row, threshold, recipient, "MOBILE_PUSH");
        }
    }

    private void publish(HrHealthCertificateVo row, int threshold,
            Long recipient, String channel)
    {
        UserNotificationCommand command = new UserNotificationCommand();
        command.setChannel(channel);
        command.setRecipientUserId(recipient);
        command.setBusinessKey("HR_HEALTH_CERT:" + row.getCertificateId()
                + ":" + row.getExpiresOn() + ":" + threshold + ":"
                + channel);
        command.setTitle("员工健康证即将到期");
        command.setBody((row.getEmployeeName() == null ? "员工"
                : row.getEmployeeName()) + "的健康证将在" + threshold
                + "天后到期（" + row.getExpiresOn() + "）");
        command.setRouteType("HR_HEALTH_CERT_DUE");
        command.setRouteParams("{\"userId\":" + row.getUserId()
                + ",\"certificateId\":" + row.getCertificateId() + "}");
        try
        {
            notificationService.publish(command);
            recordReminder("success");
        }
        catch (RuntimeException failure)
        {
            recordReminder("failure");
            throw failure;
        }
    }

    private void recordReminder(String outcome)
    {
        if (metrics != null)
        {
            metrics.recordHealthCertificateReminder(outcome);
        }
    }

    private List<Integer> thresholds()
    {
        try
        {
            String configured = configService.selectConfigByKey(
                    WARNING_DAYS_KEY);
            List<Integer> parsed = Arrays.stream(configured.split(","))
                    .map(String::trim).map(Integer::parseInt)
                    .filter(value -> value >= 0 && value <= 365)
                    .distinct().sorted(java.util.Comparator.reverseOrder())
                    .toList();
            return parsed.isEmpty() ? DEFAULT_THRESHOLDS : parsed;
        }
        catch (Exception ignored)
        {
            return DEFAULT_THRESHOLDS;
        }
    }
}
