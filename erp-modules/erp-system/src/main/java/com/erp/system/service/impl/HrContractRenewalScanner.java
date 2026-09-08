package com.erp.system.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.system.mapper.SysUserProfileMapper;
import com.erp.system.service.IHrLifecycleService;
import com.erp.system.service.ISysConfigService;

/** 每日发现临近到期合同并追加续签决策动作。 */
@Component
public class HrContractRenewalScanner
{
    static final String DECISION_DAYS_KEY = "sign.renewal.decision-days";
    private static final Logger log = LoggerFactory.getLogger(HrContractRenewalScanner.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_DECISION_DAYS = 30;
    private static final int BATCH_SIZE = 200;

    private final ISysConfigService configService;
    private final SysUserProfileMapper profileMapper;
    private final IHrLifecycleService lifecycleService;
    private final Clock clock;

    @Autowired
    public HrContractRenewalScanner(ISysConfigService configService,
            SysUserProfileMapper profileMapper, IHrLifecycleService lifecycleService)
    {
        this(configService, profileMapper, lifecycleService, Clock.system(SHANGHAI));
    }

    HrContractRenewalScanner(ISysConfigService configService,
            SysUserProfileMapper profileMapper, IHrLifecycleService lifecycleService, Clock clock)
    {
        this.configService = configService;
        this.profileMapper = profileMapper;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Shanghai")
    public void scanRenewalDecisions()
    {
        LocalDate windowStart = LocalDate.now(clock.withZone(SHANGHAI));
        LocalDate windowEnd = windowStart.plusDays(decisionDays());
        long afterUserId = 0L;
        while (true)
        {
            List<Long> employeeIds = profileMapper.selectRenewalCandidateUserIds(
                    windowStart, windowEnd, afterUserId, BATCH_SIZE);
            if (employeeIds == null || employeeIds.isEmpty())
            {
                return;
            }
            for (Long employeeId : employeeIds)
            {
                if (employeeId == null || employeeId <= 0)
                {
                    continue;
                }
                try
                {
                    lifecycleService.createRenewalDecision(employeeId, windowStart, windowEnd);
                }
                catch (RuntimeException exception)
                {
                    log.error("创建员工续签决策失败, employeeId={}", employeeId, exception);
                }
            }
            afterUserId = employeeIds.get(employeeIds.size() - 1);
            if (employeeIds.size() < BATCH_SIZE)
            {
                return;
            }
        }
    }

    private int decisionDays()
    {
        String configured;
        try
        {
            configured = configService.selectConfigByKey(DECISION_DAYS_KEY);
        }
        catch (RuntimeException exception)
        {
            log.error("续签决策天数配置读取失败，使用默认值{}, key={}, error={}",
                    DEFAULT_DECISION_DAYS, DECISION_DAYS_KEY, exception.getMessage());
            return DEFAULT_DECISION_DAYS;
        }
        try
        {
            int days = Integer.parseInt(configured == null ? "" : configured.trim());
            if (days >= 0)
            {
                return days;
            }
        }
        catch (NumberFormatException ignored)
        {
            // Fall through to the safe default and record the bad operational setting.
        }
        log.warn("续签决策天数配置非法，使用默认值{}, key={}, value={}",
                DEFAULT_DECISION_DAYS, DECISION_DAYS_KEY, configured);
        return DEFAULT_DECISION_DAYS;
    }
}
