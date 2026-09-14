package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalAfterCommitTrigger;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalOutboxService;
import com.erp.oa.attendance.correction.AttendanceCorrectionMapper;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ApprovalOutbox;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleSegmentRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionService;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalAfterCommitTrigger;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalOutboxService;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveSegment;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.AttendanceLeaveService;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceRequestSettlementInvalidationTest
{
    private BusinessFeatureGate gate;
    private ShopScopeService shopScope;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId("7");
        SecurityContextHolder.setUserName("employee-7");
        gate = mock(BusinessFeatureGate.class);
        doNothing().when(gate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
        shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(101L)).thenReturn(101L);
        when(shopScope.resolveShopDeptName(101L)).thenReturn("一号店");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void autoApprovedLeaveInvalidatesSettledDaysBeforeReturning()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        LeaveRequest request = leaveRequest();
        LeaveType type = leaveType(false);
        when(mapper.selectLeaveRequestByIdForUpdate(12L))
                .thenReturn(request);
        when(mapper.selectLeaveRequestById(12L)).thenReturn(request);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("员工七");
        when(mapper.selectLeaveTypeById(3L)).thenReturn(type);
        when(mapper.selectLeaveTypeForUpdate(3L)).thenReturn(type);
        when(mapper.countOverlappingLeave(eq(7L), eq(12L), any(), any()))
                .thenReturn(0);
        when(mapper.countLeaveAttachments(12L)).thenReturn(0);
        com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef
                leaveSchedule = new com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef();
        leaveSchedule.scheduleId = 22L;
        leaveSchedule.shopId = 101L;
        leaveSchedule.businessDate = LocalDate.parse("2026-08-20");
        leaveSchedule.scheduleStart = LocalDateTime.parse(
                "2026-08-20T09:00:00");
        leaveSchedule.scheduleEnd = LocalDateTime.parse(
                "2026-08-20T18:00:00");
        when(mapper.calculateScheduledWorkMinutes(7L, 101L,
                request.startTime, request.endTime)).thenReturn(540);
        when(mapper.countOverlappingPublishedSchedules(7L, 101L,
                request.startTime, request.endTime)).thenReturn(1);
        when(mapper.selectScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-20"),
                LocalDate.parse("2026-08-20")))
                .thenReturn(List.of(leaveSchedule));
        when(mapper.insertLeaveSegment(any())).thenReturn(1);
        when(mapper.markLeaveAutoApproved(12L, "DRAFT", 1L,
                "employee-7")).thenReturn(1);
        when(mapper.selectLeaveSegments(12L)).thenReturn(List.of());
        when(mapper.selectLeaveAttachments(12L)).thenReturn(List.of());

        AttendanceLeaveService service = new AttendanceLeaveService(mapper,
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shopScope, gate, legacyQuota());

        service.submit(12L, 1L, 101L);

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteLeaveSegments(12L);
        order.verify(mapper).selectScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-20"),
                LocalDate.parse("2026-08-20"));
        order.verify(mapper).insertLeaveSegment(any());
        order.verify(mapper).markLeaveAutoApproved(12L, "DRAFT", 1L,
                "employee-7");
        order.verify(mapper).invalidateDayResultsForLeaveRequest(12L,
                "employee-7");
    }

    @Test
    void overnightLeaveSegmentsKeepPriorBusinessScheduleAuditLink()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        LeaveRequest request = leaveRequest();
        request.startTime = LocalDateTime.parse("2026-08-20T20:00:00");
        request.endTime = LocalDateTime.parse("2026-08-21T04:00:00");
        LeaveType type = leaveType(false);
        when(mapper.selectLeaveRequestByIdForUpdate(12L))
                .thenReturn(request);
        when(mapper.selectLeaveRequestById(12L)).thenReturn(request);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("员工七");
        when(mapper.selectLeaveTypeById(3L)).thenReturn(type);
        when(mapper.selectLeaveTypeForUpdate(3L)).thenReturn(type);
        when(mapper.countOverlappingLeave(eq(7L), eq(12L), any(), any()))
                .thenReturn(0);
        when(mapper.calculateScheduledWorkMinutes(7L, 101L,
                request.startTime, request.endTime)).thenReturn(480);
        when(mapper.countOverlappingPublishedSchedules(7L, 101L,
                request.startTime, request.endTime)).thenReturn(1);
        when(mapper.countLeaveAttachments(12L)).thenReturn(0);

        com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef night =
                new com.erp.oa.attendance.leave.AttendanceLeaveModels.ScheduleRef();
        night.scheduleId = 22L;
        night.shopId = 101L;
        night.businessDate = LocalDate.parse("2026-08-20");
        night.scheduleStart = request.startTime;
        night.scheduleEnd = request.endTime;
        when(mapper.selectScheduleRefs(7L, 101L,
                LocalDate.parse("2026-08-20"),
                LocalDate.parse("2026-08-21")))
                .thenReturn(List.of(night));
        when(mapper.insertLeaveSegment(any())).thenReturn(1);
        when(mapper.markLeaveAutoApproved(12L, "DRAFT", 1L,
                "employee-7")).thenReturn(1);
        when(mapper.selectLeaveSegments(12L)).thenReturn(List.of());
        when(mapper.selectLeaveAttachments(12L)).thenReturn(List.of());

        AttendanceLeaveService service = new AttendanceLeaveService(mapper,
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shopScope, gate, legacyQuota());

        service.submit(12L, 1L, 101L);

        ArgumentCaptor<LeaveSegment> segments = ArgumentCaptor.forClass(
                LeaveSegment.class);
        verify(mapper, times(2)).insertLeaveSegment(segments.capture());
        assertThat(segments.getAllValues())
                .extracting(value -> value.scheduleId)
                .containsExactly(22L, 22L);
        assertThat(segments.getAllValues())
                .extracting(value -> value.businessDate)
                .containsExactly(LocalDate.parse("2026-08-20"),
                        LocalDate.parse("2026-08-20"));
    }

    @Test
    void leaveSubmitRejectsIntervalsWithoutPublishedWork()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        LeaveRequest request = leaveRequest();
        LeaveType type = leaveType(false);
        when(mapper.selectLeaveRequestByIdForUpdate(12L))
                .thenReturn(request);
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("员工七");
        when(mapper.selectLeaveTypeById(3L)).thenReturn(type);
        when(mapper.selectLeaveTypeForUpdate(3L)).thenReturn(type);
        when(mapper.countOverlappingLeave(eq(7L), eq(12L), any(), any()))
                .thenReturn(0);
        when(mapper.calculateScheduledWorkMinutes(7L, 101L,
                request.startTime, request.endTime)).thenReturn(0);
        when(mapper.countOverlappingPublishedSchedules(7L, 101L,
                request.startTime, request.endTime)).thenReturn(1);

        AttendanceLeaveService service = new AttendanceLeaveService(mapper,
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shopScope, gate, legacyQuota());

        assertThatThrownBy(() -> service.submit(12L, 1L, 101L))
                .hasMessageContaining("LEAVE_NO_SCHEDULED_WORK");
        verify(mapper, times(0)).insertLeaveSegment(any());
    }

    @Test
    void submittedCorrectionInvalidatesSettledDayBeforeApprovalOutbox()
    {
        AttendanceCorrectionMapper mapper = mock(
                AttendanceCorrectionMapper.class);
        AttendanceCorrectionApprovalOutboxService outboxService = mock(
                AttendanceCorrectionApprovalOutboxService.class);
        CorrectionRequest request = correctionRequest();
        ScheduleRef schedule = schedule();
        when(mapper.selectCorrectionRequestByIdForUpdate(13L))
                .thenReturn(request);
        when(mapper.selectCorrectionRequestById(13L)).thenReturn(request);
        when(mapper.selectScheduleRefForUpdate(22L)).thenReturn(schedule);
        when(mapper.selectScheduleSegmentRefs(22L))
                .thenReturn(List.of(workSegment()));
        when(mapper.selectActiveEmployeeNameInShop(7L, 101L))
                .thenReturn("员工七");
        when(mapper.countAcceptedPunchEvents(22L, 7L, "IN"))
                .thenReturn(0);
        when(mapper.countActiveCorrection(7L, 22L, "IN", 13L))
                .thenReturn(0);
        when(mapper.markCorrectionSubmitting(13L, "DRAFT", 1L, 1,
                "employee-7")).thenReturn(1);
        ApprovalOutbox outbox = new ApprovalOutbox();
        outbox.outboxId = 88L;
        when(outboxService.enqueue(eq(request), any(), eq("employee-7")))
                .thenReturn(outbox);

        AttendanceCorrectionService service = new AttendanceCorrectionService(
                mapper, outboxService,
                mock(AttendanceCorrectionApprovalAfterCommitTrigger.class),
                shopScope, gate);

        service.submit(13L, 1L, 101L);

        InOrder order = inOrder(mapper, outboxService);
        order.verify(mapper).selectScheduleRefForUpdate(22L);
        order.verify(mapper).selectCorrectionRequestByIdForUpdate(13L);
        order.verify(mapper).markCorrectionSubmitting(13L, "DRAFT", 1L,
                1, "employee-7");
        order.verify(mapper).invalidateDayResultForCorrection(13L,
                "employee-7");
        order.verify(outboxService).enqueue(eq(request), any(),
                eq("employee-7"));
    }

    private LeaveRequest leaveRequest()
    {
        LeaveRequest value = new LeaveRequest();
        value.leaveRequestId = 12L;
        value.userId = 7L;
        value.userName = "员工七";
        value.shopId = 101L;
        value.leaveTypeId = 3L;
        value.startTime = LocalDateTime.parse("2026-08-20T09:00:00");
        value.endTime = LocalDateTime.parse("2026-08-20T18:00:00");
        value.reason = "身体不适";
        value.status = "DRAFT";
        value.businessRound = 0;
        value.rowVersion = 1L;
        return value;
    }

    private LeaveType leaveType(boolean approvalRequired)
    {
        LeaveType value = new LeaveType();
        value.leaveTypeId = 3L;
        value.status = "ENABLED";
        value.payPolicy = "UNPAID";
        value.paidRatio = BigDecimal.ZERO;
        value.minMinutes = 30;
        value.stepMinutes = 30;
        value.maxMinutesPerRequest = 1440;
        value.allowCrossDay = true;
        value.attachmentRequired = false;
        value.approvalRequired = approvalRequired;
        return value;
    }

    private CorrectionRequest correctionRequest()
    {
        CorrectionRequest value = new CorrectionRequest();
        value.correctionRequestId = 13L;
        value.userId = 7L;
        value.userName = "员工七";
        value.shopId = 101L;
        value.scheduleId = 22L;
        value.businessDate = LocalDate.parse("2026-08-20");
        value.correctionType = "MISSING_PUNCH";
        value.targetPunchType = "IN";
        value.requestedPunchTime = LocalDateTime.parse(
                "2026-08-20T08:30:00");
        value.reason = "忘记打卡";
        value.status = "DRAFT";
        value.businessRound = 0;
        value.rowVersion = 1L;
        return value;
    }

    private ScheduleRef schedule()
    {
        ScheduleRef value = new ScheduleRef();
        value.scheduleId = 22L;
        value.userId = 7L;
        value.userName = "员工七";
        value.shopId = 101L;
        value.businessDate = LocalDate.parse("2026-08-20");
        value.status = "PUBLISHED";
        value.startTimeSnapshot = LocalTime.of(9, 0);
        value.endTimeSnapshot = LocalTime.of(18, 0);
        value.crossDaySnapshot = false;
        value.checkInOpenMinutesSnapshot = 120;
        value.checkInCloseMinutesSnapshot = 60;
        value.checkOutOpenMinutesSnapshot = 60;
        value.checkOutCloseMinutesSnapshot = 120;
        return value;
    }

    private ScheduleSegmentRef workSegment()
    {
        ScheduleSegmentRef value = new ScheduleSegmentRef();
        value.scheduleSegmentSnapshotId = 91L;
        value.scheduleId = 22L;
        value.segmentOrder = 1;
        value.segmentType = "WORK";
        value.startMinuteOffset = 540;
        value.endMinuteOffset = 1080;
        return value;
    }

    private static com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaService legacyQuota()
    {
        return org.mockito.Mockito.mock(com.erp.oa.attendance.leave.balance.AttendanceLeaveQuotaService.class, invocation -> {
            String method=invocation.getMethod().getName();
            if("hydrate".equals(method) || "copyPolicy".equals(method))return invocation.getArgument(0);
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
