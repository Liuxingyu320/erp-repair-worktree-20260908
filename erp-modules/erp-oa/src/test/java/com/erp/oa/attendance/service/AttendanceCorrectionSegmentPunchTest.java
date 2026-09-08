package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.constant.SecurityConstants;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalAfterCommitTrigger;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalOutboxService;
import com.erp.oa.attendance.correction.AttendanceCorrectionMapper;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.PunchEventRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleSegmentRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionRequests.SaveDraft;
import com.erp.oa.attendance.correction.AttendanceCorrectionService;
import com.erp.oa.attendance.domain.AttendanceSettlementModels.LeaveSegmentSource;
import com.erp.oa.service.BusinessFeatureGate;
import com.erp.system.api.model.LoginUser;

class AttendanceCorrectionSegmentPunchTest
{
    private AttendanceCorrectionMapper mapper;
    private AttendanceCorrectionService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("employee-7");
        mapper = mock(AttendanceCorrectionMapper.class);
        ShopScopeService shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        BusinessFeatureGate gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        service = new AttendanceCorrectionService(mapper,
                mock(AttendanceCorrectionApprovalOutboxService.class),
                mock(AttendanceCorrectionApprovalAfterCommitTrigger.class),
                shopScope, gate);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("员工七");
        when(mapper.selectScheduleSegmentRefs(22L)).thenReturn(List.of(
                segment(91L, 1, "WORK", 480, 720)));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void perWorkSegmentDraftPersistsBothSlotIdentifiers()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(morning);
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(morning));
        when(mapper.countAcceptedPunchEventsForSlot(22L, 7L,
                "SEGMENT:91:IN")).thenReturn(0);
        when(mapper.countActiveCorrectionForSlot(7L, 22L,
                "SEGMENT:91:IN", null)).thenReturn(0);

        AtomicReference<CorrectionRequest> saved = new AtomicReference<>();
        doAnswer(invocation -> {
            CorrectionRequest row = invocation.getArgument(0);
            row.correctionRequestId = 501L;
            saved.set(row);
            return 1;
        }).when(mapper).insertCorrectionRequest(any(CorrectionRequest.class));
        when(mapper.selectCorrectionRequestById(501L))
                .thenAnswer(invocation -> saved.get());

        CorrectionRequest result = service.saveDraft(
                missingDraft(91L, "SEGMENT:91:IN",
                        LocalDateTime.parse("2026-08-20T08:00")), 101L);

        assertThat(result.targetPunchType).isEqualTo("IN");
        assertThat(result.targetScheduleSegmentSnapshotId).isEqualTo(91L);
        assertThat(result.targetPunchSlotKey).isEqualTo("SEGMENT:91:IN");
        assertThat(result.targetSegmentLabelSnapshot).isEqualTo("第1工作段");
        verify(mapper).selectScheduleRefForUpdate(22L);
    }

    @Test
    void perWorkSegmentRejectsMissingOrMismatchedSlotIdentity()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));

        SaveDraft missing = missingDraft(null, null,
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(missing, 101L))
                .hasMessageContaining("CORRECTION_PUNCH_SLOT_REQUIRED");

        ScheduleSegmentRef segment = segment(91L, 1, "WORK", 480, 720);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment);
        SaveDraft mismatched = missingDraft(91L, "SEGMENT:91:OUT",
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(mismatched, 101L))
                .hasMessageContaining("CORRECTION_PUNCH_SLOT_MISMATCH");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void perWorkSegmentRejectsExistingPunchAndDuplicateRequestPerSlot()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        ScheduleSegmentRef segment = segment(91L, 1, "WORK", 480, 720);
        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment);
        SaveDraft body = missingDraft(91L, "SEGMENT:91:IN",
                LocalDateTime.parse("2026-08-20T08:00"));

        when(mapper.countAcceptedPunchEventsForSlot(22L, 7L,
                "SEGMENT:91:IN")).thenReturn(1);
        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining("CORRECTION_PUNCH_ALREADY_EXISTS");

        when(mapper.countAcceptedPunchEventsForSlot(22L, 7L,
                "SEGMENT:91:IN")).thenReturn(0);
        when(mapper.countActiveCorrectionForSlot(7L, 22L,
                "SEGMENT:91:IN", null)).thenReturn(1);
        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining("CORRECTION_ALREADY_EXISTS");
    }

    @Test
    void originalEventMustBelongToTheSelectedWorkSegment()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment(91L, 1, "WORK", 480, 720));
        PunchEventRef event = new PunchEventRef();
        event.punchEventId = 700L;
        event.scheduleId = 22L;
        event.userId = 7L;
        event.shopId = 101L;
        event.businessDate = LocalDate.parse("2026-08-20");
        event.punchType = "IN";
        event.scheduleSegmentSnapshotId = 92L;
        event.punchSlotKey = "SEGMENT:92:IN";
        event.serverPunchTime = LocalDateTime.parse("2026-08-20T08:05");
        event.verificationStatus = "ACCEPTED";
        when(mapper.selectPunchEventRef(700L)).thenReturn(event);

        SaveDraft body = missingDraft(91L, "SEGMENT:91:IN",
                LocalDateTime.parse("2026-08-20T08:10"));
        body.correctionType = "WRONG_TIME";
        body.originalPunchEventId = 700L;

        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining(
                        "CORRECTION_ORIGINAL_EVENT_SLOT_INVALID");
    }

    @Test
    void shiftBoundaryKeepsLegacyInOutCorrectionContract()
    {
        ScheduleRef schedule = schedule("SHIFT_BOUNDARY");
        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.countAcceptedPunchEvents(22L, 7L, "IN"))
                .thenReturn(1);

        SaveDraft body = missingDraft(null, null,
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining("CORRECTION_PUNCH_ALREADY_EXISTS");
        verify(mapper, never()).selectScheduleSegmentRef(any(), any());
    }

    @Test
    void eligibleSchedulesExposeFourServerDerivedSlots()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        ScheduleSegmentRef breakSegment = segment(92L, 2, "BREAK", 720,
                840);
        ScheduleSegmentRef afternoon = segment(93L, 3, "WORK", 840,
                1080);
        PunchEventRef punched = new PunchEventRef();
        punched.punchEventId = 700L;
        punched.scheduleId = 22L;
        punched.userId = 7L;
        punched.shopId = 101L;
        punched.businessDate = LocalDate.parse("2026-08-20");
        punched.punchType = "IN";
        punched.scheduleSegmentSnapshotId = 91L;
        punched.punchSlotKey = "SEGMENT:91:IN";
        punched.serverPunchTime = LocalDateTime.parse(
                "2026-08-20T08:01");
        punched.verificationStatus = "ACCEPTED";
        when(mapper.selectOwnedScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20")))
                .thenReturn(List.of(schedule));
        when(mapper.selectScheduleSegmentRefs(22L)).thenReturn(List.of(
                morning, breakSegment, afternoon));
        when(mapper.selectSchedulePunchEvents(22L, 7L))
                .thenReturn(List.of(punched));

        List<ScheduleRef> result = service.eligibleSchedules(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20"), 101L);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.punchModeSnapshot)
                    .isEqualTo("PER_WORK_SEGMENT");
            assertThat(row.punchSlots).hasSize(4);
            assertThat(row.punchSlots).extracting(slot -> slot.segmentLabel)
                    .containsExactly("上午", "上午", "下午", "下午");
            assertThat(row.punchSlots.get(0).completed).isTrue();
            assertThat(row.punchSlots.get(0).eligibleForMissingPunch)
                    .isFalse();
            assertThat(row.punchSlots.get(2).startAt)
                    .isEqualTo(LocalDateTime.parse("2026-08-20T14:00"));
        });
    }

    @Test
    void eligibleSlotsExposePendingCorrectionAndApprovedLeaveAndIgnoreForgedEvent()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        ScheduleSegmentRef afternoon = segment(93L, 3, "WORK", 840, 1080);
        PunchEventRef forged = new PunchEventRef();
        forged.punchEventId = 700L;
        forged.scheduleId = 22L;
        forged.userId = 7L;
        forged.shopId = 999L;
        forged.businessDate = LocalDate.parse("2026-08-20");
        forged.punchType = "IN";
        forged.scheduleSegmentSnapshotId = 91L;
        forged.punchSlotKey = "SEGMENT:91:IN";
        forged.serverPunchTime = LocalDateTime.parse(
                "2026-08-20T08:01");
        forged.verificationStatus = "ACCEPTED";
        when(mapper.selectOwnedScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20")))
                .thenReturn(List.of(schedule));
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(morning, afternoon));
        when(mapper.selectSchedulePunchEvents(22L, 7L))
                .thenReturn(List.of(forged));
        when(mapper.countActiveCorrectionForSlot(7L, 22L,
                "SEGMENT:91:IN", null)).thenReturn(1);
        when(mapper.selectApprovedLeaveSegmentsForSchedule(22L, 7L, 101L))
                .thenReturn(List.of(
                        leave("2026-08-20T08:00", "2026-08-20T10:00"),
                        leave("2026-08-20T10:00", "2026-08-20T12:00")));

        List<ScheduleRef> result = service.eligibleSchedules(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20"), 101L);

        assertThat(result.get(0).punchSlots.get(0)).satisfies(slot -> {
            assertThat(slot.completed).isFalse();
            assertThat(slot.correctionPending).isTrue();
            assertThat(slot.coveredByApprovedLeave).isTrue();
            assertThat(slot.status).isEqualTo("CORRECTION_PENDING");
            assertThat(slot.eligibleForMissingPunch).isFalse();
        });
        assertThat(result.get(0).punchSlots.get(1)).satisfies(slot -> {
            assertThat(slot.status).isEqualTo("APPROVED_LEAVE");
            assertThat(slot.coveredByApprovedLeave).isTrue();
            assertThat(slot.eligibleForMissingPunch).isFalse();
        });
    }

    @Test
    void leaveCoveredFacesWithRemainingWorkCannotReopenOriginalPunchSlots()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        when(mapper.selectOwnedScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20")))
                .thenReturn(List.of(schedule));
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(morning));
        when(mapper.selectApprovedLeaveSegmentsForSchedule(22L, 7L, 101L))
                .thenReturn(List.of(
                        leave("2026-08-20T08:00", "2026-08-20T10:00"),
                        leave("2026-08-20T11:00", "2026-08-20T12:00")));

        List<ScheduleRef> result = service.eligibleSchedules(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20"), 101L);

        assertThat(result.get(0).punchSlots).allSatisfy(slot -> {
            assertThat(slot.status).isEqualTo("REMAINING_WORK_PENDING");
            assertThat(slot.requiresRemainingWorkConfirmation).isTrue();
            assertThat(slot.coveredByApprovedLeave).isTrue();
            assertThat(slot.eligibleForMissingPunch).isFalse();
        });

        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(morning);
        SaveDraft draft = missingDraft(91L, "SEGMENT:91:IN",
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(draft, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_REQUIRES_REMAINING_WORK_CONFIRMATION");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void correctionSlotsAndDraftValidationUseClippedMidpointWindows()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        schedule.checkInOpenMinutesSnapshot = 180;
        schedule.checkOutCloseMinutesSnapshot = 180;
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        ScheduleSegmentRef afternoon = segment(93L, 2, "WORK", 840, 1080);
        when(mapper.selectOwnedScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20")))
                .thenReturn(List.of(schedule));
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(morning, afternoon));

        List<ScheduleRef> result = service.eligibleSchedules(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-20"), 101L);
        assertThat(result.get(0).punchSlots.get(1).closesAt)
                .isEqualTo(LocalDateTime.parse("2026-08-20T13:00"));
        assertThat(result.get(0).punchSlots.get(2).opensAt)
                .isEqualTo(LocalDateTime.parse("2026-08-20T13:00"));

        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(morning);
        SaveDraft outside = missingDraft(91L, "SEGMENT:91:OUT",
                LocalDateTime.parse("2026-08-20T13:30"));
        outside.targetPunchType = "OUT";
        assertThatThrownBy(() -> service.saveDraft(outside, 101L))
                .hasMessageContaining(
                        "CORRECTION_TIME_OUTSIDE_PUNCH_WINDOW");
    }

    @Test
    void otherCorrectionCannotEnterSettlement()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("SHIFT_BOUNDARY"));
        SaveDraft body = missingDraft(null, null,
                LocalDateTime.parse("2026-08-20T08:00"));
        body.correctionType = "OTHER";

        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining("CORRECTION_OTHER_NOT_SUPPORTED");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void wrongTypeRejectsAnAlreadyOccupiedTargetForSegmentAndBoundaryModes()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment(91L, 1, "WORK", 480, 720));
        when(mapper.countAcceptedPunchEventsForSlot(22L, 7L,
                "SEGMENT:91:OUT")).thenReturn(1);
        SaveDraft segmented = missingDraft(91L, "SEGMENT:91:OUT",
                LocalDateTime.parse("2026-08-20T12:00"));
        segmented.correctionType = "WRONG_TYPE";
        segmented.targetPunchType = "OUT";
        segmented.originalPunchEventId = 700L;

        assertThatThrownBy(() -> service.saveDraft(segmented, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_PUNCH_ALREADY_EXISTS");

        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("SHIFT_BOUNDARY"));
        when(mapper.countAcceptedPunchEvents(22L, 7L, "OUT"))
                .thenReturn(1);
        SaveDraft boundary = missingDraft(null, null,
                LocalDateTime.parse("2026-08-20T18:00"));
        boundary.correctionType = "WRONG_TYPE";
        boundary.targetPunchType = "OUT";
        boundary.originalPunchEventId = 701L;

        assertThatThrownBy(() -> service.saveDraft(boundary, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_PUNCH_ALREADY_EXISTS");
        verify(mapper, never()).selectPunchEventRef(any());
    }

    @Test
    void activeCorrectionOwnsItsOriginalEventExclusively()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment(91L, 1, "WORK", 480, 720));
        PunchEventRef event = validEvent(700L, 91L, "IN",
                "SEGMENT:91:IN", "2026-08-20T08:05");
        when(mapper.selectPunchEventRef(700L)).thenReturn(event);
        when(mapper.countActiveCorrectionForOriginalEvent(7L, 700L,
                null)).thenReturn(1);
        SaveDraft body = missingDraft(91L, "SEGMENT:91:IN",
                LocalDateTime.parse("2026-08-20T08:10"));
        body.correctionType = "WRONG_TIME";
        body.originalPunchEventId = 700L;

        assertThatThrownBy(() -> service.saveDraft(body, 101L))
                .hasMessageContaining(
                        "CORRECTION_ORIGINAL_EVENT_ALREADY_CORRECTED");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void approvedLeaveCoverageRejectsSegmentAndBoundaryMissingPunches()
    {
        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment(91L, 1, "WORK", 480, 720));
        when(mapper.selectApprovedLeaveSegmentsForSchedule(22L, 7L, 101L))
                .thenReturn(List.of(
                        leave("2026-08-20T08:00", "2026-08-20T10:00"),
                        leave("2026-08-20T10:00", "2026-08-20T12:00")));
        SaveDraft segmented = missingDraft(91L, "SEGMENT:91:IN",
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(segmented, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_COVERED_BY_APPROVED_LEAVE");

        when(mapper.selectScheduleRefForUpdate(22L))
                .thenReturn(schedule("SHIFT_BOUNDARY"));
        when(mapper.selectApprovedLeaveSegmentsForSchedule(22L, 7L, 101L))
                .thenReturn(List.of(
                        leave("2026-08-20T08:00", "2026-08-20T18:00")));
        SaveDraft boundary = missingDraft(null, null,
                LocalDateTime.parse("2026-08-20T08:00"));
        assertThatThrownBy(() -> service.saveDraft(boundary, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_COVERED_BY_APPROVED_LEAVE");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void submitRechecksApprovedLeaveWhileHoldingScheduleLock()
    {
        ScheduleRef schedule = schedule("PER_WORK_SEGMENT");
        CorrectionRequest request = new CorrectionRequest();
        request.correctionRequestId = 501L;
        request.userId = 7L;
        request.shopId = 101L;
        request.scheduleId = 22L;
        request.businessDate = LocalDate.parse("2026-08-20");
        request.correctionType = "MISSING_PUNCH";
        request.targetPunchType = "IN";
        request.targetScheduleSegmentSnapshotId = 91L;
        request.targetPunchSlotKey = "SEGMENT:91:IN";
        request.requestedPunchTime = LocalDateTime.parse(
                "2026-08-20T08:00");
        request.reason = "忘记打卡";
        request.status = "DRAFT";
        request.rowVersion = 0L;
        when(mapper.selectCorrectionRequestById(501L)).thenReturn(request);
        when(mapper.selectCorrectionRequestByIdForUpdate(501L))
                .thenReturn(request);
        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRef(22L, 91L))
                .thenReturn(segment(91L, 1, "WORK", 480, 720));
        when(mapper.selectApprovedLeaveSegmentsForSchedule(22L, 7L, 101L))
                .thenReturn(List.of(
                        leave("2026-08-20T08:00", "2026-08-20T12:00")));

        assertThatThrownBy(() -> service.submit(501L, 0L, 101L))
                .hasMessageContaining(
                        "CORRECTION_TARGET_COVERED_BY_APPROVED_LEAVE");
        verify(mapper, never()).markCorrectionSubmitting(any(), any(), any(),
                any(), any());
    }

    @Test
    void eligiblePunchEventsAreStrictlyScopedAndExcludeOccupiedEvidence()
    {
        when(mapper.selectScheduleRef(22L))
                .thenReturn(schedule("PER_WORK_SEGMENT"));
        ScheduleSegmentRef morning = segment(91L, 1, "WORK", 480, 720);
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(morning));
        PunchEventRef eligible = validEvent(700L, 91L, "IN",
                "SEGMENT:91:IN", "2026-08-20T08:05");
        PunchEventRef occupied = validEvent(701L, 91L, "OUT",
                "SEGMENT:91:OUT", "2026-08-20T12:00");
        PunchEventRef wrongShop = validEvent(702L, 91L, "IN",
                "SEGMENT:91:IN", "2026-08-20T08:06");
        wrongShop.shopId = 999L;
        PunchEventRef wrongSlot = validEvent(703L, 91L, "IN",
                "SEGMENT:93:IN", "2026-08-20T08:07");
        when(mapper.selectSchedulePunchEvents(22L, 7L)).thenReturn(List.of(
                eligible, occupied, wrongShop, wrongSlot));
        when(mapper.countActiveCorrectionForOriginalEvent(7L, 701L, null))
                .thenReturn(1);

        assertThat(service.eligiblePunchEvents(22L, 101L))
                .extracting(event -> event.punchEventId)
                .containsExactly(700L);
    }

    @Test
    void approverCanReadTodoDetailWithoutTeamListPermission()
    {
        CorrectionRequest request = new CorrectionRequest();
        request.correctionRequestId = 501L;
        request.userId = 7L;
        request.shopId = 101L;
        when(mapper.selectCorrectionRequestById(501L)).thenReturn(request);
        SecurityContextHolder.setUserId("9");
        LoginUser login = new LoginUser();
        login.setPermissions(Set.of("oa:attendance:correction:approve"));
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, login);

        assertThat(service.detail(501L, 101L)).isSameAs(request);
    }

    private SaveDraft missingDraft(Long segmentId, String slotKey,
            LocalDateTime requestedTime)
    {
        SaveDraft body = new SaveDraft();
        body.scheduleId = 22L;
        body.correctionType = "MISSING_PUNCH";
        body.targetPunchType = "IN";
        body.targetScheduleSegmentSnapshotId = segmentId;
        body.targetPunchSlotKey = slotKey;
        body.requestedPunchTime = requestedTime;
        body.reason = "忘记打卡";
        return body;
    }

    private ScheduleRef schedule(String punchMode)
    {
        ScheduleRef value = new ScheduleRef();
        value.scheduleId = 22L;
        value.userId = 7L;
        value.userName = "员工七";
        value.shopId = 101L;
        value.businessDate = LocalDate.parse("2026-08-20");
        value.status = "PUBLISHED";
        value.shiftNameSnapshot = "分段班";
        value.punchModeSnapshot = punchMode;
        value.startTimeSnapshot = LocalTime.of(8, 0);
        value.endTimeSnapshot = LocalTime.of(18, 0);
        value.crossDaySnapshot = false;
        value.checkInOpenMinutesSnapshot = 60;
        value.checkInCloseMinutesSnapshot = 60;
        value.checkOutOpenMinutesSnapshot = 60;
        value.checkOutCloseMinutesSnapshot = 60;
        return value;
    }

    private ScheduleSegmentRef segment(Long id, int order, String type,
            int start, int end)
    {
        ScheduleSegmentRef value = new ScheduleSegmentRef();
        value.scheduleSegmentSnapshotId = id;
        value.scheduleId = 22L;
        value.segmentOrder = order;
        value.segmentType = type;
        value.startMinuteOffset = start;
        value.endMinuteOffset = end;
        return value;
    }

    private LeaveSegmentSource leave(String start, String end)
    {
        LeaveSegmentSource value = new LeaveSegmentSource();
        value.startTime = LocalDateTime.parse(start);
        value.endTime = LocalDateTime.parse(end);
        return value;
    }

    private PunchEventRef validEvent(Long id, Long segmentId,
            String punchType, String slotKey, String time)
    {
        PunchEventRef value = new PunchEventRef();
        value.punchEventId = id;
        value.scheduleId = 22L;
        value.userId = 7L;
        value.shopId = 101L;
        value.businessDate = LocalDate.parse("2026-08-20");
        value.punchType = punchType;
        value.scheduleSegmentSnapshotId = segmentId;
        value.punchSlotKey = slotKey;
        value.serverPunchTime = LocalDateTime.parse(time);
        value.verificationStatus = "ACCEPTED";
        return value;
    }
}
