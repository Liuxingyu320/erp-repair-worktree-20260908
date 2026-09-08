package com.erp.oa.service.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.erp.oa.mapper.OaSignOnboardImportBatchMapper;
import com.erp.oa.mapper.OaSignOnboardImportRowMapper;

class OaSignOnboardGenerationRecoveryServiceTest
{
    @Test
    void scheduledRecoveryUsesLeaseCutoffAndProcessesEveryCandidate()
    {
        OaSignOnboardImportBatchMapper batchMapper =
                mock(OaSignOnboardImportBatchMapper.class);
        Clock clock = Clock.fixed(Instant.ofEpochMilli(600_000L), ZoneOffset.UTC);
        Date staleBefore = new Date(300_000L);
        when(batchMapper.selectStaleGeneratingBatchIds(staleBefore, 100))
                .thenReturn(List.of(31L, 32L));
        OaSignOnboardGenerationRecoveryService service =
                new OaSignOnboardGenerationRecoveryService(
                        mock(OaSignOnboardImportService.class),
                        mock(OaSignOnboardGenerationService.class),
                        batchMapper, mock(OaSignOnboardImportRowMapper.class), clock);

        service.recoverStaleGenerations();

        verify(batchMapper).selectStaleGeneratingBatchIds(staleBefore, 100);
        verify(batchMapper).selectById(31L);
        verify(batchMapper).selectById(32L);
    }
}
