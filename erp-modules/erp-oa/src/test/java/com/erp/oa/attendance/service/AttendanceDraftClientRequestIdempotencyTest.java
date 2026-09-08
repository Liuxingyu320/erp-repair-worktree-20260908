package com.erp.oa.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.erp.approval.api.RemoteApprovalService;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.common.core.exception.ServiceException;
import com.erp.common.security.shop.ShopScopeService;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalAfterCommitTrigger;
import com.erp.oa.attendance.correction.AttendanceCorrectionApprovalOutboxService;
import com.erp.oa.attendance.correction.AttendanceCorrectionMapper;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ScheduleSegmentRef;
import com.erp.oa.attendance.correction.AttendanceCorrectionRequests;
import com.erp.oa.attendance.correction.AttendanceCorrectionService;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalAfterCommitTrigger;
import com.erp.oa.attendance.leave.AttendanceLeaveApprovalOutboxService;
import com.erp.oa.attendance.leave.AttendanceLeaveAttachmentStorage;
import com.erp.oa.attendance.leave.AttendanceLeaveMapper;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveType;
import com.erp.oa.attendance.leave.AttendanceLeaveRequests;
import com.erp.oa.attendance.leave.AttendanceLeaveService;
import com.erp.oa.attendance.support.AttendanceClientRequestSupport;
import com.erp.oa.attendance.support.AttendanceRuleEngine;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceDraftClientRequestIdempotencyTest
{
    private static final Long USER_ID = 9L;
    private static final Long SHOP_ID = 101L;
    private ShopScopeService shopScope;
    private BusinessFeatureGate featureGate;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.setUserId(String.valueOf(USER_ID));
        SecurityContextHolder.setUserName("tester");
        shopScope = mock(ShopScopeService.class);
        when(shopScope.resolveRequiredShopDept(SHOP_ID)).thenReturn(SHOP_ID);
        featureGate = mock(BusinessFeatureGate.class);
        doNothing().when(featureGate).requireEnabled(
                BusinessFeatureGate.ATTENDANCE_V2);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    void leaveSameKeyAndSamePayloadReturnsTheOriginalWithoutRevalidation()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        AttendanceLeaveRequests.SaveDraft body = leaveBody();
        LeaveRequest original = leaveReplay(body);
        when(mapper.selectLeaveRequestByClientRequestId(USER_ID, SHOP_ID,
                body.clientRequestId)).thenReturn(original);

        LeaveRequest result = leaveService(mapper).saveDraft(body, SHOP_ID);

        assertThat(result).isSameAs(original);
        verify(mapper, never()).selectLeaveTypeById(any());
        verify(mapper, never()).insertLeaveRequest(any());
    }

    @Test
    void leaveSameKeyWithDifferentPayloadIsRejected()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        AttendanceLeaveRequests.SaveDraft body = leaveBody();
        LeaveRequest original = leaveReplay(body);
        when(mapper.selectLeaveRequestByClientRequestId(USER_ID, SHOP_ID,
                body.clientRequestId)).thenReturn(original);
        body.reason = "不同原因";

        assertThatThrownBy(() -> leaveService(mapper).saveDraft(body, SHOP_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("LEAVE_CLIENT_REQUEST_ID_REUSED");
        verify(mapper, never()).insertLeaveRequest(any());
    }

    @Test
    void leaveDuplicateKeyRaceRereadsAndReturnsTheWinningRequest()
    {
        AttendanceLeaveMapper mapper = mock(AttendanceLeaveMapper.class);
        AttendanceLeaveRequests.SaveDraft body = leaveBody();
        LeaveRequest winner = leaveReplay(body);
        when(mapper.selectActiveEmployeeNameInShop(USER_ID, SHOP_ID))
                .thenReturn("测试员工");
        LeaveType type = leaveType();
        when(mapper.selectLeaveTypeById(type.leaveTypeId)).thenReturn(type);
        doThrow(new DuplicateKeyException("race"))
                .when(mapper).insertLeaveRequest(any());
        when(mapper.selectLeaveRequestByClientRequestIdForUpdate(USER_ID,
                SHOP_ID, body.clientRequestId)).thenReturn(winner);

        assertThat(leaveService(mapper).saveDraft(body, SHOP_ID))
                .isSameAs(winner);
        verify(mapper, never()).deleteLeaveSegments(any());
    }

    @Test
    void correctionSameKeyAndSamePayloadReturnsTheOriginalWithoutValidation()
    {
        AttendanceCorrectionMapper mapper = mock(
                AttendanceCorrectionMapper.class);
        AttendanceCorrectionRequests.SaveDraft body = correctionBody();
        CorrectionRequest original = correctionReplay(body);
        when(mapper.selectCorrectionRequestByClientRequestId(USER_ID, SHOP_ID,
                body.clientRequestId)).thenReturn(original);

        CorrectionRequest result = correctionService(mapper).saveDraft(body,
                SHOP_ID);

        assertThat(result).isSameAs(original);
        verify(mapper, never()).selectScheduleRefForUpdate(any());
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void correctionSameKeyWithDifferentPayloadIsRejected()
    {
        AttendanceCorrectionMapper mapper = mock(
                AttendanceCorrectionMapper.class);
        AttendanceCorrectionRequests.SaveDraft body = correctionBody();
        CorrectionRequest original = correctionReplay(body);
        when(mapper.selectCorrectionRequestByClientRequestId(USER_ID, SHOP_ID,
                body.clientRequestId)).thenReturn(original);
        body.reason = "不同原因";

        assertThatThrownBy(() -> correctionService(mapper).saveDraft(body,
                SHOP_ID)).isInstanceOf(ServiceException.class)
                .hasMessageContaining("CORRECTION_CLIENT_REQUEST_ID_REUSED");
        verify(mapper, never()).insertCorrectionRequest(any());
    }

    @Test
    void correctionDuplicateKeyRaceRereadsAndReturnsTheWinningRequest()
    {
        AttendanceCorrectionMapper mapper = mock(
                AttendanceCorrectionMapper.class);
        AttendanceCorrectionRequests.SaveDraft body = correctionBody();
        CorrectionRequest winner = correctionReplay(body);
        ScheduleRef schedule = schedule();
        when(mapper.selectScheduleRefForUpdate(body.scheduleId))
                .thenReturn(schedule);
        when(mapper.selectActiveEmployeeNameInShop(USER_ID, SHOP_ID))
                .thenReturn("测试员工");
        when(mapper.selectScheduleSegmentRefs(body.scheduleId))
                .thenReturn(List.of(workSegment()));
        doThrow(new DuplicateKeyException("race"))
                .when(mapper).insertCorrectionRequest(any());
        when(mapper.selectCorrectionRequestByClientRequestIdForUpdate(USER_ID,
                SHOP_ID, body.clientRequestId)).thenReturn(winner);

        assertThat(correctionService(mapper).saveDraft(body, SHOP_ID))
                .isSameAs(winner);
    }

    private AttendanceLeaveService leaveService(AttendanceLeaveMapper mapper)
    {
        return new AttendanceLeaveService(mapper,
                mock(AttendanceLeaveAttachmentStorage.class),
                mock(AttendanceLeaveApprovalOutboxService.class),
                mock(AttendanceLeaveApprovalAfterCommitTrigger.class),
                mock(RemoteApprovalService.class), shopScope, featureGate);
    }

    private AttendanceCorrectionService correctionService(
            AttendanceCorrectionMapper mapper)
    {
        return new AttendanceCorrectionService(mapper,
                mock(AttendanceCorrectionApprovalOutboxService.class),
                mock(AttendanceCorrectionApprovalAfterCommitTrigger.class),
                shopScope, featureGate);
    }

    private AttendanceLeaveRequests.SaveDraft leaveBody()
    {
        AttendanceLeaveRequests.SaveDraft value =
                new AttendanceLeaveRequests.SaveDraft();
        value.clientRequestId = "leave:req:0001";
        value.leaveTypeId = 3L;
        value.startTime = LocalDateTime.of(2026, 8, 24, 9, 0);
        value.endTime = LocalDateTime.of(2026, 8, 24, 10, 0);
        value.reason = "  看医生  ";
        return value;
    }

    private LeaveRequest leaveReplay(AttendanceLeaveRequests.SaveDraft body)
    {
        LeaveRequest value = new LeaveRequest();
        value.leaveRequestId = 41L;
        value.clientRequestId = body.clientRequestId;
        value.clientRequestFingerprint = AttendanceClientRequestSupport
                .fingerprint("OA_ATTENDANCE_LEAVE_DRAFT_V1",
                        body.leaveTypeId, body.startTime, body.endTime,
                        body.reason.trim());
        value.userId = USER_ID;
        value.shopId = SHOP_ID;
        value.status = "DRAFT";
        value.rowVersion = 0L;
        return value;
    }

    private LeaveType leaveType()
    {
        LeaveType value = new LeaveType();
        value.leaveTypeId = 3L;
        value.status = "ENABLED";
        value.minMinutes = 30;
        value.stepMinutes = 30;
        value.allowCrossDay = true;
        value.payPolicy = "UNPAID";
        return value;
    }

    private AttendanceCorrectionRequests.SaveDraft correctionBody()
    {
        AttendanceCorrectionRequests.SaveDraft value =
                new AttendanceCorrectionRequests.SaveDraft();
        value.clientRequestId = "correction:req:0001";
        value.scheduleId = 31L;
        value.correctionType = "missing_punch";
        value.targetPunchType = "in";
        value.requestedPunchTime = LocalDateTime.of(2026, 8, 22, 8, 0);
        value.reason = "  忘记打卡  ";
        return value;
    }

    private CorrectionRequest correctionReplay(
            AttendanceCorrectionRequests.SaveDraft body)
    {
        CorrectionRequest value = new CorrectionRequest();
        value.correctionRequestId = 51L;
        value.clientRequestId = body.clientRequestId;
        value.clientRequestFingerprint = AttendanceClientRequestSupport
                .fingerprint("OA_ATTENDANCE_CORRECTION_DRAFT_V1",
                        body.scheduleId, "MISSING_PUNCH", "IN", null, null,
                        null, body.requestedPunchTime, body.reason.trim());
        value.userId = USER_ID;
        value.shopId = SHOP_ID;
        value.status = "DRAFT";
        value.rowVersion = 0L;
        return value;
    }

    private ScheduleRef schedule()
    {
        ScheduleRef value = new ScheduleRef();
        value.scheduleId = 31L;
        value.userId = USER_ID;
        value.shopId = SHOP_ID;
        value.businessDate = LocalDate.of(2026, 8, 22);
        value.status = "PUBLISHED";
        value.punchModeSnapshot = AttendanceRuleEngine.SHIFT_BOUNDARY;
        value.startTimeSnapshot = LocalTime.of(8, 0);
        value.endTimeSnapshot = LocalTime.of(17, 0);
        value.crossDaySnapshot = false;
        value.checkInOpenMinutesSnapshot = 60;
        value.checkInCloseMinutesSnapshot = 60;
        value.checkOutOpenMinutesSnapshot = 60;
        value.checkOutCloseMinutesSnapshot = 60;
        return value;
    }

    private ScheduleSegmentRef workSegment()
    {
        ScheduleSegmentRef value = new ScheduleSegmentRef();
        value.scheduleSegmentSnapshotId = 91L;
        value.scheduleId = 31L;
        value.segmentOrder = 1;
        value.segmentType = "WORK";
        value.startMinuteOffset = 480;
        value.endMinuteOffset = 1020;
        return value;
    }
}
