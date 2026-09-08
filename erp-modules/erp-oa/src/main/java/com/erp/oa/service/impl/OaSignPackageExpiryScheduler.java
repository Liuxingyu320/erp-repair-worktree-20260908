package com.erp.oa.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.erp.oa.domain.OaSignPackage;

/** Runs small independent expiry transactions and keeps processing after one bad row. */
@Service
@ConditionalOnProperty(prefix = "oa.sign.expiry", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class OaSignPackageExpiryScheduler
{
    private static final Logger log = LoggerFactory.getLogger(OaSignPackageExpiryScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final OaSignPackageLifecycleService lifecycleService;

    public OaSignPackageExpiryScheduler(OaSignPackageLifecycleService lifecycleService)
    {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${oa.sign.expiry.fixed-delay-ms:60000}")
    public void expireDue()
    {
        List<OaSignPackage> candidates = lifecycleService.selectExpiredCandidates(BATCH_SIZE);
        for (OaSignPackage candidate : candidates)
        {
            try
            {
                lifecycleService.expireOne(candidate);
            }
            catch (RuntimeException exception)
            {
                log.warn("签约包过期处理失败，packageId={}", candidate.getPackageId(), exception);
            }
        }
    }
}
