package com.erp.system.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.erp.system.service.ISysNoticeService;

/**
 * 公告计划发布与过期下线扫描。每个公告由服务代理开启独立事务并行锁抢占。
 */
@Component
public class SysNoticePublishScheduler
{
    private static final Logger log = LoggerFactory.getLogger(SysNoticePublishScheduler.class);
    private final ISysNoticeService noticeService;

    public SysNoticePublishScheduler(ISysNoticeService noticeService)
    {
        this.noticeService = noticeService;
    }

    @Scheduled(fixedDelayString = "${system.notice.scheduler-delay-ms:60000}",
            initialDelayString = "${system.notice.scheduler-initial-delay-ms:60000}")
    public void scan()
    {
        noticeService.offlineExpiredNotices();
        for (Long noticeId : noticeService.selectDueScheduledNoticeIds(100))
        {
            try
            {
                noticeService.publishScheduledNotice(noticeId);
            }
            catch (RuntimeException ex)
            {
                // 不记录公告内容或受众明细；保持 SCHEDULED，下一轮重试。
                log.warn("scheduled notice publish failed: noticeId={}, errorType={}",
                        noticeId, ex.getClass().getSimpleName());
            }
        }
    }
}
