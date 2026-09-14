package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.erp.system.api.domain.UserNotificationCommand;
import com.erp.system.domain.vo.HrHealthCertificateVo;
import com.erp.system.mapper.HrHealthCertificateMapper;
import com.erp.system.service.ISysConfigService;
import com.erp.system.service.ISysUserNotificationService;

class HrHealthCertificateReminderScannerTest
{
    private static final LocalDate TODAY=LocalDate.of(2026,7,13);
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"),ZoneId.of("Asia/Shanghai"));

    @Test
    void remindsAtThirtyFifteenAndSevenDaysAndDeduplicatesRecipients()
    {
        HrHealthCertificateMapper mapper=mock(HrHealthCertificateMapper.class);
        ISysConfigService config=mock(ISysConfigService.class);
        ISysUserNotificationService notifications=mock(ISysUserNotificationService.class);
        when(config.selectConfigByKey(HrHealthCertificateReminderScanner.WARNING_DAYS_KEY)).thenReturn("30,15,7");
        when(mapper.selectReminderCandidates(TODAY,TODAY.plusDays(30),0L,200)).thenReturn(List.of(
                certificate(1L,7L,10L,TODAY.plusDays(30)),
                certificate(2L,7L,10L,TODAY.plusDays(15)),
                certificate(3L,7L,10L,TODAY.plusDays(7)),
                certificate(4L,7L,10L,TODAY.plusDays(10))));
        when(mapper.selectReminderRecipientUserIds(10L)).thenReturn(List.of(7L,8L,8L));
        HrHealthCertificateReminderScanner scanner=new HrHealthCertificateReminderScanner(
                mapper,config,notifications,CLOCK);

        scanner.scan();

        ArgumentCaptor<UserNotificationCommand> commands=ArgumentCaptor.forClass(UserNotificationCommand.class);
        verify(notifications,times(12)).publish(commands.capture());
        assertThat(commands.getAllValues()).extracting(UserNotificationCommand::getRecipientUserId)
                .containsOnly(7L,8L);
        assertThat(commands.getAllValues()).extracting(command -> command.getRecipientUserId()
                        + ":" + command.getBusinessKey())
                .doesNotHaveDuplicates();
        assertThat(commands.getAllValues().stream().filter(command -> Long.valueOf(7L)
                .equals(command.getRecipientUserId())).count()).isEqualTo(6L);
        assertThat(commands.getAllValues().stream().filter(command -> Long.valueOf(8L)
                .equals(command.getRecipientUserId())).count()).isEqualTo(6L);
        verify(config,never()).selectConfigByKey(HrHealthCertificateFeatureService.FEATURE_KEY);
    }

    @Test
    void invalidThresholdFallsBackAndOneRowFailureDoesNotStopTheBatch()
    {
        HrHealthCertificateMapper mapper=mock(HrHealthCertificateMapper.class);
        ISysConfigService config=mock(ISysConfigService.class);
        ISysUserNotificationService notifications=mock(ISysUserNotificationService.class);
        when(config.selectConfigByKey(HrHealthCertificateReminderScanner.WARNING_DAYS_KEY)).thenReturn("invalid");
        when(mapper.selectReminderCandidates(TODAY,TODAY.plusDays(30),0L,200)).thenReturn(List.of(
                certificate(1L,7L,10L,TODAY.plusDays(30)),
                certificate(2L,9L,11L,TODAY.plusDays(15))));
        when(mapper.selectReminderRecipientUserIds(any())).thenReturn(List.of());
        when(notifications.publish(any())).thenThrow(new IllegalStateException("single failure"))
                .thenReturn(null);
        HrHealthCertificateReminderScanner scanner=new HrHealthCertificateReminderScanner(
                mapper,config,notifications,CLOCK);

        scanner.scan();

        verify(notifications,atLeast(2)).publish(any());
        verify(mapper).selectReminderRecipientUserIds(eq(11L));
    }

    private static HrHealthCertificateVo certificate(Long id,Long userId,Long deptId,LocalDate expires)
    {
        HrHealthCertificateVo row=new HrHealthCertificateVo();row.setCertificateId(id);row.setUserId(userId);
        row.setCurrentDeptId(deptId);row.setExpiresOn(expires);row.setEmployeeName("员工"+userId);return row;
    }
}
