package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import com.erp.file.drive.config.DriveProperties;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveActor;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.metric.DriveMetrics;
import com.erp.file.drive.storage.DriveStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("云盘上传容量预占恢复")
class DriveUploadReservationServiceTest
{
    private DriveProperties properties;
    private DriveUploadReservationPersistence persistence;
    private DriveCapacityService capacityService;
    private DriveStorageProvider storage;
    private DriveMetrics metrics;
    private DriveUploadReservationService service;

    @BeforeEach
    void setUp()
    {
        properties = new DriveProperties();
        properties.setEnabled(true);
        properties.setCapacityReservationEnabled(true);
        properties.setUploadReservationTimeoutMinutes(10);
        persistence = mock(DriveUploadReservationPersistence.class);
        capacityService = mock(DriveCapacityService.class);
        storage = mock(DriveStorageProvider.class);
        metrics = mock(DriveMetrics.class);
        service = new DriveUploadReservationService(
                properties, persistence, capacityService, storage, metrics);
    }

    @Test
    @DisplayName("开关开启时在对象存储前建立可过期预占")
    void shouldCreateDurableReservationBeforeStorage()
    {
        long before = System.currentTimeMillis();

        String id = service.reserveBeforeStorage(4L,
                "2026/07/object.pdf", 12L, actor());

        ArgumentCaptor<DriveUploadReservation> captured =
                ArgumentCaptor.forClass(DriveUploadReservation.class);
        verify(persistence).create(captured.capture());
        DriveUploadReservation value = captured.getValue();
        assertThat(id).isEqualTo(value.getReservationId()).isNotBlank();
        assertThat(value.getStatus()).isEqualTo(DriveConstants.RESERVATION_RESERVED);
        assertThat(value.getReservedBytes()).isEqualTo(12L);
        assertThat(value.getExpireTime().getTime()).isBetween(
                before + 599_000L, before + 601_000L);
    }

    @Test
    @DisplayName("开关关闭时保持旧链路兼容且不写预占")
    void shouldRemainCompatibleWhileDisabled()
    {
        properties.setCapacityReservationEnabled(false);

        assertThat(service.reserveBeforeStorage(4L,
                "2026/07/object.pdf", 12L, actor())).isNull();

        verify(capacityService).requireLegacyUploadAllowedWithoutReservation();
        verifyNoInteractions(persistence, storage, metrics);
    }

    @Test
    @DisplayName("过期预占先认领，在事务外删对象，再释放容量")
    void shouldCleanupExpiredReservationIdempotently() throws Exception
    {
        DriveUploadReservation candidate = reservation(
                DriveConstants.RESERVATION_RESERVED, 0);
        DriveUploadReservation claimed = reservation(
                DriveConstants.RESERVATION_CLEANING, 1);
        when(persistence.selectCleanupCandidates(any(Date.class),
                any(Date.class), eq(100))).thenReturn(List.of(candidate));
        when(persistence.claimCleanup(eq(candidate), any(Date.class),
                any(Date.class), eq("system"))).thenReturn(claimed);
        when(storage.exists(claimed.getStorageKey())).thenReturn(true, false);

        service.cleanupExpired();

        verify(storage).delete(claimed.getStorageKey());
        verify(persistence).completeCleanup("r-1");
        verify(persistence, never()).markCleanupFailed(
                eq("r-1"), any(), any());
    }

    @Test
    @DisplayName("删除对象失败时预占继续计入容量并记录低基数指标")
    void shouldKeepReservationWhenObjectCleanupFails() throws Exception
    {
        DriveUploadReservation candidate = reservation(
                DriveConstants.RESERVATION_RESERVED, 0);
        DriveUploadReservation claimed = reservation(
                DriveConstants.RESERVATION_CLEANING, 1);
        when(persistence.selectCleanupCandidates(any(Date.class),
                any(Date.class), eq(100))).thenReturn(List.of(candidate));
        when(persistence.claimCleanup(eq(candidate), any(Date.class),
                any(Date.class), eq("system"))).thenReturn(claimed);
        when(storage.exists(claimed.getStorageKey())).thenReturn(true);
        doThrow(new IOException("unavailable"))
                .when(storage).delete(claimed.getStorageKey());

        service.cleanupExpired();

        verify(persistence).markCleanupFailed("r-1",
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "system");
        verify(persistence, never()).completeCleanup("r-1");
        verify(metrics).recordCleanupFailure("upload_reservation");
    }

    @Test
    @DisplayName("外层确认对象不存在时结算预占")
    void shouldSettleOnlyAfterConfirmedAbsence()
    {
        service.settleAfterCompensation("r-1", true, actor());
        service.settleAfterCompensation("r-2", false, actor());

        verify(persistence).releaseConfirmedAbsent("r-1");
        verify(persistence).markCleanupFailed("r-2",
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "alice");
    }

    private static DriveUploadReservation reservation(String status, int version)
    {
        DriveUploadReservation value = new DriveUploadReservation();
        value.setReservationId("r-1");
        value.setSpaceId(4L);
        value.setStorageKey("2026/07/object.pdf");
        value.setReservedBytes(12L);
        value.setStatus(status);
        value.setExpireTime(new Date(0L));
        value.setRetryCount(0);
        value.setVersion(version);
        return value;
    }

    private static DriveActor actor()
    {
        return new DriveActor(20L, 8L, "财务部", "alice",
                Set.of(DriveConstants.PERMISSION_ACCESS), false);
    }
}
