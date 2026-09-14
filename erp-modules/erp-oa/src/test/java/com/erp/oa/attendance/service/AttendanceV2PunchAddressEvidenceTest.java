package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.domain.AttendanceModels.Challenge;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.Evidence;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand;
import com.erp.oa.attendance.dto.AttendanceViews.PunchResult;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.service.AttendanceEvidenceStorageService.StoredEvidence;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceAddressResolver.ResolvedAddress;
import com.erp.oa.attendance.support.AttendanceChallengePolicy;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer.Coordinate;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Bounds;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator.Evaluation;
import com.erp.oa.attendance.support.AttendanceGeoFence;
import com.erp.oa.attendance.support.AttendanceLocationAuditPolicy;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceV2PunchAddressEvidenceTest
{
    private static final LocalDate DAY = LocalDate.of(2026, 8, 20);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20,
            9, 5);
    private static final String TOKEN = "a".repeat(64);

    private AttendanceV2Mapper mapper;
    private AttendanceAddressResolver addressResolver;
    private AttendanceEvidenceStorageService evidenceStorage;
    private AttendanceDaySettlementCalculator settlement;
    private AttendanceV2Service service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("张三");
        TransactionSynchronizationManager.initSynchronization();
        mapper = mock(AttendanceV2Mapper.class);
        addressResolver = mock(AttendanceAddressResolver.class);
        evidenceStorage = mock(AttendanceEvidenceStorageService.class);
        AttendanceV2Properties properties = new AttendanceV2Properties();
        AttendanceRuleEngine rules = new AttendanceRuleEngine();
        settlement = mock(AttendanceDaySettlementCalculator.class);
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        when(settlement.bounds(any())).thenReturn(new Bounds(
                DAY.atTime(9, 0), DAY.atTime(18, 0)));
        when(settlement.fullyCoveredByApprovedLeave(any(), any(), any()))
                .thenReturn(false);
        when(mapper.selectScheduleSegmentSnapshots(eq(31L), anyBoolean()))
                .thenReturn(List.of(workSegment()));
        ShopScopeService shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        service = new AttendanceV2Service(mapper, rules,
                new AttendanceLocationAuditPolicy(
                        new AttendanceCoordinateTransformer(), properties),
                new AttendanceCoordinateTransformer(),
                new AttendanceGeoFence(),
                addressResolver, new AttendanceChallengePolicy(), settlement,
                evidenceStorage, properties, mock(AttendanceRuntimePolicy.class),
                shopScope, gate,
                Clock.fixed(Instant.parse("2026-08-20T01:05:00Z"),
                        ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown()
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        SecurityContextHolder.remove();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void storesRawCoordinatesButWatermarksOnlyTheResolvedAddress()
    {
        Challenge challenge = challenge();
        Schedule schedule = schedule();
        DayResult dayResult = new DayResult();
        dayResult.scheduleId = schedule.scheduleId;
        dayResult.scheduledMinutes = 480;
        stubPunchPrelude(challenge, schedule);
        when(mapper.selectShopOrganizationPath(101L)).thenReturn(
                "金英灵韵 / 北京区域 / 北京区域运营 / 北京柏悦");
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
        when(addressResolver.resolve(new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), "WGS84"))
                .thenReturn(new ResolvedAddress(
                        "浙江省杭州市拱墅区华润大厦", "CONTROLLED_TEST"));
        StoredEvidence stored = new StoredEvidence("capture.jpg",
                "2026/08/20/AP-original.jpg",
                "2026/08/20/AP-watermarked.jpg", 10, 20,
                "original-sha", "watermarked-sha", 640, 480, "payload");
        when(evidenceStorage.store(anyString(), eq(DAY), any(), any()))
                .thenReturn(stored);
        when(mapper.insertPunchEvent(any())).thenAnswer(invocation -> {
            PunchEvent event = invocation.getArgument(0);
            event.punchEventId = 88L;
            return 1;
        });
        when(mapper.insertEvidence(any())).thenAnswer(invocation -> {
            Evidence evidence = invocation.getArgument(0);
            evidence.evidenceId = 99L;
            return 1;
        });
        when(mapper.selectDayResultByScheduleId(31L)).thenReturn(dayResult);
        when(settlement.evaluate(any(Schedule.class),
                org.mockito.ArgumentMatchers.<PunchEvent>anyList(), any(),
                any(), any(), any(), any(), any()))
                .thenReturn(new Evaluation(dayResult, List.of()));
        when(mapper.upsertDayResult(any())).thenReturn(1);
        when(mapper.consumeChallenge(41L, 88L, NOW, 0L)).thenReturn(1);
        MultipartFile photo = mock(MultipartFile.class);

        PunchResult result = service.punch(command(), photo, 101L);

        assertThat(result.success).isTrue();
        assertThat(result.event.longitude)
                .isEqualByComparingTo("120.1551000");
        assertThat(result.event.latitude).isEqualByComparingTo("30.2741000");
        assertThat(result.event.distanceMeters).isEqualByComparingTo("0.00");
        assertThat(result.event.geofenceStatus).isEqualTo("INSIDE");
        assertThat(result.event.resolvedAddress)
                .isEqualTo("浙江省杭州市拱墅区华润大厦");
        assertThat(result.event.riskFlags)
                .contains("ADDRESS_RESOLVED:CONTROLLED_TEST");

        ArgumentCaptor<List> watermark = ArgumentCaptor.forClass(List.class);
        verify(evidenceStorage).store(anyString(), eq(DAY), eq(photo),
                watermark.capture());
        List<String> lines = watermark.getValue();
        assertThat(lines).hasSize(6);
        assertThat(lines.subList(0, 5)).containsExactly(
                "员工：张三",
                "门店：金英灵韵 / 北京区域 / 北京区域运营 / 北京柏悦",
                "班次：本地测试班次",
                "时间：2026-08-20 09:05:00  类型：IN",
                "地址：浙江省杭州市拱墅区华润大厦");
        assertThat(lines.get(5)).startsWith("凭证编号：AP");
        String text = String.join("\n", lines);
        assertThat(text).doesNotContain("金英灵韵集团", "ID-101")
                .doesNotContain("120.1551000", "30.2741000", "坐标",
                        "经度", "纬度", "距考勤点");
    }

    @Test
    void rejectsAPunchOutsideThePublishedGeofenceBeforeCreatingEvidence()
    {
        Schedule schedule = schedule();
        stubPunchPrelude(challenge(), schedule);
        PunchCommand command = command();
        command.latitude = new BigDecimal("30.2841000");
        command.longitude = new BigDecimal("120.1651000");
        command.accuracyMeters = BigDecimal.TEN;

        assertThatThrownBy(() -> service.punch(command,
                mock(MultipartFile.class), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("OUTSIDE_ATTENDANCE_GEOFENCE");
        verify(addressResolver, never()).resolve(any(), any(), any());
        verify(evidenceStorage, never()).store(anyString(), any(), any(), any());
    }

    @Test
    void rejectsAccuracyWorseThanThePublishedSiteLimit()
    {
        Schedule schedule = schedule();
        stubPunchPrelude(challenge(), schedule);
        PunchCommand command = command();
        command.accuracyMeters = new BigDecimal("50.01");

        assertThatThrownBy(() -> service.punch(command,
                mock(MultipartFile.class), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("LOCATION_ACCURACY_INSUFFICIENT");
        verify(addressResolver, never()).resolve(any(), any(), any());
        verify(evidenceStorage, never()).store(anyString(), any(), any(), any());
    }

    @Test
    void historicalScheduleWithoutAGeofenceSnapshotFailsClosed()
    {
        Schedule schedule = schedule();
        schedule.siteId = null;
        schedule.siteNameSnapshot = null;
        schedule.addressSnapshot = null;
        schedule.latitudeSnapshot = null;
        schedule.longitudeSnapshot = null;
        schedule.coordinateSystemSnapshot = null;
        schedule.radiusMetersSnapshot = null;
        schedule.maxAccuracyMetersSnapshot = null;
        stubPunchPrelude(challenge(), schedule);

        assertThatThrownBy(() -> service.punch(command(),
                mock(MultipartFile.class), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_GEOFENCE_NOT_CONFIGURED");
        verify(addressResolver, never()).resolve(any(), any(), any());
        verify(evidenceStorage, never()).store(anyString(), any(), any(), any());
    }

    @Test
    void refusesToCreatePartialEvidenceWhenOrganizationPathIsMissing()
    {
        Challenge challenge = challenge();
        Schedule schedule = schedule();
        when(mapper.selectDatabaseNow()).thenReturn(NOW);
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
        when(addressResolver.resolve(new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), "WGS84"))
                .thenReturn(new ResolvedAddress(
                        "浙江省杭州市拱墅区华润大厦", "CONTROLLED_TEST"));

        assertThatThrownBy(() -> service.punch(command(),
                mock(MultipartFile.class), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_SHOP_ORGANIZATION_PATH_UNAVAILABLE");
        verify(evidenceStorage, never()).store(anyString(), eq(DAY), any(),
                any());
    }

    @Test
    void refusesToCreatePartialEvidenceWhenPublishedShiftNameIsMissing()
    {
        Challenge challenge = challenge();
        Schedule schedule = schedule();
        schedule.shiftNameSnapshot = " ";
        when(mapper.selectDatabaseNow()).thenReturn(NOW);
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.selectShopOrganizationPath(101L)).thenReturn(
                "金英灵韵 / 北京区域 / 北京区域运营 / 北京柏悦");
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
        when(addressResolver.resolve(new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), "WGS84"))
                .thenReturn(new ResolvedAddress(
                        "浙江省杭州市拱墅区华润大厦", "CONTROLLED_TEST"));

        assertThatThrownBy(() -> service.punch(command(),
                mock(MultipartFile.class), 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_SHIFT_NAME_UNAVAILABLE");
        verify(evidenceStorage, never()).store(anyString(), eq(DAY), any(),
                any());
    }

    @Test
    void challengeExpiryMustUseDatabaseTimeAfterWaitingForItsRowLock()
    {
        var challenge = challenge();
        var locked = new java.util.concurrent.atomic.AtomicBoolean(false);
        stubPunchPrelude(challenge, schedule());
        when(mapper.selectDatabaseNow()).thenAnswer(inv -> locked.get()
                ? challenge.expiresAt.plusSeconds(1) : NOW);
        when(mapper.selectChallengeByTokenForUpdate(TOKEN)).thenAnswer(inv -> {
            locked.set(true);
            return challenge;
        });
        assertThatThrownBy(() -> service.punch(command(), null, 101L))
                .hasMessage("PUNCH_CHALLENGE_EXPIRED");
        verify(mapper, never()).insertPunchEvent(any());
        verify(evidenceStorage, never()).store(anyString(), any(), any(), any());
    }

    @Test
    void absoluteCaptureTimesAreMappedToTheDatabaseClockAndStillExpire()
    {
        stubPunchPrelude(challenge(), schedule());
        var clock = new com.erp.oa.attendance.domain.AttendanceModels.DatabaseClock();
        clock.localTime = NOW;
        clock.epochMillis = NOW.toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli();
        when(mapper.selectDatabaseClock()).thenReturn(clock);
        for (String offset : List.of("Z", "+09:00", "-07:00"))
        {
            var absolute = java.time.Instant.ofEpochMilli(clock.epochMillis);
            var encoded = absolute.atOffset(java.time.ZoneOffset.of(offset)).toString();
            var command = command();
            command.clientCaptureTime = NOW.minusDays(1);
            command.clientCaptureTimestamp = encoded;
            command.latitude = new BigDecimal("30.2841000");
            command.longitude = new BigDecimal("120.1651000");
            command.accuracyMeters = BigDecimal.TEN;
            assertThatThrownBy(() -> service.punch(command, null, 101L))
                    .hasMessage("OUTSIDE_ATTENDANCE_GEOFENCE");
            assertThat(command.clientCaptureTime).isEqualTo(NOW);
        }
        for (int delta : new int[] { -600, 31 })
        {
            var command = command();
            command.clientCaptureInstant = java.time.Instant.ofEpochMilli(clock.epochMillis).plusSeconds(delta);
            assertThatThrownBy(() -> service.punch(command, null, 101L)).hasMessage("CLIENT_CAPTURE_TIME_STALE");
        }
        var invalid = command(); invalid.clientCaptureTimestamp = "invalid";
        assertThatThrownBy(() -> service.punch(invalid, null, 101L)).hasMessage("CLIENT_CAPTURE_TIME_INVALID");
        var consumed = challenge(); consumed.status = "CONSUMED";
        when(mapper.selectChallengeByTokenForUpdate(TOKEN)).thenReturn(consumed);
        assertThatThrownBy(() -> service.punch(invalid, null, 101L)).hasMessage("PUNCH_CHALLENGE_ALREADY_USED");
        when(mapper.selectChallengeByTokenForUpdate(TOKEN)).thenReturn(challenge());
        when(mapper.selectDatabaseClock()).thenReturn(null);
        var missingClock = command();
        missingClock.clientCaptureInstant = java.time.Instant.ofEpochMilli(clock.epochMillis);
        assertThatThrownBy(() -> service.punch(missingClock, null, 101L)).hasMessage("ATTENDANCE_SERVER_TIME_UNAVAILABLE");
        verify(mapper, never()).insertPunchEvent(any());
    }

    private PunchCommand command()
    {
        PunchCommand value = new PunchCommand();
        value.challengeToken = TOKEN;
        value.punchType = "IN";
        value.latitude = new BigDecimal("30.2741000");
        value.longitude = new BigDecimal("120.1551000");
        value.accuracyMeters = new BigDecimal("35.50");
        value.clientCoordinateSystem = "WGS84";
        value.clientCaptureTime = NOW;
        return value;
    }

    private void stubPunchPrelude(Challenge challenge, Schedule schedule)
    {
        when(mapper.selectDatabaseNow()).thenReturn(NOW);
        when(mapper.selectChallengeByTokenForUpdate(TOKEN))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
    }

    private Challenge challenge()
    {
        Challenge value = new Challenge();
        value.challengeId = 41L;
        value.challengeToken = TOKEN;
        value.scheduleId = 31L;
        value.userId = 7L;
        value.shopId = 101L;
        value.businessDate = DAY;
        value.punchType = "IN";
        value.status = "ISSUED";
        value.issuedAt = NOW.minusMinutes(1);
        value.expiresAt = NOW.plusMinutes(2);
        value.rowVersion = 0L;
        return value;
    }

    private Schedule schedule()
    {
        Schedule value = new Schedule();
        value.scheduleId = 31L;
        value.userId = 7L;
        value.userName = "张三";
        value.shopId = 101L;
        value.businessDate = DAY;
        value.shiftId = 5L;
        value.siteId = 51L;
        value.shiftNameSnapshot = "本地测试班次";
        value.status = "PUBLISHED";
        value.startTimeSnapshot = LocalTime.of(9, 0);
        value.endTimeSnapshot = LocalTime.of(18, 0);
        value.crossDaySnapshot = false;
        value.standardMinutesSnapshot = 480;
        value.graceInMinutesSnapshot = 0;
        value.graceOutMinutesSnapshot = 0;
        value.checkInOpenMinutesSnapshot = 60;
        value.checkInCloseMinutesSnapshot = 120;
        value.checkOutOpenMinutesSnapshot = 120;
        value.checkOutCloseMinutesSnapshot = 60;
        value.siteNameSnapshot = "华润大厦";
        value.addressSnapshot = "浙江省杭州市拱墅区华润大厦";
        Coordinate site = new AttendanceCoordinateTransformer().transform(
                new BigDecimal("30.2741000"),
                new BigDecimal("120.1551000"), "WGS84", "GCJ02");
        value.latitudeSnapshot = site.latitude();
        value.longitudeSnapshot = site.longitude();
        value.coordinateSystemSnapshot = "GCJ02";
        value.radiusMetersSnapshot = 200;
        value.maxAccuracyMetersSnapshot = 50;
        return value;
    }

    private ScheduleSegmentSnapshot workSegment()
    {
        ScheduleSegmentSnapshot value = new ScheduleSegmentSnapshot();
        value.scheduleSegmentSnapshotId = 91L;
        value.scheduleId = 31L;
        value.segmentOrder = 1;
        value.segmentType = "WORK";
        value.startMinuteOffset = 540;
        value.endMinuteOffset = 1020;
        value.paid = true;
        return value;
    }
}
