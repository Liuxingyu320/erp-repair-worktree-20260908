package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.erp.common.core.exception.ServiceException;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.config.AttendanceRuntimePolicy;
import com.erp.oa.attendance.config.AttendanceV2Properties;
import com.erp.oa.attendance.domain.AttendanceModels.DayResult;
import com.erp.oa.attendance.domain.AttendanceModels.EmployeeOption;
import com.erp.oa.attendance.domain.AttendanceModels.PunchEvent;
import com.erp.oa.attendance.domain.AttendanceModels.Schedule;
import com.erp.oa.attendance.domain.AttendanceModels.ScheduleSegmentSnapshot;
import com.erp.oa.attendance.domain.AttendanceModels.Shift;
import com.erp.oa.attendance.domain.AttendanceModels.ShiftSegment;
import com.erp.oa.attendance.domain.AttendanceModels.Site;
import com.erp.oa.attendance.dto.AttendanceRequests.ScheduleBatch;
import com.erp.oa.attendance.dto.AttendanceRequests.ScheduleItem;
import com.erp.oa.attendance.dto.AttendanceRequests.SchedulePublish;
import com.erp.oa.attendance.dto.AttendanceRequests.ChallengeCreate;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.attendance.mapper.AttendanceV2Mapper;
import com.erp.oa.attendance.support.AttendanceAddressResolver;
import com.erp.oa.attendance.support.AttendanceChallengePolicy;
import com.erp.oa.attendance.support.AttendanceCoordinateTransformer;
import com.erp.oa.attendance.support.AttendanceDaySettlementCalculator;
import com.erp.oa.attendance.support.AttendanceGeoFence;
import com.erp.oa.attendance.support.AttendanceLocationAuditPolicy;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceV2ServiceScopeTest
{
    private AttendanceV2Mapper mapper;
    private ShopScopeService shopScope;
    private AttendanceV2Service service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("9");
        SecurityContextHolder.setUserName("tester");
        mapper = mock(AttendanceV2Mapper.class);
        when(mapper.selectDatabaseClock()).thenAnswer(inv -> {
            var local = mapper.selectDatabaseNow();
            if (local == null) return null;
            var sample = new com.erp.oa.attendance.domain.AttendanceModels.DatabaseClock();
            sample.localTime = local;
            sample.epochMillis = local.toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli();
            return sample;
        });
        shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        BusinessFeatureGate featureGate = mock(BusinessFeatureGate.class);
        doNothing().when(featureGate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        service = new AttendanceV2Service(mapper,
                new AttendanceRuleEngine(),
                mock(AttendanceLocationAuditPolicy.class),
                new AttendanceCoordinateTransformer(),
                new AttendanceGeoFence(),
                mock(AttendanceAddressResolver.class),
                mock(AttendanceChallengePolicy.class),
                new AttendanceDaySettlementCalculator(
                        new AttendanceRuleEngine()),
                mock(AttendanceEvidenceStorageService.class),
                new AttendanceV2Properties(),
                mock(AttendanceRuntimePolicy.class), shopScope, featureGate,
                Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"),
                        ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void employeeOptionsRejectsARequestedShopOutsideTheSelectedShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);

        assertThatThrownBy(() -> service.employeeOptions(202L, null, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ATTENDANCE_SHOP_SCOPE_MISMATCH");

        verifyNoInteractions(mapper);
    }

    @Test
    void employeeOptionsOnlyQueriesTheResolvedSelectedShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        EmployeeOption option = new EmployeeOption();
        option.userId = 7L;
        option.userName = "张三";
        option.employeeNo = "E007";
        when(mapper.selectActiveEmployeeOptions(101L, "张"))
                .thenReturn(List.of(option));

        List<EmployeeOption> values = service.employeeOptions(101L, " 张 ",
                101L);

        assertThat(values).containsExactly(option);
        verify(mapper).selectActiveEmployeeOptions(101L, "张");
    }

    @Test
    void managerDayResultsCannotQueryAnotherShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);

        assertThatThrownBy(() -> service.listDayResults(202L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                null, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ATTENDANCE_SHOP_SCOPE_MISMATCH");

        verify(mapper, never()).selectDayResultsByShopAndRange(202L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null);
    }

    @Test
    void managerDayResultsUseTheResolvedSelectedShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        DayResult result = new DayResult();
        result.scheduleId = 31L;
        when(mapper.selectDayResultsByShopAndRange(101L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 7L))
                .thenReturn(List.of(result));

        assertThat(service.listDayResults(101L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                7L, 101L)).containsExactly(result);
    }

    @Test
    void aNewDraftSchedulePersistsAnEnabledSiteInTheSameShop()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("张三");
        Shift shift = new Shift();
        shift.shiftId = 5L;
        when(mapper.selectShiftById(5L)).thenReturn(shift);
        Site site = enabledSite(51L, 101L);
        when(mapper.selectSiteByIdForUpdate(51L)).thenReturn(site);
        when(mapper.selectScheduleByUserAndDateForUpdate(7L,
                LocalDate.of(2026, 8, 21))).thenReturn(null);
        when(mapper.insertDraftSchedule(any())).thenAnswer(invocation -> {
            Schedule value = invocation.getArgument(0);
            value.scheduleId = 31L;
            return 1;
        });
        Schedule saved = new Schedule();
        saved.scheduleId = 31L;
        when(mapper.selectScheduleById(31L)).thenReturn(saved);
        ScheduleItem item = new ScheduleItem();
        item.userId = 7L;
        item.businessDate = LocalDate.of(2026, 8, 21);
        item.shiftId = 5L;
        item.siteId = 51L;
        ScheduleBatch command = new ScheduleBatch();
        command.shopId = 101L;
        command.items = List.of(item);

        assertThat(service.saveScheduleBatch(command, 101L))
                .containsExactly(saved);

        ArgumentCaptor<Schedule> draft = ArgumentCaptor.forClass(
                Schedule.class);
        verify(mapper).insertDraftSchedule(draft.capture());
        assertThat(draft.getValue().siteId).isEqualTo(51L);
        verify(mapper).selectSiteByIdForUpdate(51L);
    }

    @Test
    void aNewDraftScheduleWithoutASiteFailsClosed()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        ScheduleItem item = new ScheduleItem();
        item.userId = 7L;
        item.businessDate = LocalDate.of(2026, 8, 21);
        item.shiftId = 5L;
        ScheduleBatch command = new ScheduleBatch();
        command.shopId = 101L;
        command.items = List.of(item);

        assertThatThrownBy(() -> service.saveScheduleBatch(command, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("SCHEDULE_ITEM_INCOMPLETE");
        verify(mapper, never()).insertDraftSchedule(any());
    }

    @Test
    void aNewDraftRejectsCrossShopOrDisabledSites()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("张三");
        Shift shift = new Shift();
        shift.shiftId = 5L;
        when(mapper.selectShiftById(5L)).thenReturn(shift);
        when(mapper.selectScheduleByUserAndDateForUpdate(7L,
                LocalDate.of(2026, 8, 21))).thenReturn(null);
        ScheduleItem item = new ScheduleItem();
        item.userId = 7L;
        item.businessDate = LocalDate.of(2026, 8, 21);
        item.shiftId = 5L;
        item.siteId = 51L;
        ScheduleBatch command = new ScheduleBatch();
        command.shopId = 101L;
        command.items = List.of(item);

        when(mapper.selectSiteByIdForUpdate(51L))
                .thenReturn(enabledSite(51L, 202L));
        assertThatThrownBy(() -> service.saveScheduleBatch(command, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_SITE_NOT_FOUND_IN_SHOP");

        Site disabled = enabledSite(51L, 101L);
        disabled.status = "DISABLED";
        when(mapper.selectSiteByIdForUpdate(51L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.saveScheduleBatch(command, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("ATTENDANCE_SITE_MUST_BE_ENABLED");
        verify(mapper, never()).insertDraftSchedule(any());
    }

    @Test
    void deletingADraftRequiresSelectedShopAndMatchingVersion()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        Schedule draft = new Schedule();
        draft.scheduleId = 31L;
        draft.shopId = 101L;
        draft.status = "DRAFT";
        draft.rowVersion = 4L;
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(draft);
        when(mapper.deleteDraftSchedule(31L, 101L, 4L)).thenReturn(1);

        service.deleteDraftSchedule(31L, 4L, 101L);

        verify(mapper).deleteDraftSchedule(31L, 101L, 4L);
    }

    @Test
    void deletingAPublishedScheduleFailsClosed()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        Schedule published = new Schedule();
        published.scheduleId = 31L;
        published.shopId = 101L;
        published.status = "PUBLISHED";
        published.rowVersion = 4L;
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(published);

        assertThatThrownBy(() -> service.deleteDraftSchedule(31L, 4L, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PUBLISHED_SCHEDULE_IMMUTABLE");
        verify(mapper, never()).deleteDraftSchedule(31L, 101L, 4L);
    }

    @Test
    void publishedScheduleListingsIncludeFrozenSegmentSnapshots()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        Schedule published = new Schedule();
        published.scheduleId = 31L;
        published.shopId = 101L;
        published.status = "PUBLISHED";
        ScheduleSegmentSnapshot work = new ScheduleSegmentSnapshot();
        work.scheduleSegmentSnapshotId = 91L;
        work.scheduleId = 31L;
        work.segmentOrder = 1;
        work.segmentType = "WORK";
        when(mapper.selectSchedules(101L, LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21), null, List.of(101L)))
                .thenReturn(List.of(published));
        when(mapper.selectScheduleSegmentSnapshots(31L, false))
                .thenReturn(List.of(work));

        assertThat(service.listSchedules(101L,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21),
                null, 101L).get(0).segmentSnapshots).containsExactly(work);
    }

    @Test
    void publishingFreezesShiftSegmentsInTheSameWorkflow()
    {
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        Schedule draft = new Schedule();
        draft.scheduleId = 31L;
        draft.shopId = 101L;
        draft.userId = 7L;
        draft.shiftId = 5L;
        draft.siteId = 51L;
        draft.status = "DRAFT";
        Shift shift = new Shift();
        shift.shiftId = 5L;
        shift.shiftCode = "SPLIT_DAY";
        shift.shiftName = "早晚分段班";
        shift.startTime = LocalTime.of(8, 0);
        shift.endTime = LocalTime.of(18, 0);
        shift.standardMinutes = 480;
        Schedule published = new Schedule();
        published.scheduleId = 31L;
        published.shopId = 101L;
        published.userId = 7L;
        published.shiftId = 5L;
        published.businessDate = LocalDate.of(2026, 8, 20);
        published.standardMinutesSnapshot = 480;
        published.status = "PUBLISHED";
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(draft);
        when(mapper.countActiveEmployeeInShop(7L, 101L)).thenReturn(1);
        when(mapper.selectShiftByIdForUpdate(5L)).thenReturn(shift);
        when(mapper.selectSiteByIdForUpdate(51L))
                .thenReturn(enabledSite(51L, 101L));
        when(mapper.selectShiftSegments(5L)).thenReturn(List.of(
                shiftSegment("WORK", 480, 720, true),
                shiftSegment("BREAK", 720, 840, false),
                shiftSegment("WORK", 840, 1080, true)));
        when(mapper.publishSchedule(31L, 101L, 9L, "tester"))
                .thenReturn(1);
        when(mapper.insertScheduleSegmentSnapshotsFromShift(31L, 5L))
                .thenReturn(3);
        when(mapper.selectScheduleById(31L)).thenReturn(published);
        when(mapper.selectScheduleSegmentSnapshots(31L, false))
                .thenReturn(List.of(scheduleSegment(31L, 91L, 1,
                        480, 720)));
        SchedulePublish command = new SchedulePublish();
        command.shopId = 101L;
        command.scheduleIds = List.of(31L);

        assertThat(service.publishSchedules(command, 101L))
                .containsExactly(published);

        verify(mapper).deleteScheduleSegmentSnapshots(31L);
        verify(mapper).insertScheduleSegmentSnapshotsFromShift(31L, 5L);
    }

    @Test
    void aContinuousShiftGetsAnExplicitWorkSegmentSnapshotSource()
    {
        Shift shift = new Shift();
        shift.shiftCode = "DAY_12H";
        shift.shiftName = "白班";
        shift.startTime = LocalTime.of(8, 0);
        shift.endTime = LocalTime.of(20, 0);
        shift.standardMinutes = 720;
        when(mapper.insertShift(shift)).thenAnswer(invocation -> {
            shift.shiftId = 5L;
            return 1;
        });
        when(mapper.selectShiftById(5L)).thenReturn(shift);
        when(mapper.selectShiftSegments(5L)).thenReturn(List.of());
        when(mapper.insertShiftSegment(any())).thenReturn(1);

        service.createShift(shift);

        ArgumentCaptor<ShiftSegment> segment = ArgumentCaptor.forClass(
                ShiftSegment.class);
        verify(mapper).insertShiftSegment(segment.capture());
        assertThat(segment.getValue().segmentType).isEqualTo("WORK");
        assertThat(segment.getValue().startMinuteOffset).isEqualTo(480);
        assertThat(segment.getValue().endMinuteOffset).isEqualTo(1200);
        assertThat(segment.getValue().paid).isTrue();
    }

    @Test
    void afterMidnightCanStillChooseYesterdayCrossNightShiftForLateCheckIn()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 21, 0, 30);
        Schedule yesterday = schedule(31L, LocalDate.of(2026, 8, 20),
                LocalTime.of(20, 0), LocalTime.of(4, 0), true);
        Schedule today = schedule(32L, LocalDate.of(2026, 8, 21),
                LocalTime.of(20, 0), LocalTime.of(4, 0), true);
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(today, yesterday));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today(101L).schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today(101L).allowedPunchType).isEqualTo("IN");
    }

    @Test
    void unfinishedCrossNightCheckOutTakesPriorityOverTonightShift()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 21, 4, 30);
        Schedule yesterday = schedule(31L, LocalDate.of(2026, 8, 20),
                LocalTime.of(20, 0), LocalTime.of(4, 0), true);
        Schedule today = schedule(32L, LocalDate.of(2026, 8, 21),
                LocalTime.of(20, 0), LocalTime.of(4, 0), true);
        PunchEvent in = new PunchEvent();
        in.punchEventId = 41L;
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(today, yesterday));
        when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(in);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today(101L).schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today(101L).allowedPunchType).isEqualTo("OUT");
    }

    @Test
    void todayEvidenceLookupUsesTheCurrentUserAndChosenScheduleScope()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Schedule today = schedule(31L, now.toLocalDate(), LocalTime.of(8, 0),
                LocalTime.of(20, 0), false);
        PunchEvent latest = new PunchEvent();
        latest.punchEventId = 41L;
        latest.scheduleId = 999L;
        latest.userId = 777L;
        latest.serverPunchTime = LocalDateTime.of(2026, 8, 20, 11, 58);
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectLatestPunch(31L)).thenReturn(latest);
        when(mapper.selectEvidenceIdForPunchInScope(41L, 31L, 9L))
                .thenReturn(77L);

        var context = service.today(101L);

        assertThat(context.latestPunch).isSameAs(latest);
        assertThat(context.latestPunch.serverPunchTime)
                .isEqualTo(LocalDateTime.of(2026, 8, 20, 11, 58));
        assertThat(context.latestEvidenceId).isEqualTo(77L);
        verify(mapper).selectEvidenceIdForPunchInScope(41L, 31L, 9L);
    }

    @Test
    void todayWithoutALatestPunchDoesNotQueryEvidence()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Schedule today = schedule(31L, now.toLocalDate(), LocalTime.of(8, 0),
                LocalTime.of(20, 0), false);
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today(101L).latestEvidenceId).isNull();
        verify(mapper, never()).selectEvidenceIdForPunchInScope(any(), any(),
                any());
    }

    @Test
    void approvedFullShiftLeaveLocksPunchingWithoutRequiringFakePunches()
    {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 9, 0);
        Schedule today = schedule(31L, now.toLocalDate(), LocalTime.of(8, 0),
                LocalTime.of(20, 0), false);
        today.standardMinutesSnapshot = 720;
        ScheduleSegmentSnapshot work = new ScheduleSegmentSnapshot();
        work.scheduleSegmentSnapshotId = 91L;
        work.scheduleId = 31L;
        work.segmentOrder = 1;
        work.segmentType = "WORK";
        work.startMinuteOffset = 480;
        work.endMinuteOffset = 1200;
        work.paid = true;
        LeaveSegmentSource leave = new LeaveSegmentSource();
        leave.startTime = LocalDateTime.of(2026, 8, 20, 8, 0);
        leave.endTime = LocalDateTime.of(2026, 8, 20, 20, 0);
        leave.totalMinutes = 720;
        leave.paidMinutes = 720;
        leave.unpaidMinutes = 0;
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectScheduleSegmentSnapshots(31L, false))
                .thenReturn(List.of(work));
        when(mapper.selectApprovedLeaveSegments(9L, 101L,
                LocalDateTime.of(2026, 8, 20, 8, 0),
                LocalDateTime.of(2026, 8, 20, 20, 0), false))
                .thenReturn(List.of(leave));

        assertThat(service.today(101L).state).isEqualTo("ON_LEAVE");
        assertThat(service.today(101L).canPunch).isFalse();
        when(mapper.selectScheduleById(31L)).thenReturn(today);
        ChallengeCreate challenge = new ChallengeCreate();
        challenge.scheduleId = 31L;
        challenge.punchType = "IN";
        assertThatThrownBy(() -> service.issueChallenge(challenge, 101L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PUNCH_BLOCKED_BY_APPROVED_LEAVE");
    }

    @Test
    void missedBoundaryInDoesNotBlockAnOpenOutWindow()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 18, 0);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        value.checkInCloseMinutesSnapshot = 120;
        stubRecoveryToday(now, List.of(value));
        var context = service.today(101L);
        assertThat(context.canPunch).isTrue();
        assertThat(context.allowedPunchType).isEqualTo("OUT");
        when(mapper.selectScheduleById(31L)).thenReturn(value);
        when(mapper.insertChallenge(any())).thenReturn(1);
        ChallengeCreate request = new ChallengeCreate();
        request.scheduleId = 31L;
        request.punchType = "OUT";
        var challenge = service.issueChallenge(request, 101L);
        assertThat(challenge.punchType).isEqualTo("OUT");
        assertThat(java.time.Instant.parse(challenge.expiresAtUtc))
                .isEqualTo(challenge.expiresAt.toInstant(java.time.ZoneOffset.ofHours(8)));
        verify(mapper, never()).insertPunchEvent(any());
    }

    @Test
    void boundaryKeepsExactInDeadlineAndReportsMissingAfterAllWindowsClose()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 11, 0);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        value.checkInCloseMinutesSnapshot = 120;
        stubRecoveryToday(now, List.of(value));
        assertThat(service.today(101L).allowedPunchType).isEqualTo("IN");
        assertThat(service.today(101L).canPunch).isTrue();
        when(mapper.selectDatabaseNow()).thenReturn(now.plusSeconds(1));
        assertThat(service.today(101L).allowedPunchType).isEqualTo("OUT");
        assertThat(service.today(101L).canPunch).isFalse();
        when(mapper.selectDatabaseNow()).thenReturn(now.withHour(21));
        assertThat(service.today(101L).state).isEqualTo("CLOSED_WITH_MISSING");
        assertThat(service.today(101L).canPunch).isFalse();
    }

    @Test
    void tomorrowEarlyWindowTakesPriorityOverACompletedTodayShift()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 23, 30);
        Schedule today = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        Schedule tomorrow = schedule(32L, now.toLocalDate().plusDays(1),
                LocalTime.of(0, 15), LocalTime.of(8, 15), false);
        tomorrow.checkInOpenMinutesSnapshot = 60;
        stubRecoveryToday(now, List.of(tomorrow, today));
        assertThat(service.today(101L).schedule.scheduleId).isEqualTo(32L);
        assertThat(service.today(101L).canPunch).isTrue();
        assertThat(service.today(101L).allowedPunchType).isEqualTo("IN");
    }

    @Test
    void previousNonCrossDayOutWindowStillWinsAfterMidnight()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 0, 30);
        Schedule previous = schedule(31L, now.toLocalDate().minusDays(1),
                LocalTime.of(15, 0), LocalTime.of(23, 30), false);
        Schedule today = schedule(32L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(new PunchEvent());
        stubRecoveryToday(now, List.of(today, previous));
        assertThat(service.today(101L).schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today(101L).canPunch).isTrue();
        assertThat(service.today(101L).allowedPunchType).isEqualTo("OUT");
    }

    @Test
    void delayedCrossDayOutCanBelongToTheBusinessDateTwoDaysEarlier()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 0, 30);
        Schedule previous = schedule(31L, now.toLocalDate().minusDays(2),
                LocalTime.of(23, 0), LocalTime.of(22, 30), true);
        when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(new PunchEvent());
        stubRecoveryToday(now, List.of(previous));
        assertThat(service.today(101L).schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today(101L).canPunch).isTrue();
        assertThat(service.today(101L).allowedPunchType).isEqualTo("OUT");
    }

    @Test
    void selectedStoreMismatchLocksTodayAndCannotIssueAChallenge()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 9, 0);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        stubRecoveryToday(now, List.of(value));
        when(shopScope.resolveRequiredShopDept(202L)).thenReturn(202L);
        assertThat(service.today(202L).state).isEqualTo("LOCKED_WRONG_STORE");
        assertThat(service.today(202L).schedule).isNull();
        when(mapper.selectScheduleById(31L)).thenReturn(value);
        ChallengeCreate request = new ChallengeCreate();
        request.scheduleId = 31L;
        request.punchType = "IN";
        assertThatThrownBy(() -> service.issueChallenge(request, 202L))
                .hasMessage("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        verify(mapper, never()).insertChallenge(any());
    }

    @Test
    void selectedStoreMismatchCannotSubmitAPreviouslyIssuedChallenge()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 9, 0);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0),
                LocalTime.of(18, 0), false);
        stubRecoveryToday(now, List.of(value));
        when(shopScope.resolveRequiredShopDept(202L)).thenReturn(202L);
        var challenge = new com.erp.oa.attendance.domain.AttendanceModels.Challenge();
        challenge.scheduleId = 31L;
        challenge.issuedAt = now.minusSeconds(1);
        when(mapper.selectChallengeByTokenForUpdate("scope-proof-token"))
                .thenReturn(challenge);
        when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(value);
        var command = new com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand();
        command.challengeToken = "scope-proof-token";
        command.punchType = "IN";
        command.clientCaptureTime = now;
        assertThatThrownBy(() -> service.punch(command, null, 202L))
                .hasMessage("ATTENDANCE_SHOP_SCOPE_MISMATCH");
        verify(mapper, never()).insertPunchEvent(any());
    }

    @Test
    void approvedMissingCorrectionsCompleteBothModesAndCannotReissueTheSlot()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
        {
            LocalDateTime now = LocalDateTime.of(2026, 9, 9, 20, 1);
            Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
            value.punchModeSnapshot = mode;
            value.standardMinutesSnapshot = 540;
            stubRecoveryToday(now, List.of(value));
            var correction = correction(value, "IN", "APPROVED");
            when(mapper.selectSettlementCorrectionSources(31L, false)).thenReturn(List.of(correction));
            var out = accepted(value, "OUT", now.withHour(18).withMinute(0));
            when(mapper.selectLastAcceptedPunch(31L, "OUT")).thenReturn(out);
            when(mapper.selectAcceptedPunches(31L)).thenReturn(List.of(out));
            var context = service.today(101L);
            assertThat(context.state).as(mode).isEqualTo("COMPLETED");
            if ("PER_WORK_SEGMENT".equals(mode))
                assertThat(context.punchSlots.get(0).status).isEqualTo("CORRECTED");
            when(mapper.selectDatabaseNow()).thenReturn(now.withHour(10));
            when(mapper.selectLastAcceptedPunch(31L, "OUT")).thenReturn(null);
            when(mapper.selectAcceptedPunches(31L)).thenReturn(List.of());
            when(mapper.selectScheduleById(31L)).thenReturn(value);
            ChallengeCreate request = new ChallengeCreate();
            request.scheduleId = 31L; request.punchType = "IN";
            request.punchSlotKey = correction.targetPunchSlotKey;
            assertThatThrownBy(() -> service.issueChallenge(request, 101L)).isInstanceOf(ServiceException.class);
        }
        verify(mapper, never()).insertChallenge(any());
    }

    @Test
    void pendingCorrectionsDoNotCompleteSlotsAndInvalidApprovedEvidenceFailsClosed()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 10, 0);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        value.standardMinutesSnapshot = 540;
        stubRecoveryToday(now, List.of(value));
        var correction = correction(value, "IN", "PENDING");
        when(mapper.selectSettlementCorrectionSources(31L, false)).thenReturn(List.of(correction));
        assertThat(service.today(101L).state).isEqualTo("READY_IN");
        correction.status = "APPROVED"; correction.userId = 999L;
        assertThatThrownBy(() -> service.today(101L)).hasMessage("PUNCH_CORRECTION_EVIDENCE_INVALID");
    }

    @Test
    void wrongTypeCorrectionDoesNotReopenTheImmutableOriginalSlot()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 18, 1);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        value.standardMinutesSnapshot = 540; value.checkInCloseMinutesSnapshot = 720;
        stubRecoveryToday(now, List.of(value));
        var original = accepted(value, "IN", now.minusMinutes(1));
        when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(original);
        when(mapper.selectAcceptedPunches(31L)).thenReturn(List.of(original));
        var correction = correction(value, "OUT", "APPROVED");
        correction.correctionType = "WRONG_TYPE"; correction.originalPunchEventId = original.punchEventId;
        when(mapper.selectSettlementCorrectionSources(31L, false)).thenReturn(List.of(correction));
        var context = service.today(101L);
        assertThat(context.canPunch).isFalse();
        assertThat(context.state).isEqualTo("CLOSED_WITH_MISSING");
        // A new approved missing correction can restore the moved-away slot;
        // the original physical event remains immutable and is not duplicated.
        when(mapper.selectSettlementCorrectionSources(31L, false)).thenReturn(
                List.of(correction, correction(value, "IN", "APPROVED")));
        assertThat(service.today(101L).state).isEqualTo("COMPLETED");
    }

    @Test
    void leaveExemptionSurvivesAnApprovedTypeReplacementOfTheOriginalSlot()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 18, 1);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        value.punchModeSnapshot = "PER_WORK_SEGMENT"; value.standardMinutesSnapshot = 540;
        value.checkInCloseMinutesSnapshot = 720;
        stubRecoveryToday(now, List.of(value));
        var original = accepted(value, "IN", now.minusMinutes(1));
        when(mapper.selectAcceptedPunches(31L)).thenReturn(List.of(original));
        var moved = correction(value, "OUT", "APPROVED");
        moved.correctionType = "WRONG_TYPE"; moved.originalPunchEventId = original.punchEventId;
        when(mapper.selectSettlementCorrectionSources(31L, false)).thenReturn(List.of(moved));
        var leave = new LeaveSegmentSource(); leave.startTime = now.toLocalDate().atTime(9, 0);
        leave.endTime = now.toLocalDate().atTime(10, 0);
        when(mapper.selectApprovedLeaveSegments(9L, 101L, leave.startTime, now.toLocalDate().atTime(18, 0), false))
                .thenReturn(List.of(leave));
        var context = service.today(101L);
        assertThat(context.state).isEqualTo("COMPLETED");
        assertThat(context.punchSlots.get(0).status).isEqualTo("EXEMPT_LEAVE");
    }

    @Test
    void correctionApprovedAfterChallengeIsRecheckedBeforePunchWriting()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
        {
            LocalDateTime now = LocalDateTime.of(2026, 9, 9, 10, 0);
            Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
            value.punchModeSnapshot = mode; value.standardMinutesSnapshot = 540;
            stubRecoveryToday(now, List.of(value));
            var snapshots = mapper.selectScheduleSegmentSnapshots(31L, false);
            when(mapper.selectScheduleSegmentSnapshots(31L, true)).thenReturn(snapshots);
            when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(value);
            var correction = correction(value, "IN", "APPROVED");
            when(mapper.selectSettlementCorrectionSources(31L, true)).thenReturn(List.of(correction));
            var token = new com.erp.oa.attendance.domain.AttendanceModels.Challenge();
            token.scheduleId = 31L; token.shopId = 101L; token.businessDate = now.toLocalDate();
            token.issuedAt = now.minusSeconds(5); token.punchSlotKey = correction.targetPunchSlotKey;
            token.scheduleSegmentSnapshotId = correction.targetScheduleSegmentSnapshotId;
            when(mapper.selectChallengeByTokenForUpdate("pending-correction-token")).thenReturn(token);
            var command = new com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand();
            command.challengeToken = "pending-correction-token"; command.punchType = "IN";
            command.punchSlotKey = correction.targetPunchSlotKey; command.clientCaptureTime = now;
            assertThatThrownBy(() -> service.punch(command, null, 101L)).hasMessage(
                    "PER_WORK_SEGMENT".equals(mode) ? "PUNCH_SLOT_NOT_NEXT" : "PUNCH_SLOT_TYPE_MISMATCH");
        }
        verify(mapper, never()).insertPunchEvent(any());
    }

    @Test
    void confirmedRemainingWorkClosesTodayWithoutReopeningOriginalSlots()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
            for (String decision : List.of("ATTENDED", "ABSENT"))
            {
                Schedule value = remainingWorkSchedule(mode);
                var confirmed = remainingConfirmation(value, 801L, decision);
                when(mapper.selectRemainingWorkConfirmationSources(31L, false))
                        .thenReturn(List.of(confirmed));
                var today = service.today(101L);
                assertThat(today.state).isEqualTo("REMAINING_WORK_CONFIRMED");
                assertThat(today.canPunch).isFalse();
                if ("PER_WORK_SEGMENT".equals(mode))
                    assertThat(today.punchSlots).allSatisfy(slot -> {
                        assertThat(slot.status).isEqualTo("REMAINING_WORK_CONFIRMED");
                        assertThat(slot.completed).isTrue();
                    });
                ChallengeCreate request = new ChallengeCreate();
                request.scheduleId = 31L; request.punchType = "IN";
                request.punchSlotKey = "PER_WORK_SEGMENT".equals(mode) ? "SEGMENT:1031:IN" : null;
                assertThatThrownBy(() -> service.issueChallenge(request, 101L))
                        .hasMessage("ALL_PUNCH_SLOTS_COMPLETED");
            }
        verify(mapper, never()).insertChallenge(any());
    }

    @Test
    void remainingWorkUsesLatestDecisionAndDoesNotTreatPhysicalPunchesAsReview()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
        {
            Schedule value = remainingWorkSchedule(mode);
            var attended = remainingConfirmation(value, 801L, "ATTENDED");
            var returned = remainingConfirmation(value, 802L, "RETURN_FOR_EVIDENCE");
            var raw = accepted(value, "IN", value.businessDate.atTime(9, 0));
            when(mapper.selectAcceptedPunches(31L)).thenReturn(List.of(raw));
            when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(raw);
            when(mapper.selectRemainingWorkConfirmationSources(31L, false))
                    .thenReturn(List.of(returned, attended));
            var today = service.today(101L);
            assertThat(today.state).isEqualTo("REMAINING_WORK_EVIDENCE_REQUIRED");
            if ("PER_WORK_SEGMENT".equals(mode))
                assertThat(today.punchSlots).allSatisfy(slot -> {
                    assertThat(slot.status).isEqualTo("REMAINING_WORK_EVIDENCE_REQUIRED");
                    assertThat(slot.completed).isFalse();
                });
            returned.decision = "ABSENT";
            returned.actualArrivalTime = returned.remainingStart;
            assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMATION_INVALID");
            returned.actualArrivalTime = null;
            assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMED");
        }
    }

    @Test
    void unrelatedOrStaleRemainingWorkConfirmationsDoNotCloseToday()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
        {
            Schedule value = remainingWorkSchedule(mode);
            for (int changed = 0; changed < 6; changed++)
            {
                var confirmation = remainingConfirmation(value, 801L, "ATTENDED");
                switch (changed)
                {
                    case 0 -> confirmation.userId = 99L;
                    case 1 -> confirmation.shopId = 202L;
                    case 2 -> confirmation.scheduleId = 32L;
                    case 3 -> confirmation.businessDate = value.businessDate.minusDays(1);
                    case 4 -> confirmation.remainingStart = confirmation.remainingStart.plusMinutes(1);
                    case 5 -> confirmation.remainingEnd = confirmation.remainingEnd.minusMinutes(1);
                }
                when(mapper.selectRemainingWorkConfirmationSources(31L, false))
                        .thenReturn(List.of(confirmation));
                assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMATION_REQUIRED");
            }
        }
    }

    @Test
    void boundaryRemainingWorkRequiresEachWorkIntervalAndExcludesTheBreak()
    {
        Schedule value = remainingWorkSchedule("SHIFT_BOUNDARY");
        value.standardMinutesSnapshot = 480;
        when(mapper.selectScheduleSegmentSnapshots(31L, false)).thenReturn(List.of(
                scheduleSegment(31L, 1031L, 1, 540, 720),
                scheduleSegment(31L, 1033L, 3, 780, 1080)));
        var spanning = remainingConfirmation(value, 800L, "ATTENDED");
        when(mapper.selectRemainingWorkConfirmationSources(31L, false)).thenReturn(List.of(spanning));
        assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMATION_REQUIRED");
        var morning = remainingConfirmation(value, 801L, "ATTENDED");
        morning.remainingEnd = value.businessDate.atTime(12, 0);
        morning.actualDepartureTime = morning.remainingEnd;
        var afternoon = remainingConfirmation(value, 802L, "ABSENT");
        afternoon.remainingStart = value.businessDate.atTime(13, 0);
        when(mapper.selectRemainingWorkConfirmationSources(31L, false)).thenReturn(List.of(morning));
        assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMATION_REQUIRED");
        when(mapper.selectRemainingWorkConfirmationSources(31L, false)).thenReturn(List.of(morning, afternoon));
        assertThat(service.today(101L).state).isEqualTo("REMAINING_WORK_CONFIRMED");
    }

    @Test
    void aPreviouslyIssuedChallengeCannotReopenReviewedLeaveCoveredSlots()
    {
        for (String mode : List.of("SHIFT_BOUNDARY", "PER_WORK_SEGMENT"))
        {
            Schedule value = remainingWorkSchedule(mode);
            var segments = mapper.selectScheduleSegmentSnapshots(31L, false);
            when(mapper.selectScheduleSegmentSnapshots(31L, true)).thenReturn(segments);
            when(mapper.selectScheduleByIdForUpdate(31L)).thenReturn(value);
            var leaves = mapper.selectApprovedLeaveSegments(9L, 101L,
                    value.businessDate.atTime(9, 0), value.businessDate.atTime(18, 0), false);
            when(mapper.selectApprovedLeaveSegments(9L, 101L,
                    value.businessDate.atTime(9, 0), value.businessDate.atTime(18, 0), true)).thenReturn(leaves);
            when(mapper.selectRemainingWorkConfirmationSources(31L, true))
                    .thenReturn(List.of(remainingConfirmation(value, 801L, "ATTENDED")));
            var token = new com.erp.oa.attendance.domain.AttendanceModels.Challenge();
            token.scheduleId = 31L; token.shopId = 101L; token.businessDate = value.businessDate;
            token.issuedAt = value.businessDate.atTime(20, 0);
            token.punchSlotKey = "PER_WORK_SEGMENT".equals(mode) ? "SEGMENT:1031:IN" : null;
            token.scheduleSegmentSnapshotId = "PER_WORK_SEGMENT".equals(mode) ? 1031L : null;
            when(mapper.selectChallengeByTokenForUpdate("remaining-work-token")).thenReturn(token);
            var command = new com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand();
            command.challengeToken = "remaining-work-token"; command.punchType = "IN";
            command.punchSlotKey = token.punchSlotKey; command.clientCaptureTime = value.businessDate.atTime(20, 1);
            assertThatThrownBy(() -> service.punch(command, null, 101L))
                    .hasMessage("ALL_PUNCH_SLOTS_COMPLETED");
        }
        verify(mapper, org.mockito.Mockito.atLeastOnce()).selectRemainingWorkConfirmationSources(31L, true);
        verify(mapper, never()).insertPunchEvent(any());
    }

    private Schedule remainingWorkSchedule(String mode)
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 20, 1);
        Schedule value = schedule(31L, now.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(18, 0), false);
        value.punchModeSnapshot = mode; value.standardMinutesSnapshot = 540;
        stubRecoveryToday(now, List.of(value));
        when(mapper.selectScheduleById(31L)).thenReturn(value);
        var start = new LeaveSegmentSource(); start.startTime = now.toLocalDate().atTime(9, 0); start.endTime = now.toLocalDate().atTime(10, 0);
        var end = new LeaveSegmentSource(); end.startTime = now.toLocalDate().atTime(17, 0); end.endTime = now.toLocalDate().atTime(18, 0);
        when(mapper.selectApprovedLeaveSegments(9L, 101L, start.startTime, end.endTime, false)).thenReturn(List.of(start, end));
        return value;
    }

    private com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource remainingConfirmation(
            Schedule value, Long id, String decision)
    {
        var confirmation = new com.erp.oa.attendance.domain.AttendanceSettlementModels.RemainingWorkConfirmationSource();
        confirmation.confirmationId = id; confirmation.scheduleId = value.scheduleId;
        confirmation.userId = value.userId; confirmation.shopId = value.shopId; confirmation.businessDate = value.businessDate;
        confirmation.remainingStart = value.businessDate.atTime(10, 0); confirmation.remainingEnd = value.businessDate.atTime(17, 0);
        confirmation.decision = decision;
        if ("ATTENDED".equals(decision))
        {
            confirmation.actualArrivalTime = confirmation.remainingStart;
            confirmation.actualDepartureTime = confirmation.remainingEnd;
        }
        return confirmation;
    }

    private com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource correction(Schedule value, String type, String status)
    {
        var correction = new com.erp.oa.attendance.domain.AttendanceSettlementModels.CorrectionSource();
        correction.correctionRequestId = 801L; correction.scheduleId = value.scheduleId;
        correction.userId = value.userId; correction.shopId = value.shopId; correction.businessDate = value.businessDate;
        correction.correctionType = "MISSING_PUNCH"; correction.targetPunchType = type; correction.status = status;
        correction.requestedPunchTime = value.businessDate.atTime("IN".equals(type) ? 9 : 18, 0);
        if ("PER_WORK_SEGMENT".equals(value.punchModeSnapshot))
        {
            correction.targetScheduleSegmentSnapshotId = 1031L;
            correction.targetPunchSlotKey = "SEGMENT:1031:" + type;
        }
        return correction;
    }

    private PunchEvent accepted(Schedule value, String type, LocalDateTime time)
    {
        PunchEvent event = new PunchEvent(); event.punchEventId = 902L;
        event.scheduleId = value.scheduleId; event.userId = value.userId; event.shopId = value.shopId;
        event.businessDate = value.businessDate; event.punchType = type;
        event.serverPunchTime = time; event.verificationStatus = "ACCEPTED";
        if ("PER_WORK_SEGMENT".equals(value.punchModeSnapshot))
        {
            event.scheduleSegmentSnapshotId = 1031L; event.punchSlotKey = "SEGMENT:1031:" + type;
        }
        return event;
    }

    private void stubRecoveryToday(LocalDateTime now, List<Schedule> schedules)
    {
        when(mapper.selectDatabaseNow()).thenReturn(now);
        when(mapper.selectPublishedScheduleCandidatesForUser(9L,
                now.toLocalDate().minusDays(2), now.toLocalDate().plusDays(1)))
                .thenReturn(schedules);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
    }

    private Schedule schedule(Long id, LocalDate businessDate,
            LocalTime start, LocalTime end, boolean crossDay)
    {
        Schedule value = new Schedule();
        value.scheduleId = id;
        value.userId = 9L;
        value.shopId = 101L;
        value.businessDate = businessDate;
        value.status = "PUBLISHED";
        value.startTimeSnapshot = start;
        value.endTimeSnapshot = end;
        value.crossDaySnapshot = crossDay;
        value.checkInOpenMinutesSnapshot = 120;
        value.checkInCloseMinutesSnapshot = 360;
        value.checkOutOpenMinutesSnapshot = 240;
        value.checkOutCloseMinutesSnapshot = 120;
        when(mapper.selectScheduleSegmentSnapshots(id, false))
                .thenReturn(List.of(scheduleSegment(id, id + 1000L, 1,
                        start.toSecondOfDay() / 60,
                        end.toSecondOfDay() / 60
                                + (crossDay ? 1440 : 0))));
        return value;
    }

    private ShiftSegment shiftSegment(String type, int start, int end,
            boolean paid)
    {
        ShiftSegment value = new ShiftSegment();
        value.segmentType = type;
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        value.paid = paid;
        return value;
    }

    private Site enabledSite(Long siteId, Long shopId)
    {
        Site value = new Site();
        value.siteId = siteId;
        value.shopId = shopId;
        value.siteName = "柏悦门店";
        value.address = "北京市朝阳区建国门外大街2号";
        value.latitude = new BigDecimal("39.9100000");
        value.longitude = new BigDecimal("116.4600000");
        value.coordinateSystem = "GCJ02";
        value.radiusMeters = 200;
        value.maxAccuracyMeters = 100;
        value.status = "ENABLED";
        return value;
    }

    private ScheduleSegmentSnapshot scheduleSegment(Long scheduleId,
            Long snapshotId, int order, int start, int end)
    {
        ScheduleSegmentSnapshot value = new ScheduleSegmentSnapshot();
        value.scheduleSegmentSnapshotId = snapshotId;
        value.scheduleId = scheduleId;
        value.segmentOrder = order;
        value.segmentType = "WORK";
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        value.paid = true;
        return value;
    }
}
