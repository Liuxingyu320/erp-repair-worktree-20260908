package com.erp.file.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import com.erp.file.drive.constant.DriveConstants;
import com.erp.file.drive.constant.DriveErrorCodes;
import com.erp.file.drive.domain.DriveUploadReservation;
import com.erp.file.drive.mapper.DriveUploadReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

@DisplayName("云盘上传容量预占事务")
class DriveUploadReservationPersistenceTest
{
    private DriveUploadReservationMapper mapper;
    private DriveCapacityService capacityService;
    private DriveUploadReservationPersistence persistence;

    @BeforeEach
    void setUp()
    {
        mapper = mock(DriveUploadReservationMapper.class);
        capacityService = mock(DriveCapacityService.class);
        persistence = new DriveUploadReservationPersistence(mapper, capacityService);
    }

    @Test
    @DisplayName("预占必须先锁全局容量再写账本")
    void shouldLockCapacityBeforeCreatingReservation()
    {
        DriveUploadReservation reservation = reservation(
                DriveConstants.RESERVATION_RESERVED, 0);
        when(mapper.insertReservation(reservation)).thenReturn(1);

        assertThat(persistence.create(reservation)).isSameAs(reservation);

        InOrder order = inOrder(capacityService, mapper);
        order.verify(capacityService).requireUploadCapacityAndLock(12L);
        order.verify(mapper).insertReservation(reservation);
    }

    @Test
    @DisplayName("预占唯一冲突返回稳定并发错误")
    void shouldTranslateDuplicateReservation()
    {
        DriveUploadReservation reservation = reservation(
                DriveConstants.RESERVATION_RESERVED, 0);
        when(mapper.insertReservation(reservation))
                .thenThrow(new DuplicateKeyException("storage_key"));

        assertThatThrownBy(() -> persistence.create(reservation))
                .extracting("businessCode")
                .isEqualTo(DriveErrorCodes.DRIVE_CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("只有确认对象不存在后才在容量锁内释放预占")
    void shouldReleaseConfirmedAbsentUnderCapacityLock()
    {
        DriveUploadReservation reservation = reservation(
                DriveConstants.RESERVATION_CLEANUP_FAILED, 3);
        when(mapper.selectForUpdate("r-1")).thenReturn(reservation);
        when(mapper.deleteExpected("r-1", 3,
                DriveConstants.RESERVATION_CLEANUP_FAILED)).thenReturn(1);

        persistence.releaseConfirmedAbsent("r-1");

        InOrder order = inOrder(capacityService, mapper);
        order.verify(capacityService).lockForAllocationChange();
        order.verify(mapper).selectForUpdate("r-1");
        order.verify(mapper).deleteExpected("r-1", 3,
                DriveConstants.RESERVATION_CLEANUP_FAILED);
    }

    @Test
    @DisplayName("清理失败保留预占并设置有上限的重试时间")
    void shouldKeepFailedCleanupAccounted()
    {
        DriveUploadReservation reservation = reservation(
                DriveConstants.RESERVATION_CLEANING, 2);
        reservation.setRetryCount(1);
        when(mapper.selectForUpdate("r-1")).thenReturn(reservation);
        when(mapper.markCleanupFailed(eq("r-1"), eq(2), eq(2),
                any(Date.class), eq(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE),
                eq("system"))).thenReturn(1);

        long before = System.currentTimeMillis();
        persistence.markCleanupFailed("r-1",
                DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE, "system");

        ArgumentCaptor<Date> retry = ArgumentCaptor.forClass(Date.class);
        verify(mapper).markCleanupFailed(eq("r-1"), eq(2), eq(2), retry.capture(),
                eq(DriveErrorCodes.DRIVE_STORAGE_UNAVAILABLE), eq("system"));
        assertThat(retry.getValue().getTime()).isBetween(
                before + 119_000L, before + 121_000L);
    }

    private static DriveUploadReservation reservation(String status, int version)
    {
        DriveUploadReservation value = new DriveUploadReservation();
        value.setReservationId("r-1");
        value.setSpaceId(4L);
        value.setStorageKey("2026/07/object.pdf");
        value.setReservedBytes(12L);
        value.setStatus(status);
        value.setExpireTime(new Date());
        value.setRetryCount(0);
        value.setVersion(version);
        return value;
    }
}
