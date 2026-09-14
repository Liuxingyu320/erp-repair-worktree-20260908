package com.erp.oa.attendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.ApprovalOutbox;
import com.erp.oa.attendance.leave.AttendanceLeaveModels.LeaveRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AttendanceLeaveApprovalOutboxServiceTest
{
    private AttendanceLeaveMapper mapper;
    private AttendanceLeaveApprovalOutboxService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AttendanceLeaveMapper.class);
        service = new AttendanceLeaveApprovalOutboxService(mapper,
                new ObjectMapper(), legacyQuota());
    }

    @Test
    void enqueueFreezesRequestVersionAndStableBusinessKey()
    {
        LeaveRequest request = request();
        ApprovalStartRequest command = command();
        when(mapper.insertApprovalOutbox(any())).thenAnswer(invocation -> {
            ApprovalOutbox row = invocation.getArgument(0);
            row.outboxId = 70L;
            return 1;
        });

        ApprovalOutbox row = service.enqueue(request, command, "tester");

        assertThat(row.leaveRequestId).isEqualTo(12L);
        assertThat(row.businessRound).isEqualTo(2);
        assertThat(row.requestRowVersion).isEqualTo(3L);
        assertThat(row.idempotencyKey)
                .isEqualTo("OA_ATTENDANCE_LEAVE:12:2");
        assertThat(row.requestJson).contains("OA_ATTENDANCE_LEAVE",
                "businessRound");
    }

    @Test
    void persistedRemoteResultIsLinkedBeforeOutboxCompletion()
    {
        ApprovalOutbox candidate = new ApprovalOutbox();
        candidate.outboxId = 70L;
        candidate.status = "REMOTE_SUCCEEDED";
        candidate.rowVersion = 4L;
        ApprovalOutbox locked = new ApprovalOutbox();
        locked.outboxId = 70L;
        locked.leaveRequestId = 12L;
        locked.status = "REMOTE_SUCCEEDED";
        locked.businessRound = 2;
        locked.remoteBusinessRound = 2;
        locked.remoteInstanceId = 91L;
        locked.requestRowVersion = 3L;
        locked.rowVersion = 4L;
        LeaveRequest request = request();
        request.status = "SUBMITTING";
        when(mapper.selectApprovalOutboxByIdForUpdate(70L))
                .thenReturn(locked);
        when(mapper.selectApprovalOutboxById(70L)).thenReturn(locked);
        when(mapper.selectLeaveRequestByIdForUpdate(12L)).thenReturn(request);
        when(mapper.finalizeLeaveApprovalStart(12L, 2, 3L, 91L,
                "tester")).thenReturn(1);
        when(mapper.markApprovalCompleted(70L, "REMOTE_SUCCEEDED", 4L))
                .thenReturn(1);

        service.finalizeRemoteSuccess(candidate, "tester");

        verify(mapper).finalizeLeaveApprovalStart(12L, 2, 3L, 91L,
                "tester");
        verify(mapper).markApprovalCompleted(70L, "REMOTE_SUCCEEDED", 4L);
        assertThat(candidate.status).isEqualTo("COMPLETED");
        assertThat(candidate.remoteInstanceId).isEqualTo(91L);
    }

    private LeaveRequest request()
    {
        LeaveRequest value = new LeaveRequest();
        value.leaveRequestId = 12L;
        value.businessRound = 2;
        value.rowVersion = 3L;
        return value;
    }

    private ApprovalStartRequest command()
    {
        ApprovalStartRequest value = new ApprovalStartRequest();
        value.setBusinessCode("OA_ATTENDANCE_LEAVE");
        value.setBusinessId("12");
        value.setBusinessRound(2);
        value.setIdempotencyKey("OA_ATTENDANCE_LEAVE:12:2");
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
