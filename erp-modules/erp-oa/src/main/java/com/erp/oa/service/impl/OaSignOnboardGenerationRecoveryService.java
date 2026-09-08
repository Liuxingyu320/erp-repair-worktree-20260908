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
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignOnboardImportBatch;
import com.erp.oa.domain.OaSignOnboardImportRow;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;

/**
 * Repairs generation leases outside user-facing reads. Batch and row compare-and-set operations
 * remain the fencing boundary, so multiple application instances may run the scheduler safely.
 */
@Service
@ConditionalOnProperty(prefix = "oa.sign.excel-import", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OaSignOnboardGenerationRecoveryService
{
    private static final Logger log = LoggerFactory.getLogger(
            OaSignOnboardGenerationRecoveryService.class);
    private static final int BATCH_SIZE = 100;
    private static final long GENERATION_LEASE_MILLIS = 5L * 60L * 1000L;

    private final OaSignOnboardImportService importService;
    private final OaSignOnboardGenerationService generationService;
    private final OaSignOnboardImportBatchMapper batchMapper;
    private final OaSignOnboardImportRowMapper rowMapper;
    private final Clock clock;

    @Autowired
    public OaSignOnboardGenerationRecoveryService(
            OaSignOnboardImportService importService,
            OaSignOnboardGenerationService generationService,
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper)
    {
        this(importService, generationService, batchMapper, rowMapper,
                Clock.systemUTC());
    }

    OaSignOnboardGenerationRecoveryService(
            OaSignOnboardImportService importService,
            OaSignOnboardGenerationService generationService,
            OaSignOnboardImportBatchMapper batchMapper,
            OaSignOnboardImportRowMapper rowMapper,
            Clock clock)
    {
        this.importService = importService;
        this.generationService = generationService;
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString =
            "${oa.sign.onboard-generation-recovery.fixed-delay-ms:60000}")
    public void recoverStaleGenerations()
    {
        Date staleBefore = new Date(clock.millis() - GENERATION_LEASE_MILLIS);
        List<Long> batchIds =
                batchMapper.selectStaleGeneratingBatchIds(staleBefore, BATCH_SIZE);
        if (batchIds == null) return;
        for (Long batchId : batchIds)
        {
            try
            {
                recoverOne(batchId, staleBefore);
            }
            catch (RuntimeException exception)
            {
                log.warn("入职签约生成租约恢复失败，batchId={}", batchId, exception);
            }
        }
    }

    boolean recoverOne(Long batchId, Date staleBefore)
    {
        OaSignOnboardImportBatch batch = batchMapper.selectById(batchId);
        if (!isStaleGenerationBatch(batch, staleBefore)) return false;
        List<OaSignOnboardImportRow> preflightRows = rowMapper.selectByBatchId(batchId);
        if (preflightRows == null) return false;
        try
        {
            for (OaSignOnboardImportRow row : preflightRows)
            {
                if (row != null && "GENERATING".equals(row.getStatus()))
                    importService.requireValidTaskBinding(row);
            }
        }
        catch (ServiceException bindingChanged)
        {
            // Never claim a mixed or corrupt batch merely because one stale row selected its id.
            return false;
        }
        if (batchMapper.claimGeneration(batchId, batch.getVersion(), staleBefore) != 1)
            return false;
        boolean allClaimsResolved = false;
        try
        {
            OaSignOnboardImportBatch claimed = batchMapper.selectById(batchId);
            if (claimed == null) claimed = batch;
            List<OaSignOnboardImportRow> rows = rowMapper.selectByBatchId(batchId);
            if (rows == null) return true;
            allClaimsResolved = true;
            for (OaSignOnboardImportRow row : rows)
            {
                if (row == null || !"GENERATING".equals(row.getStatus())) continue;
                try
                {
                    importService.requireValidTaskBinding(row);
                }
                catch (ServiceException bindingChanged)
                {
                    allClaimsResolved = false;
                    continue;
                }
                if (row.getUpdateTime() == null || row.getUpdateTime().after(staleBefore))
                {
                    allClaimsResolved = false;
                    continue;
                }
                OaSignOnboardImportRow recovered = generationService
                        .recoverInterruptedGeneration(claimed, row, staleBefore);
                if (recovered == null || "GENERATING".equals(recovered.getStatus()))
                    allClaimsResolved = false;
            }
        }
        finally
        {
            // A recovery exception or live row keeps the fenced lease for the next retry window.
            if (allClaimsResolved) importService.refreshSummary(batchId);
        }
        return true;
    }

    private boolean isStaleGenerationBatch(OaSignOnboardImportBatch batch,
            Date staleBefore)
    {
        return batch != null && "GENERATING".equals(batch.getStatus())
                && batch.getVersion() != null && batch.getUpdateTime() != null
                && staleBefore != null && !batch.getUpdateTime().after(staleBefore);
    }
}
