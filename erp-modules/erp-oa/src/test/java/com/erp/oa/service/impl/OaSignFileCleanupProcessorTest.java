package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.domain.OaSignFileCleanup;

@DisplayName("签约文件清理执行与重试")
class OaSignFileCleanupProcessorTest
{
    private static final Instant NOW = Instant.parse("2026-07-20T02:00:00Z");

    private OaSignFileCleanupService cleanupService;
    private OaSignFileCleanupProcessor processor;

    @BeforeEach
    void setUp()
    {
        cleanupService = mock(OaSignFileCleanupService.class);
        processor = new OaSignFileCleanupProcessor(cleanupService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("首次即时清理成功后完成台账")
    void shouldCompleteClaimedCleanup()
    {
        OaSignFileCleanup cleanup = cleanup(700L);
        when(cleanupService.selectById(700L)).thenReturn(cleanup);
        when(cleanupService.claim(eq(cleanup), any(Date.class), anyString(), any(Date.class)))
                .thenReturn(true);

        assertThat(processor.processById(700L)).isTrue();

        verify(cleanupService).deleteManagedFiles(cleanup);
        verify(cleanupService).markCompleted(cleanup);
        verify(cleanupService, never()).markRetry(any(), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("文件系统失败时保留台账并以退避时间重试")
    void shouldPersistRetryAfterStorageFailure()
    {
        OaSignFileCleanup cleanup = cleanup(700L);
        when(cleanupService.claim(eq(cleanup), any(Date.class), anyString(), any(Date.class)))
                .thenReturn(true);
        doThrow(new ServiceException("磁盘暂时不可用")).when(cleanupService).deleteManagedFiles(cleanup);

        assertThatThrownBy(() -> processor.process(cleanup, NOW))
                .isInstanceOf(ServiceException.class);

        ArgumentCaptor<Date> retryTime = ArgumentCaptor.forClass(Date.class);
        verify(cleanupService).markRetry(eq(cleanup), eq(1), retryTime.capture(),
                eq("ServiceException"));
        assertThat(retryTime.getValue()).isEqualTo(Date.from(NOW.plusSeconds(60L)));
        verify(cleanupService, never()).markCompleted(any());
    }

    @Test
    @DisplayName("多次失败后仅封顶退避时间而不转为永久失败")
    void shouldKeepRetryingIndefinitelyAtTheCappedDelay()
    {
        OaSignFileCleanup cleanup = cleanup(700L);
        cleanup.setRetryCount(9);
        when(cleanupService.claim(eq(cleanup), any(Date.class), anyString(), any(Date.class)))
                .thenReturn(true);
        doThrow(new ServiceException("磁盘暂时不可用")).when(cleanupService).deleteManagedFiles(cleanup);

        assertThatThrownBy(() -> processor.process(cleanup, NOW))
                .isInstanceOf(ServiceException.class);

        verify(cleanupService).markRetry(cleanup, 10,
                Date.from(NOW.plusSeconds(24L * 60L * 60L)), "ServiceException");
    }

    @Test
    @DisplayName("定时任务中一条失败不阻断后续台账")
    void shouldContinueTheBatchAfterOneFailure()
    {
        OaSignFileCleanup first = cleanup(700L);
        OaSignFileCleanup second = cleanup(701L);
        when(cleanupService.selectDue(any(), eq(100))).thenReturn(List.of(first, second));
        when(cleanupService.claim(any(), any(), anyString(), any())).thenReturn(true);
        doThrow(new ServiceException("磁盘暂时不可用")).when(cleanupService).deleteManagedFiles(first);

        processor.processDue();

        verify(cleanupService).markRetry(eq(first), eq(1), any(), eq("ServiceException"));
        verify(cleanupService).deleteManagedFiles(second);
        verify(cleanupService).markCompleted(second);
    }

    @Test
    @DisplayName("后台抢先获得租约时即时删除不误报失败")
    void shouldAcceptCleanupClaimedByAnotherWorker()
    {
        OaSignFileCleanup initial = cleanup(700L);
        OaSignFileCleanup processing = cleanup(700L);
        processing.setStatus("PROCESSING");
        when(cleanupService.selectById(700L)).thenReturn(initial, processing);
        when(cleanupService.claim(eq(initial), any(Date.class), anyString(), any(Date.class)))
                .thenReturn(false);

        assertThat(processor.processById(700L)).isTrue();

        verify(cleanupService, never()).deleteManagedFiles(any());
        verify(cleanupService, never()).markCompleted(any());
    }

    private static OaSignFileCleanup cleanup(Long cleanupId)
    {
        OaSignFileCleanup cleanup = new OaSignFileCleanup();
        cleanup.setCleanupId(cleanupId);
        cleanup.setTaskId(9L);
        cleanup.setPackageId(90L);
        cleanup.setFileReferencesJson("[]");
        cleanup.setStatus("PENDING");
        cleanup.setRetryCount(0);
        cleanup.setVersion(0L);
        return cleanup;
    }
}
