package com.erp.oa.attendance.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.erp.approval.api.domain.ApprovalStartRequest;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.ApprovalOutbox;
import com.erp.oa.attendance.correction.AttendanceCorrectionModels.CorrectionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AttendanceCorrectionApprovalOutboxServiceTest
{
    private AttendanceCorrectionMapper mapper;
    private AttendanceCorrectionApprovalOutboxService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(AttendanceCorrectionMapper.class);
        service = new AttendanceCorrectionApprovalOutboxService(mapper,
                new ObjectMapper());
    }

    @Test
    void enqueueFreezesRoundVersionAndIdempotencyKey()
    {
        CorrectionRequest request = request();
        ApprovalStartRequest command = command();
        when(mapper.insertApprovalOutbox(any())).thenAnswer(invocation -> {
            ApprovalOutbox row = invocation.getArgument(0);
            row.outboxId = 77L;
            return 1;
        });

        ApprovalOutbox row = service.enqueue(request, command, "tester");

        assertThat(row.outboxId).isEqualTo(77L);
        assertThat(row.correctionRequestId).isEqualTo(12L);
        assertThat(row.businessRound).isEqualTo(2);
        assertThat(row.requestRowVersion).isEqualTo(3L);
        assertThat(row.idempotencyKey)
                .isEqualTo("OA_ATTENDANCE_CORRECTION:12:2");
        assertThat(row.requestJson).contains("OA_ATTENDANCE_CORRECTION",
                "businessRound");
    }

    @Test
    void remoteSuccessLinksLocalRequestBeforeCompletingOutbox()
    {
        ApprovalOutbox candidate = new ApprovalOutbox();
        candidate.outboxId = 77L;
        candidate.status = "REMOTE_SUCCEEDED";
        candidate.rowVersion = 4L;

        ApprovalOutbox locked = new ApprovalOutbox();
        locked.outboxId = 77L;
        locked.correctionRequestId = 12L;
        locked.status = "REMOTE_SUCCEEDED";
        locked.businessRound = 2;
        locked.remoteBusinessRound = 2;
        locked.remoteInstanceId = 91L;
        locked.requestRowVersion = 3L;
        locked.rowVersion = 4L;
        CorrectionRequest request = request();
        request.status = "SUBMITTING";
        when(mapper.selectApprovalOutboxByIdForUpdate(77L))
                .thenReturn(locked);
        when(mapper.selectCorrectionRequestByIdForUpdate(12L))
                .thenReturn(request);
        when(mapper.finalizeCorrectionApprovalStart(12L, 2, 3L, 91L,
                "tester")).thenReturn(1);
        when(mapper.markApprovalCompleted(77L, "REMOTE_SUCCEEDED", 4L))
                .thenReturn(1);

        service.finalizeRemoteSuccess(candidate, "tester");

        verify(mapper).finalizeCorrectionApprovalStart(12L, 2, 3L, 91L,
                "tester");
        verify(mapper).markApprovalCompleted(77L, "REMOTE_SUCCEEDED", 4L);
        assertThat(candidate.status).isEqualTo("COMPLETED");
        assertThat(candidate.remoteInstanceId).isEqualTo(91L);
    }

    private CorrectionRequest request()
    {
        CorrectionRequest value = new CorrectionRequest();
        value.correctionRequestId = 12L;
        value.businessRound = 2;
        value.rowVersion = 3L;
        return value;
    }

    private ApprovalStartRequest command()
    {
        ApprovalStartRequest value = new ApprovalStartRequest();
        value.setBusinessCode("OA_ATTENDANCE_CORRECTION");
        value.setBusinessId("12");
        value.setBusinessRound(2);
        value.setIdempotencyKey("OA_ATTENDANCE_CORRECTION:12:2");
        return value;
    }
}
