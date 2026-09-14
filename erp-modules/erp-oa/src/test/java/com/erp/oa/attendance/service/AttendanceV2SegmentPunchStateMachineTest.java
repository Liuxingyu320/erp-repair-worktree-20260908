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
import java.util.concurrent.atomic.AtomicReference;
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
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.dto.AttendanceRequests.ChallengeCreate;
import com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand;
import com.erp.oa.attendance.dto.AttendanceViews.TodayContext;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceAddressResolver.ResolvedAddress;
import com.erp.oa.attendance.support.AttendanceChallengePolicy;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceGeoFence;
import com.erp.oa.attendance.support.AttendanceLocationAuditPolicy;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceV2SegmentPunchStateMachineTest
{
    private static final LocalDate DAY = LocalDate.of(2026, 8, 20);
    private AttendanceV2Mapper mapper;
    private AttendanceLocationAuditPolicy locationPolicy;
    private AttendanceAddressResolver addressResolver;
    private AttendanceEvidenceStorageService evidenceStorage;
    private AttendanceRuntimePolicy runtimePolicy;
    private AttendanceV2Service service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("张三");
        TransactionSynchronizationManager.initSynchronization();
        mapper = mock(AttendanceV2Mapper.class);
        when(mapper.selectDatabaseClock()).thenAnswer(inv -> {
            var local = mapper.selectDatabaseNow();
            if (local == null) return null;
            var sample = new com.erp.oa.attendance.domain.AttendanceModels.DatabaseClock();
            sample.localTime = local;
            sample.epochMillis = local.toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli();
            return sample;
        });
        locationPolicy = mock(AttendanceLocationAuditPolicy.class);
        addressResolver = mock(AttendanceAddressResolver.class);
        evidenceStorage = mock(AttendanceEvidenceStorageService.class);
        runtimePolicy = mock(AttendanceRuntimePolicy.class);
        when(runtimePolicy.challengeTtlSeconds()).thenReturn(120);
        AttendanceRuleEngine rules = new AttendanceRuleEngine();
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        ShopScopeService shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        service = new AttendanceV2Service(mapper, rules, locationPolicy,
                new AttendanceCoordinateTransformer(),
                new AttendanceGeoFence(),
                addressResolver,
                new AttendanceChallengePolicy(),
                new AttendanceDaySettlementCalculator(rules),
                evidenceStorage,
                new AttendanceV2Properties(), runtimePolicy,
                shopScope, gate,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"),
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
    void todayPublishesFourStableSlotsForTwoWorkSegments()
    {
        LocalDateTime now = DAY.atTime(7, 45);
        stubToday(now, List.of(), List.of());

        TodayContext context = service.today(101L);

        assertThat(context.punchModeSnapshot).isEqualTo("PER_WORK_SEGMENT");
        assertThat(context.canPunch).isTrue();
        assertThat(context.state).isEqualTo("READY_IN");
        assertThat(context.allowedPunchSlotKey).isEqualTo("SEGMENT:91:IN");
        assertThat(context.nextPunchSlot.punchSlotKey)
                .isEqualTo("SEGMENT:91:IN");
        assertThat(context.punchSlots)
                .extracting(value -> value.punchSlotKey)
                .containsExactly("SEGMENT:91:IN", "SEGMENT:91:OUT",
                        "SEGMENT:93:IN", "SEGMENT:93:OUT");
        assertThat(context.punchSlots)
                .extracting(value -> value.segmentLabel)
                .containsExactly("上午工作段", "上午工作段",
                        "下午工作段", "下午工作段");
    }

    @Test
    void completedMorningSegmentShowsBreakAndPointsAtAfternoonCheckIn()
    {
        LocalDateTime now = DAY.atTime(12, 30);
        stubToday(now, List.of(event(101L, 91L, "IN", DAY.atTime(8, 0)),
                event(102L, 91L, "OUT", DAY.atTime(12, 0))), List.of());

        TodayContext context = service.today(101L);

        assertThat(context.canPunch).isFalse();
        assertThat(context.state).isEqualTo("BETWEEN_SEGMENTS");
        assertThat(context.lockReason).contains("休息中", "14:00");
        assertThat(context.allowedPunchSlotKey).isEqualTo("SEGMENT:93:IN");
        assertThat(context.nextPunchSlot.segmentLabel).isEqualTo("下午工作段");
    }

    @Test
    void fullLeaveForOneWorkSegmentExemptsOnlyItsTwoSlots()
    {
        LocalDateTime now = DAY.atTime(13, 45);
        LeaveSegmentSource morningLeave = new LeaveSegmentSource();
        morningLeave.startTime = DAY.atTime(8, 0);
        morningLeave.endTime = DAY.atTime(12, 0);
        stubToday(now, List.of(), List.of(morningLeave));

        TodayContext context = service.today(101L);

        assertThat(context.punchSlots.get(0).status).isEqualTo("EXEMPT_LEAVE");
        assertThat(context.punchSlots.get(1).status).isEqualTo("EXEMPT_LEAVE");
        assertThat(context.punchSlots.get(0).completed).isTrue();
        assertThat(context.punchSlots.get(1).completed).isTrue();
        assertThat(context.nextPunchSlot.punchSlotKey)
                .isEqualTo("SEGMENT:93:IN");
        assertThat(context.canPunch).isTrue();
    }

    @Test
    void missedMorningCheckOutDoesNotBlockAfternoonCheckIn()
    {
        LocalDateTime now = DAY.atTime(13, 45);
        stubToday(now, List.of(event(101L, 91L, "IN", DAY.atTime(8, 0))),
                List.of());

        TodayContext context = service.today(101L);

        assertThat(context.punchSlots.get(1).punchSlotKey)
                .isEqualTo("SEGMENT:91:OUT");
        assertThat(context.punchSlots.get(1).status).isEqualTo("MISSED");
        assertThat(context.nextPunchSlot.punchSlotKey)
                .isEqualTo("SEGMENT:93:IN");
        assertThat(context.canPunch).isTrue();
    }

    @Test
    void workIslandSkipsImpossibleOriginalSlotsAndKeepsLaterSegmentPunchable()
    {
        LocalDateTime now = DAY.atTime(13, 45);
        LeaveSegmentSource beforeIsland = new LeaveSegmentSource();
        beforeIsland.startTime = DAY.atTime(8, 0);
        beforeIsland.endTime = DAY.atTime(10, 0);
        LeaveSegmentSource afterIsland = new LeaveSegmentSource();
        afterIsland.startTime = DAY.atTime(11, 0);
        afterIsland.endTime = DAY.atTime(12, 0);
        stubToday(now, List.of(), List.of(beforeIsland, afterIsland));

        TodayContext context = service.today(101L);

        assertThat(context.punchSlots.get(0).status)
                .isEqualTo("REMAINING_WORK_PENDING");
        assertThat(context.punchSlots.get(1).status)
                .isEqualTo("REMAINING_WORK_PENDING");
        assertThat(context.punchSlots.get(0).completed).isFalse();
        assertThat(context.punchSlots.get(1).completed).isFalse();
        assertThat(context.nextPunchSlot.punchSlotKey)
                .isEqualTo("SEGMENT:93:IN");
        assertThat(context.canPunch).isTrue();
    }

    @Test
    void reviewedWorkIslandKeepsLaterSegmentPunchableAndCannotHideItsMissingPunches()
    {
        LeaveSegmentSource before = new LeaveSegmentSource();
        before.startTime = DAY.atTime(8, 0); before.endTime = DAY.atTime(10, 0);
        LeaveSegmentSource after = new LeaveSegmentSource();
        after.startTime = DAY.atTime(11, 0); after.endTime = DAY.atTime(12, 0);
        var confirmed = new com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource();
        confirmed.confirmationId = 801L; confirmed.scheduleId = 31L;
        confirmed.userId = 9L; confirmed.shopId = 101L; confirmed.businessDate = DAY;
        confirmed.remainingStart = DAY.atTime(10, 0); confirmed.remainingEnd = DAY.atTime(11, 0);
        confirmed.decision = "ATTENDED";
        confirmed.actualArrivalTime = confirmed.remainingStart;
        confirmed.actualDepartureTime = confirmed.remainingEnd;
        when(mapper.selectRemainingWorkConfirmationSources(31L, false)).thenReturn(List.of(confirmed));
        stubToday(DAY.atTime(13, 45), List.of(), List.of(before, after));
        TodayContext context = service.today(101L);
        assertThat(context.punchSlots.get(0).status).isEqualTo("REMAINING_WORK_CONFIRMED");
        assertThat(context.punchSlots.get(0).completed).isTrue();
        assertThat(context.punchSlots.get(0).requiresRemainingWorkConfirmation).isFalse();
        assertThat(context.nextPunchSlot.punchSlotKey).isEqualTo("SEGMENT:93:IN");
        assertThat(context.canPunch).isTrue();
        stubToday(DAY.atTime(23, 0), List.of(), List.of(before, after));
        assertThat(service.today(101L).state).isEqualTo("CLOSED_WITH_MISSING");
    }

    @Test
    void dayEndWithOnlyExpiredMissingSlotsIsNotReportedCompleted()
    {
        LocalDateTime now = DAY.atTime(19, 0);
        stubToday(now, List.of(), List.of());

        TodayContext context = service.today(101L);

        assertThat(context.state).isEqualTo("CLOSED_WITH_MISSING");
        assertThat(context.canPunch).isFalse();
        assertThat(context.nextPunchSlot).isNull();
        assertThat(context.punchSlots)
                .allMatch(value -> "MISSED".equals(value.status));
    }

    @Test
    void challengeIsBoundToTheExactNextSlotAndSnapshot()
    {
        LocalDateTime now = DAY.atTime(7, 45);
        Schedule schedule = schedule();
        stubSegmentState(schedule, List.of(), List.of(), false);
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectScheduleById(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.insertChallenge(any())).thenAnswer(invocation -> {
            Challenge value = invocation.getArgument(0);
            value.challengeId = 71L;
            value.rowVersion = 0L;
            return 1;
        });
        ChallengeCreate request = new ChallengeCreate();
        request.scheduleId = 31L;
        request.punchType = "IN";
        request.punchSlotKey = "SEGMENT:91:IN";

        var response = service.issueChallenge(request, 101L);

        assertThat(response.punchSlotKey).isEqualTo("SEGMENT:91:IN");
        ArgumentCaptor<Challenge> challenge = ArgumentCaptor.forClass(
                Challenge.class);
        verify(mapper).insertChallenge(challenge.capture());
        assertThat(challenge.getValue().scheduleSegmentSnapshotId)
                .isEqualTo(91L);
        assertThat(challenge.getValue().punchSlotKey)
                .isEqualTo("SEGMENT:91:IN");

        request.punchSlotKey = "SEGMENT:93:IN";
        assertThatThrownBy(() -> service.issueChallenge(request, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("PUNCH_SLOT_NOT_NEXT");
    }

    @Test
    void punchRejectsACommandThatChangesTheChallengeSlot()
    {
        LocalDateTime now = DAY.atTime(7, 45);
        Schedule schedule = schedule();
        Challenge challenge = new Challenge();
        challenge.challengeId = 71L;
        challenge.challengeToken = "a".repeat(64);
        challenge.scheduleId = 31L;
        challenge.userId = 9L;
        challenge.shopId = 101L;
        challenge.businessDate = DAY;
        challenge.punchType = "IN";
        challenge.scheduleSegmentSnapshotId = 91L;
        challenge.punchSlotKey = "SEGMENT:91:IN";
        challenge.status = "ISSUED";
        challenge.issuedAt = now.minusMinutes(1);
        challenge.expiresAt = now.plusMinutes(1);
        challenge.rowVersion = 0L;
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectChallengeByTokenForUpdate(challenge.challengeToken))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        PunchCommand command = new PunchCommand();
        command.challengeToken = challenge.challengeToken;
        command.punchType = "IN";
        command.punchSlotKey = "SEGMENT:93:IN";
        command.clientCaptureTime = now;

        assertThatThrownBy(() -> service.punch(command, null, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("PUNCH_SLOT_CHALLENGE_MISMATCH");
        verify(locationPolicy, never()).requireValid(any(), any(), any(), any());
    }

    @Test
    void punchCannotRewriteAnAlreadySettledDayResult()
    {
        LocalDateTime now = DAY.atTime(7, 45);
        Schedule schedule = schedule();
        Challenge challenge = new Challenge();
        challenge.challengeId = 72L;
        challenge.challengeToken = "c".repeat(64);
        challenge.scheduleId = 31L;
        challenge.userId = 9L;
        challenge.shopId = 101L;
        challenge.businessDate = DAY;
        challenge.punchType = "IN";
        challenge.scheduleSegmentSnapshotId = 91L;
        challenge.punchSlotKey = "SEGMENT:91:IN";
        challenge.status = "ISSUED";
        challenge.issuedAt = now.minusMinutes(1);
        challenge.expiresAt = now.plusMinutes(1);
        challenge.rowVersion = 0L;
        DayResult settled = new DayResult();
        settled.scheduleId = 31L;
        settled.settledAt = now.minusMinutes(2);
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectChallengeByTokenForUpdate(challenge.challengeToken))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectDayResultByScheduleId(31L)).thenReturn(settled);
        PunchCommand command = new PunchCommand();
        command.challengeToken = challenge.challengeToken;
        command.punchType = "IN";
        command.punchSlotKey = "SEGMENT:91:IN";
        command.clientCaptureTime = now;

        assertThatThrownBy(() -> service.punch(command, null, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("PUNCH_DAY_RESULT_ALREADY_SETTLED");
        verify(locationPolicy, never()).requireValid(any(), any(), any(), any());
        verify(evidenceStorage, never()).store(anyString(), any(), any(), any());
        verify(mapper, never()).insertPunchEvent(any());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void segmentedPunchUsesSegmentSettlementAndAddsChineseSegmentWatermark()
    {
        LocalDateTime now = DAY.atTime(12, 0);
        Schedule schedule = schedule();
        PunchEvent morningIn = event(101L, 91L, "IN", DAY.atTime(8, 0));
        AtomicReference<PunchEvent> inserted = new AtomicReference<>();
        Challenge challenge = new Challenge();
        challenge.challengeId = 71L;
        challenge.challengeToken = "b".repeat(64);
        challenge.scheduleId = 31L;
        challenge.userId = 9L;
        challenge.shopId = 101L;
        challenge.businessDate = DAY;
        challenge.punchType = "OUT";
        challenge.scheduleSegmentSnapshotId = 91L;
        challenge.punchSlotKey = "SEGMENT:91:OUT";
        challenge.status = "ISSUED";
        challenge.issuedAt = now.minusMinutes(1);
        challenge.expiresAt = now.plusMinutes(1);
        challenge.rowVersion = 0L;
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectChallengeByTokenForUpdate(challenge.challengeToken))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(schedule);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectScheduleSegmentSnapshots(eq(31L), anyBoolean()))
                .thenReturn(segments());
        when(mapper.selectAcceptedPunches(31L)).thenAnswer(invocation ->
                inserted.get() == null ? List.of(morningIn)
                        : List.of(morningIn, inserted.get()));
        when(mapper.selectApprovedLeaveSegments(eq(9L), eq(101L),
                eq(DAY.atTime(8, 0)), eq(DAY.atTime(18, 0)),
                anyBoolean())).thenReturn(List.of());
        when(locationPolicy.requireValid(any(), any(), any(), any()))
                .thenReturn("WGS84");
        when(addressResolver.resolve(any(), any(), eq("WGS84")))
                .thenReturn(new ResolvedAddress("浙江省杭州市拱墅区华润大厦",
                        "CONTROLLED_TEST"));
        when(mapper.selectShopOrganizationPath(101L)).thenReturn(
                "金英灵韵 / 北京区域 / 北京柏悦");
        when(evidenceStorage.store(anyString(), eq(DAY), any(), any()))
                .thenReturn(new AttendanceEvidenceStorageService.StoredEvidence(
                        "capture.jpg", "original.jpg", "watermarked.jpg",
                        10, 20, "sha1", "sha2", 640, 480, "payload"));
        when(mapper.insertPunchEvent(any())).thenAnswer(invocation -> {
            PunchEvent event = invocation.getArgument(0);
            event.punchEventId = 102L;
            inserted.set(event);
            return 1;
        });
        when(mapper.insertEvidence(any())).thenAnswer(invocation -> {
            Evidence value = invocation.getArgument(0);
            value.evidenceId = 201L;
            return 1;
        });
        when(mapper.upsertDayResult(any())).thenReturn(1);
        when(mapper.consumeChallenge(71L, 102L, now, 0L)).thenReturn(1);
        PunchCommand command = new PunchCommand();
        command.challengeToken = challenge.challengeToken;
        command.punchType = "OUT";
        command.punchSlotKey = "SEGMENT:91:OUT";
        command.clientCaptureTime = now;
        command.latitude = new BigDecimal("30.2741");
        command.longitude = new BigDecimal("120.1551");
        command.accuracyMeters = new BigDecimal("20");
        command.clientCoordinateSystem = "WGS84";
        MultipartFile photo = mock(MultipartFile.class);

        var result = service.punch(command, photo, 101L);

        assertThat(result.event.scheduleSegmentSnapshotId).isEqualTo(91L);
        assertThat(result.event.punchSlotKey).isEqualTo("SEGMENT:91:OUT");
        assertThat(result.dayResult.workedMinutes).isEqualTo(240);
        assertThat(result.dayResult.resultStatus).isEqualTo("PENDING");
        assertThat(result.dayResult.exceptionCodes)
                .contains("SHIFT_NOT_ENDED", "MISSING_IN", "MISSING_OUT");
        ArgumentCaptor<List> watermark = ArgumentCaptor.forClass(List.class);
        verify(evidenceStorage).store(anyString(), eq(DAY), eq(photo),
                watermark.capture());
        assertThat(watermark.getValue()).contains("工作段：上午工作段");
    }

    private void stubToday(LocalDateTime now, List<PunchEvent> accepted,
            List<LeaveSegmentSource> leaves)
    {
        Schedule schedule = schedule();
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(schedule));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        stubSegmentState(schedule, accepted, leaves, false);
    }

    private void stubSegmentState(Schedule schedule,
            List<PunchEvent> accepted, List<LeaveSegmentSource> leaves,
            boolean lockRows)
    {
        when(mapper.selectScheduleSegmentSnapshots(31L, lockRows))
                .thenReturn(segments());
        when(mapper.selectAcceptedPunches(31L)).thenReturn(accepted);
        when(mapper.selectApprovedLeaveSegments(9L, 101L,
                DAY.atTime(8, 0), DAY.atTime(18, 0), lockRows))
                .thenReturn(leaves);
    }

    private Schedule schedule()
    {
        Schedule value = new Schedule();
        value.scheduleId = 31L;
        value.userId = 9L;
        value.userName = "张三";
        value.shopId = 101L;
        value.businessDate = DAY;
        value.shiftId = 5L;
        value.siteId = 51L;
        value.shiftNameSnapshot = "早晚分段班";
        value.punchModeSnapshot = "PER_WORK_SEGMENT";
        value.status = "PUBLISHED";
        value.startTimeSnapshot = LocalTime.of(8, 0);
        value.endTimeSnapshot = LocalTime.of(18, 0);
        value.crossDaySnapshot = false;
        value.standardMinutesSnapshot = 480;
        value.checkInOpenMinutesSnapshot = 30;
        value.checkInCloseMinutesSnapshot = 30;
        value.checkOutOpenMinutesSnapshot = 30;
        value.checkOutCloseMinutesSnapshot = 30;
        value.siteNameSnapshot = "华润大厦";
        value.addressSnapshot = "浙江省杭州市拱墅区华润大厦";
        value.latitudeSnapshot = new BigDecimal("30.2741");
        value.longitudeSnapshot = new BigDecimal("120.1551");
        value.coordinateSystemSnapshot = "WGS84";
        value.radiusMetersSnapshot = 200;
        value.maxAccuracyMetersSnapshot = 50;
        return value;
    }

    private List<ScheduleSegmentSnapshot> segments()
    {
        return List.of(segment(91L, 1, "WORK", 480, 720),
                segment(92L, 2, "BREAK", 720, 840),
                segment(93L, 3, "WORK", 840, 1080));
    }

    private ScheduleSegmentSnapshot segment(Long id, int order, String type,
            int start, int end)
    {
        ScheduleSegmentSnapshot value = new ScheduleSegmentSnapshot();
        value.scheduleSegmentSnapshotId = id;
        value.scheduleId = 31L;
        value.segmentOrder = order;
        value.segmentType = type;
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        value.paid = "WORK".equals(type);
        return value;
    }

    private PunchEvent event(Long eventId, Long segmentId, String type,
            LocalDateTime time)
    {
        PunchEvent value = new PunchEvent();
        value.punchEventId = eventId;
        value.scheduleId = 31L;
        value.userId = 9L;
        value.shopId = 101L;
        value.businessDate = DAY;
        value.punchType = type;
        value.scheduleSegmentSnapshotId = segmentId;
        value.punchSlotKey = "SEGMENT:" + segmentId + ":" + type;
        value.serverPunchTime = time;
        value.verificationStatus = "ACCEPTED";
        return value;
    }
}
