package com.erp.oa.attendance.leave.balance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import com.erp.common.security.annotation.RequiresPermissions;
import com.erp.oa.attendance.leave.balance.AttendanceLeaveBalanceRequests.Recalculate;

class AttendanceLeaveBalanceControllerTest
{
    @Test void everyHttpEntryHasExplicitPermissionAndNoScheduledRoute() {
        for(Method method:AttendanceLeaveBalanceController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(RequiresPermissions.class)).as(method.getName()).isNotNull();
            assertThat(method.getName()).doesNotContain("Scheduled");
        }
        assertThat(AttendanceLeaveBalanceController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/attendance-v2/leave/balance");
    }
    @Test void selfRecalculationDoesNotAcceptClientUserOrAmount() {
        var service=mock(AttendanceLeaveBalanceService.class);var controller=new AttendanceLeaveBalanceController(service);Recalculate body=new Recalculate();body.leaveTypeId=1L;
        controller.recalculateMy(body);verify(service).recalculateMy(1L);verifyNoMoreInteractions(service);
        assertThat(Recalculate.class.getFields()).extracting(f->f.getName()).containsExactly("leaveTypeId");
    }
    @Test void employeeIdentityIsTakenFromPathAndServiceStillChecksScope() {
        var service=mock(AttendanceLeaveBalanceService.class);var controller=new AttendanceLeaveBalanceController(service);controller.employee(11L,1L);verify(service).employee(11L,1L);
        controller.ledger(11L,1L,99L);verify(service).ledger(11L,1L,99L);
    }
}
