package com.erp.oa.attendance.leave.balance;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import com.erp.oa.service.BusinessFeatureGate;

class AttendanceLeaveBalanceAccrualJobTest
{
    @Test void defaultDisabledJobTouchesNoUnmigratedTable() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var service=mock(AttendanceLeaveBalanceService.class);var gate=mock(BusinessFeatureGate.class);
        new AttendanceLeaveBalanceAccrualJob(mapper,service,gate,false).accrue();verifyNoInteractions(mapper,service,gate);
        var constructor=AttendanceLeaveBalanceAccrualJob.class.getConstructors()[0];assertThat(constructor.getParameters()[3].getAnnotation(Value.class).value()).endsWith(":false}");
    }
    @Test void attendanceGateStillRequiredAfterAccrualOptIn() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var service=mock(AttendanceLeaveBalanceService.class);var gate=mock(BusinessFeatureGate.class);
        new AttendanceLeaveBalanceAccrualJob(mapper,service,gate,true).accrue();verifyNoInteractions(mapper,service);
    }
    @Test void paginatedBatchContinuesAfterOneEmployeeFailureWithoutForgedLogin() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var service=mock(AttendanceLeaveBalanceService.class);var gate=mock(BusinessFeatureGate.class);when(gate.isEnabled(anyString())).thenReturn(true);
        when(mapper.selectConfiguredTypes()).thenReturn(List.of(1L,2L));when(mapper.selectAccrualUsers(0L,100)).thenReturn(List.of(11L,12L));when(mapper.selectAccrualUsers(12L,100)).thenReturn(List.of());
        when(service.recalculateScheduled(11L,1L)).thenThrow(new IllegalStateException("synthetic failure"));var job=new AttendanceLeaveBalanceAccrualJob(mapper,service,gate,true);job.accrue();job.accrue();
        verify(service).recalculateScheduled(12L,2L);verify(mapper).selectAccrualUsers(12L,100);job.accrue();verify(mapper,times(2)).selectAccrualUsers(0L,100);
    }
    @Test void noConfiguredPolicyDoesNotLoopOverAllEmployees() {
        var mapper=mock(AttendanceLeaveBalanceMapper.class);var service=mock(AttendanceLeaveBalanceService.class);var gate=mock(BusinessFeatureGate.class);when(gate.isEnabled(anyString())).thenReturn(true);when(mapper.selectConfiguredTypes()).thenReturn(List.of());
        new AttendanceLeaveBalanceAccrualJob(mapper,service,gate,true).accrue();verify(mapper,never()).selectAccrualUsers(anyLong(),anyInt());verifyNoInteractions(service);
    }
}
