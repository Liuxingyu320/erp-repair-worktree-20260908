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
        shopScope = mock(ShopScopeService.class);
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
                now.toLocalDate(), now.toLocalDate().minusDays(1)))
                .thenReturn(List.of(today, yesterday));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today().schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today().allowedPunchType).isEqualTo("IN");
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
                now.toLocalDate(), now.toLocalDate().minusDays(1)))
                .thenReturn(List.of(today, yesterday));
        when(mapper.selectFirstAcceptedPunch(31L, "IN")).thenReturn(in);
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today().schedule.scheduleId).isEqualTo(31L);
        assertThat(service.today().allowedPunchType).isEqualTo("OUT");
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
                now.toLocalDate(), now.toLocalDate().minusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectLatestPunch(31L)).thenReturn(latest);
        when(mapper.selectEvidenceIdForPunchInScope(41L, 31L, 9L))
                .thenReturn(77L);

        var context = service.today();

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
                now.toLocalDate(), now.toLocalDate().minusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);

        assertThat(service.today().latestEvidenceId).isNull();
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
                now.toLocalDate(), now.toLocalDate().minusDays(1)))
                .thenReturn(List.of(today));
        when(mapper.countActiveEmployeeInShop(9L, 101L)).thenReturn(1);
        when(mapper.selectScheduleSegmentSnapshots(31L, false))
                .thenReturn(List.of(work));
        when(mapper.selectApprovedLeaveSegments(9L, 101L,
                LocalDateTime.of(2026, 8, 20, 8, 0),
                LocalDateTime.of(2026, 8, 20, 20, 0), false))
                .thenReturn(List.of(leave));

        assertThat(service.today().state).isEqualTo("ON_LEAVE");
        assertThat(service.today().canPunch).isFalse();
        when(mapper.selectScheduleById(31L)).thenReturn(today);
        ChallengeCreate challenge = new ChallengeCreate();
        challenge.scheduleId = 31L;
        challenge.punchType = "IN";
        assertThatThrownBy(() -> service.issueChallenge(challenge))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PUNCH_BLOCKED_BY_APPROVED_LEAVE");
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
