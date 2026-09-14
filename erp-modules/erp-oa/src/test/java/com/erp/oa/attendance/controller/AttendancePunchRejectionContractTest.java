package com.erp.oa.attendance.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.oa.attendance.service.AttendanceV2Service;
import com.erp.oa.mapper.OaDeptScopeMapper;

class AttendancePunchRejectionContractTest
{
    @Test
    void knownValidationFailureReturnsARequestBoundTerminalRejection()
    {
        var service = mock(AttendanceV2Service.class);
        when(service.punch(any(), any(), eq(101L)))
                .thenThrow(new ServiceException("OUTSIDE_ATTENDANCE_GEOFENCE"));
        var controller = controller(service);
        var response = punch(controller);
        assertThat(response).containsEntry("code", 422)
                .containsEntry("businessCode", "ATTENDANCE_PUNCH_REJECTED")
                .containsEntry("clientRequestId", "attendance-rejection-proof")
                .containsEntry("msg", "OUTSIDE_ATTENDANCE_GEOFENCE");
        verify(service).punch(any(), any(), eq(101L));
    }

    @Test
    void consumedChallengeOrCommitFailureMustNotClaimTheRequestWasRejected()
    {
        for (String reason : new String[] { "PUNCH_CHALLENGE_CONSUMED",
                "PUNCH_CHALLENGE_EXPIRED", "PUNCH_EVENT_CREATE_FAILED",
                "PUNCH_CHALLENGE_CONSUME_CONFLICT", "database commit failed" })
        {
            var service = mock(AttendanceV2Service.class);
            var failure = new ServiceException(reason);
            when(service.punch(any(), any(), eq(101L))).thenThrow(failure);
            assertThatThrownBy(() -> punch(controller(service))).isSameAs(failure);
        }
    }

    @Test
    void captureTimestampPreservesOffsetsAndLegacyInputForTransactionalValidation()
    {
        var service = mock(AttendanceV2Service.class);
        var controller = controller(service);
        var http = new MockHttpServletRequest(); http.addHeader("Dept-NumId", "101");
        for (String value : new String[] { "2026-09-09T01:00:00Z", "2026-09-09T10:00:00+09:00", "2026-09-09T09:00:00" })
        {
            controller.punch("a".repeat(64), "IN", null, BigDecimal.ONE, BigDecimal.ONE,
                    BigDecimal.TEN, "WGS84", value, "capture-format-proof", null, null, null, http);
        }
        var captured = org.mockito.ArgumentCaptor.forClass(com.erp.oa.attendance.dto.AttendanceRequests.PunchCommand.class);
        verify(service, times(3)).punch(captured.capture(), any(), eq(101L));
        assertThat(captured.getAllValues()).extracting(value -> value.clientCaptureTimestamp)
                .containsExactly("2026-09-09T01:00:00Z", "2026-09-09T10:00:00+09:00", "2026-09-09T09:00:00");
        verifyNoMoreInteractions(service);
    }

    private AttendanceV2Controller controller(AttendanceV2Service service)
    {
        var controller = new AttendanceV2Controller(service, null);
        var scope = mock(OaDeptScopeMapper.class);
        when(scope.countActiveStoreDept(101L)).thenReturn(1);
        ReflectionTestUtils.setField(controller, "deptScopeMapper", scope);
        return controller;
    }

    private com.erp.common.core.web.domain.AjaxResult punch(AttendanceV2Controller controller)
    {
        var request = new MockHttpServletRequest();
        request.addHeader("Dept-NumId", "101");
        return controller.punch("a".repeat(64), "IN", null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, "WGS84",
                "2026-09-08T09:00:00",
                "attendance-rejection-proof", null, null, null, request);
    }
}
